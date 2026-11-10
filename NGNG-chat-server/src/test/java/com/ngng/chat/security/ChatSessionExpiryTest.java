package com.ngng.chat.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.Message;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatSessionExpiryTest {
    @Test void productionSchedulerClosesIdleAuthenticatedSocketAndNotifiesBroker() throws Exception {
        try(var context=new AnnotationConfigApplicationContext(ChatSessions.Scheduling.class)) {
            ChatSessions sessions=new ChatSessions(Clock.systemUTC(),context.getBean(TaskScheduler.class));
            WebSocketSession socket=mock(WebSocketSession.class); when(socket.getId()).thenReturn("idle");
            CountDownLatch closed=new CountDownLatch(1);
            doAnswer(invocation->{closed.countDown();return null;}).when(socket).close(CloseStatus.POLICY_VIOLATION);
            MessageChannel inbound=mock(MessageChannel.class);
            sessions.decorate(new AbstractWebSocketHandler() {}).afterConnectionEstablished(socket);
            sessions.connect("idle",new ChatIdentity(1L,"idle@fixture.invalid","USER",Instant.now().plusMillis(150)),inbound);
            assertTrue(closed.await(3,TimeUnit.SECONDS));
            verify(inbound).send(argThat(message->"idle".equals(message.getHeaders().get("simpSessionId")) &&
                    org.springframework.messaging.simp.SimpMessageType.DISCONNECT.equals(message.getHeaders().get("simpMessageType"))));
            assertFalse(sessions.active("idle"));
        }
    }
    @Test void brokerFailureDuringExpiryStillClosesSocketAndGuardDropsFurtherWrites() throws Exception {
        var clock=new ChatSecurityFixture.FixtureClock();
        TaskScheduler scheduler=mock(TaskScheduler.class);
        ChatSessions sessions=new ChatSessions(clock,scheduler);
        WebSocketSession socket=mock(WebSocketSession.class); when(socket.getId()).thenReturn("failure");
        WebSocketSession[] guarded=new WebSocketSession[1];
        var handler=sessions.decorate(new AbstractWebSocketHandler() {
            @Override public void afterConnectionEstablished(WebSocketSession session) {guarded[0]=session;}
        });
        handler.afterConnectionEstablished(socket);
        MessageChannel inbound=mock(MessageChannel.class);
        when(inbound.send(any())).thenThrow(new IllegalStateException("synthetic broker failure"));
        sessions.connect("failure",new ChatIdentity(1L,"failure@fixture.invalid","USER",clock.instant().plusSeconds(10)),inbound);
        clock.advance(10);
        assertThrows(IllegalStateException.class,()->sessions.active("failure"));
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
        guarded[0].sendMessage(new TextMessage("must not leave server")); verify(socket,never()).sendMessage(any());
    }
}
