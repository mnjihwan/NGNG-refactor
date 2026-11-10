package com.ngng.chat.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.*;

/** Each transport has its own expiry. Transport writes and expiry share a lock. */
@Component
public class ChatSessions {
    private final Map<String, State> sessions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final TaskScheduler scheduler;
    private volatile MessageChannel inbound;

    public ChatSessions(Clock clock, @Qualifier("chatExpiryScheduler") TaskScheduler scheduler) {
        this.clock = clock;
        this.scheduler = scheduler;
    }
    @Configuration
    public static class Scheduling {
        @Bean public TaskScheduler chatExpiryScheduler() {
            var scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("chat-expiry-");
            scheduler.setRemoveOnCancelPolicy(true);
            return scheduler;
        }
    }

    public void connect(String sessionId, ChatIdentity identity, MessageChannel channel) {
        inbound = channel;
        State state = sessions.computeIfAbsent(sessionId, ignored -> new State());
        synchronized (state) {
            if (state.connected || state.closed) throw new IllegalStateException("Chat connection denied");
            state.identity = identity;
            state.connected = true;
            if (identity != null) state.expiry = scheduler.schedule(() -> expire(sessionId, state), identity.expiresAt());
        }
    }
    public ChatIdentity identity(String id) {
        State state = sessions.get(id);
        if (state == null) throw new IllegalStateException("Chat connection required");
        synchronized (state) {
            if (!state.connected || state.closed) throw new IllegalStateException("Chat connection required");
            if (!active(state)) { expire(id, state); throw new IllegalStateException("Chat connection expired"); }
            return state.identity;
        }
    }
    public boolean active(String id) {
        State state = sessions.get(id);
        if (state == null) return false;
        synchronized (state) {
            if (!active(state)) { expire(id, state); return false; }
            return true;
        }
    }
    private boolean active(State state) {
        return state.connected && !state.closed && (state.identity == null || clock.instant().isBefore(state.identity.expiresAt()));
    }
    public void disconnect(String id) {
        State state = sessions.remove(id);
        if (state != null) synchronized (state) {
            state.closed = true;
            if (state.expiry != null) state.expiry.cancel(false);
        }
    }
    private void expire(String id, State state) {
        synchronized (state) {
            if (state.closed) return;
            state.closed = true;
            if (state.expiry != null) state.expiry.cancel(false);
            // Mark closed before notifying broker or touching the transport, so queued data is denied.
            try {
                if (inbound != null && state.connected) {
                    var h = StompHeaderAccessor.create(StompCommand.DISCONNECT);
                    h.setSessionId(id);
                    h.setSessionAttributes(new ConcurrentHashMap<>());
                    inbound.send(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()));
                }
            } finally {
                if (state.socket != null) try { state.socket.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { }
            }
        }
    }
    public void reject(String id) {
        State state = sessions.get(id);
        if (state == null) return;
        synchronized (state) {
            if (state.socket != null && !state.closed) try {
                state.socket.sendMessage(new TextMessage("ERROR\nmessage:Chat request rejected\n\n\u0000"));
            } catch (IOException ignored) { }
            expire(id, state);
        }
    }
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override public void afterConnectionEstablished(WebSocketSession socket) throws Exception {
                State state = new State();
                WebSocketSession guarded = new WebSocketSessionDecorator(socket) {
                    @Override public void sendMessage(WebSocketMessage<?> message) throws IOException {
                        synchronized (state) {
                            if (state.closed) return;
                            // CONNECTED/ERROR before authentication has no identity. All later writes check exp here too.
                            if (state.identity != null && !clock.instant().isBefore(state.identity.expiresAt())) {
                                expire(getId(), state);
                                return;
                            }
                            super.sendMessage(message);
                        }
                    }
                };
                state.socket = guarded;
                sessions.put(socket.getId(), state);
                try { super.afterConnectionEstablished(guarded); }
                catch (Exception exception) { disconnect(socket.getId()); throw exception; }
            }
            @Override public void handleMessage(WebSocketSession socket, WebSocketMessage<?> message) throws Exception {
                State state = sessions.get(socket.getId());
                super.handleMessage(state == null ? socket : state.socket, message);
            }
            @Override public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) throws Exception {
                try { super.afterConnectionClosed(socket, status); } finally { disconnect(socket.getId()); }
            }
        };
    }
    private static final class State {
        WebSocketSession socket;
        ChatIdentity identity;
        boolean connected;
        boolean closed;
        ScheduledFuture<?> expiry;
    }
}
