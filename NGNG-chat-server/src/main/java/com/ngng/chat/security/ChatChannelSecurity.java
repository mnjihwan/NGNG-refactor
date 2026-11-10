package com.ngng.chat.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.regex.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngng.chat.publicChat.dto.request.CreatePublicChatRequestDTO;
import com.ngng.chat.privateChat.dto.request.CreatePrivateChatMessageRequestDTO;

@Component
@RequiredArgsConstructor
public class ChatChannelSecurity implements ChannelInterceptor {
    private static final Pattern PUBLIC = Pattern.compile("/public-chats/([1-9][0-9]*)");
    private static final Pattern PRIVATE = Pattern.compile("/private-chats/([1-9][0-9]*)/([1-9][0-9]*)");
    private static final Pattern ALARM = Pattern.compile("/alarms/([1-9][0-9]*)");
    private static final Pattern SEND = Pattern.compile("/chats/([1-9][0-9]*)(?:/([1-9][0-9]*)/([1-9][0-9]*))?");
    private final ChatTokenVerifier tokens;
    private final ChatSessions sessions;
    private final ChatAccess access;
    private final ObjectMapper json;

    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor h = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (h == null || h.getSessionId() == null) throw denied();
        String session = h.getSessionId();
        if (h.getCommand() == StompCommand.CONNECT || h.getCommand() == StompCommand.STOMP) {
            // Header names are case-insensitive for credentials; mixed-case duplicates are rejected too.
            var credentials = h.toNativeHeaderMap().entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase("Authorization"))
                    .flatMap(entry -> entry.getValue().stream()).toList();
            ChatIdentity identity = credentials.isEmpty() ? null : tokens.verify(credentials);
            sessions.connect(session, identity, channel);
            h.setUser(identity);
            return message;
        }
        if (h.getCommand() == StompCommand.DISCONNECT) { sessions.disconnect(session); return message; }
        ChatIdentity identity = sessions.identity(session);
        h.setUser(identity);
        if (h.getMessageType() == SimpMessageType.HEARTBEAT || h.getCommand() == StompCommand.UNSUBSCRIBE) return message;
        String destination = h.getDestination();
        if (destination == null) throw denied();
        if (h.getCommand() == StompCommand.SEND) {
            Matcher send = SEND.matcher(destination);
            if (!send.matches()) throw denied();
            access.identity(identity);
            try {
                byte[] payload = (byte[]) message.getPayload();
                if (send.group(2) == null) {
                    var request = json.readValue(payload, CreatePublicChatRequestDTO.class);
                    access.publicSend(identity, number(send, 1), request.getUserId());
                } else {
                    var request = json.readValue(payload, CreatePrivateChatMessageRequestDTO.class);
                    access.privateSend(identity, number(send, 1), number(send, 2), number(send, 3), request);
                }
            } catch (java.io.IOException | ClassCastException exception) { throw denied(); }
        } else if (h.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscription(identity, destination);
        } else throw denied();
        return message;
    }
    private void authorizeSubscription(ChatIdentity identity, String destination) {
        if (PUBLIC.matcher(destination).matches()) return;
        Matcher room = PRIVATE.matcher(destination), alarm = ALARM.matcher(destination);
        if (room.matches()) access.destination(identity, number(room, 1), number(room, 2));
        else if (alarm.matches()) access.self(identity, number(alarm, 1));
        else throw denied();
    }
    public ChannelInterceptor outbound() {
        return new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var h = MessageHeaderAccessor.getAccessor(message, org.springframework.messaging.simp.SimpMessageHeaderAccessor.class);
                if (h == null || h.getSessionId() == null) return null;
                // Protocol cleanup/ERROR is distinct from business data delivery.
                if (h.getMessageType() != SimpMessageType.MESSAGE) return message;
                if (!sessions.active(h.getSessionId())) return null;
                try { authorizeSubscription(sessions.identity(h.getSessionId()), h.getDestination()); return message; }
                catch (RuntimeException denied) { return null; }
            }
        };
    }
    private Long number(Matcher matcher, int group) { try { return Long.valueOf(matcher.group(group)); } catch (NumberFormatException e) { throw denied(); } }
    private IllegalStateException denied() { return new IllegalStateException("Chat frame denied"); }
}
