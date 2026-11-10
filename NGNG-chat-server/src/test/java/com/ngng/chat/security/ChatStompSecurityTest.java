package com.ngng.chat.security;

import org.junit.jupiter.api.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatStompSecurityTest {
    ChatSecurityFixture f;
    WebSocketHandler handler;
    final List<Wire> wires = new ArrayList<>();
    @BeforeEach void setUp() {
        f = new ChatSecurityFixture();
        var mapping = f.context.getBean("stompWebSocketHandlerMapping", SimpleUrlHandlerMapping.class);
        handler = ((WebSocketHttpRequestHandler) mapping.getUrlMap().get("/chat-server")).getWebSocketHandler();
    }
    @AfterEach void tearDown() throws Exception { for (Wire wire : wires) handler.afterConnectionClosed(wire.socket, CloseStatus.NORMAL); f.close(); }
    @Test void anonymousPublicSubscriptionWorksButAnonymousPrivateAndWritesFail() throws Exception {
        Wire publicWire = connect("public", null);
        subscribe(publicWire, "/public-chats/10");
        f.context.getBean(SimpMessagingTemplate.class).convertAndSend("/public-chats/10", Map.of("fixture","public"));
        assertTrue(publicWire.await("MESSAGE").contains("public"));
        Wire privateWire = connect("private", null);
        privateWire.send("SUBSCRIBE\nid:private\ndestination:/private-chats/10/2\n\n");
        privateWire.errorAndClosed();
        Wire write = connect("write", null);
        write.send("SEND\ndestination:/chats/10\ncontent-type:application/json\n\n{\"userId\":1,\"message\":\"forged\"}");
        write.errorAndClosed();
        verify(f.messages, never()).save(any()); verify(f.publicChats, never()).save(any()); verify(f.reads, never()).save(any());
    }
    @Test void principalSurvivesRealHandlerAndOnlyParticipantsReceivePrivateAndOwnAlarm() throws Exception {
        Wire seller = connect("seller", f.token(f.seller));
        Wire buyer = connect("buyer", f.token(f.buyer,"UNCONFIRMED_USER",f.clock.instant().plusSeconds(3600)));
        Wire outsider = connect("other", f.token(f.other,"ADMIN",f.clock.instant().plusSeconds(3600)));
        subscribe(seller,"/private-chats/10/2"); subscribe(buyer,"/private-chats/10/2"); subscribe(seller,"/alarms/1");
        // No native user/simpUser override on SEND can replace the CONNECT principal.
        buyer.send("SEND\ndestination:/chats/10/2/1\ncontent-type:application/json\nuser:3\n\n{\"userId\":2,\"privateChatRoomId\":20,\"message\":\"hello\",\"contentType\":\"text\"}");
        String received = buyer.await("MESSAGE");
        assertTrue(received.contains("\"body\"")); assertTrue(received.contains("hello"));
        assertTrue(seller.await("MESSAGE").contains("hello"));
        assertTrue(seller.await("MESSAGE").contains("hello"));
        assertNull(outsider.frames.poll(100,TimeUnit.MILLISECONDS));
        verify(f.messages,times(1)).save(any()); verify(f.reads,times(1)).save(any());
        outsider.send("SUBSCRIBE\nid:no\ndestination:/private-chats/10/2\n\n"); outsider.errorAndClosed();
    }
    @Test void rejectsWrongAuthorRoomPathRecipientBrokerSendAndOtherAlarmWithoutSideEffects() throws Exception {
        String[] attacks = {
                "SEND\ndestination:/chats/10/2/1\ncontent-type:application/json\n\n{\"userId\":1,\"privateChatRoomId\":20}",
                "SEND\ndestination:/chats/11/2/1\ncontent-type:application/json\n\n{\"userId\":2,\"privateChatRoomId\":20}",
                "SEND\ndestination:/chats/10/2/3\ncontent-type:application/json\n\n{\"userId\":2,\"privateChatRoomId\":20}",
                "SEND\ndestination:/chats/10/2/1\ncontent-type:application/json\n\n{\"userId\":2,\"privateChatRoomId\":999}",
                "SEND\ndestination:/private-chats/10/2\n\nforged",
                "SEND\ndestination:/alarms/1\n\nforged",
                "SUBSCRIBE\nid:bad\ndestination:/alarms/1\n\n",
                "SUBSCRIBE\nid:bad\ndestination:/private-chats/10/2/extra\n\n"
        };
        Wire witness = connect("witness", f.token(f.seller)); subscribe(witness,"/private-chats/10/2"); subscribe(witness,"/alarms/1");
        for (int index=0; index<attacks.length; index++) {
            Wire attacker = connect("attack"+index, f.token(f.buyer));
            attacker.send(attacks[index]); attacker.errorAndClosed();
        }
        verify(f.messages,never()).save(any()); verify(f.reads,never()).save(any()); verify(f.publicChats,never()).save(any());
        assertNull(witness.frames.poll(150,TimeUnit.MILLISECONDS));
    }
    @Test void suppliedInvalidOrDuplicateTokenFailsConnectRatherThanBecomingAnonymous() throws Exception {
        for (String headers : List.of("Authorization:Bearer invalid\n", "Authorization:\n",
                "Authorization:"+f.token(f.seller)+"\nAuthorization:"+f.token(f.buyer)+"\n")) {
            Wire wire = open("invalid"+wires.size());
            wire.send("CONNECT\naccept-version:1.2\nhost:fixture\n"+headers+"\n"); wire.errorAndClosed();
        }
    }
    @Test void malformedSignedExpAndNbfFailConnectWithGenericErrorAndClose() throws Exception {
        int index=0;
        for(String token:ChatSecurityFixture.invalidTemporalTokens(f.clock.instant())) {
            Wire wire=open("malformed-temporal-"+index++);
            wire.send("CONNECT\naccept-version:1.2\nhost:fixture\nAuthorization:"+token+"\n\n"); wire.errorAndClosed();
        }
        verify(f.messages,never()).save(any()); verify(f.reads,never()).save(any());
    }
    @Test void publicWritePreservesEnvelopeAndSpoofedAuthorNeverSaves() throws Exception {
        Wire reader = connect("reader",null); subscribe(reader,"/public-chats/10");
        Wire writer = connect("writer",f.token(f.seller));
        writer.send("SEND\ndestination:/chats/10\ncontent-type:application/json\n\n{\"userId\":1,\"message\":\"public fixture\",\"isImage\":false}");
        String received=reader.await("MESSAGE");
        assertTrue(received.contains("\"body\"")); assertTrue(received.contains("public fixture"));
        verify(f.publicChats,times(1)).save(any());
        Wire forged=connect("forged",f.token(f.buyer));
        forged.send("SEND\ndestination:/chats/10\ncontent-type:application/json\n\n{\"userId\":1,\"message\":\"bad\"}");
        forged.errorAndClosed(); verify(f.publicChats,times(1)).save(any());
        assertNull(reader.frames.poll(100,TimeUnit.MILLISECONDS));
    }
    @Test void validConnectDoesNotTrustHandshakePrincipalAndMalformedOrPreconnectFramesFail() throws Exception {
        Wire handshake = new Wire("handshake"); wires.add(handshake);
        when(handshake.socket.getPrincipal()).thenReturn(new ChatIdentity(1L,f.seller.getEmail(),"USER",f.clock.instant().plusSeconds(100)));
        handler.afterConnectionEstablished(handshake.socket);
        handshake.send("CONNECT\naccept-version:1.2\nhost:fixture\n\n"); handshake.await("CONNECTED");
        handshake.send("SUBSCRIBE\nid:private\ndestination:/private-chats/10/2\n\n"); handshake.errorAndClosed();
        Wire preconnect = open("preconnect"); preconnect.send("SUBSCRIBE\nid:private\ndestination:/private-chats/10/2\n\n"); preconnect.errorAndClosed();
        Wire malformed=connect("malformed",f.token(f.buyer));
        malformed.send("SEND\ndestination:/chats/10/2/1\ncontent-type:application/json\n\nbroken"); malformed.errorAndClosed();
        verify(f.messages,never()).save(any()); verify(f.reads,never()).save(any());
    }
    @Test void serviceFailureProducesGenericErrorClosesAndDoesNotPublishBusinessData() throws Exception {
        Wire witness=connect("failure-witness",f.token(f.seller)); subscribe(witness,"/private-chats/10/2"); subscribe(witness,"/alarms/1");
        doThrow(new IllegalStateException("synthetic internal detail")).when(f.messages).save(any());
        Wire writer=connect("failure-writer",f.token(f.buyer));
        writer.send("SEND\ndestination:/chats/10/2/1\ncontent-type:application/json\n\n{\"userId\":2,\"privateChatRoomId\":20,\"message\":\"fail\"}");
        String error=writer.await("ERROR"); assertFalse(error.contains("synthetic internal detail"));
        awaitCondition(()->!writer.open.get());
        assertNull(witness.frames.poll(100,TimeUnit.MILLISECONDS)); verify(f.reads,never()).save(any());
    }
    @Test void subscriptionDeliveryRechecksCurrentParticipantAndFrameTokenCannotSwitchIdentity() throws Exception {
        Wire seller=connect("old-participant",f.token(f.seller)); subscribe(seller,"/private-chats/10/2");
        Wire buyer=connect("remaining-participant",f.token(f.buyer)); subscribe(buyer,"/private-chats/10/2");
        f.room.setSeller(f.other);
        f.context.getBean(SimpMessagingTemplate.class).convertAndSend("/private-chats/10/2",Map.of("fixture","changed"));
        assertTrue(buyer.await("MESSAGE").contains("changed")); assertNull(seller.frames.poll(100,TimeUnit.MILLISECONDS));
        buyer.send("SEND\ndestination:/chats/10\nAuthorization:"+f.token(f.other)+"\ncontent-type:application/json\n\n{\"userId\":3,\"message\":\"switch\"}");
        buyer.errorAndClosed(); verify(f.publicChats,never()).save(any());
    }
    @Test void cleanupFramesWorkAndDuplicateConnectCannotReplacePrincipal() throws Exception {
        Wire wire=connect("cleanup",null); subscribe(wire,"/public-chats/10");
        wire.send("UNSUBSCRIBE\nid:/public-chats/10\n\n"); awaitCondition(()->!subscribed("cleanup","/public-chats/10"));
        handler.handleMessage(wire.socket,new TextMessage("\n"));
        wire.send("DISCONNECT\n\n"); awaitCondition(()->!f.context.getBean(ChatSessions.class).active("cleanup"));
        Wire repeated=connect("repeated",f.token(f.buyer));
        repeated.send("CONNECT\naccept-version:1.2\nhost:fixture\nAuthorization:"+f.token(f.seller)+"\n\n"); repeated.errorAndClosed();
    }
    @Test void separateExpirationsCloseOnlyExpiredSessionAndRemoveSubscriptionsBeforeLaterDelivery() throws Exception {
        Wire shortSession = connect("short",f.token(f.buyer,"USER",f.clock.instant().plusSeconds(10)));
        Wire longSession = connect("long",f.token(f.buyer,"USER",f.clock.instant().plusSeconds(100)));
        subscribe(shortSession,"/private-chats/10/2"); subscribe(longSession,"/private-chats/10/2");
        f.clock.advance(10);
        f.context.getBean(ChatSecurityFixture.Expirations.class).runDue(f.clock.instant());
        assertFalse(shortSession.open.get()); assertTrue(longSession.open.get());
        awaitCondition(() -> !subscribed("short","/private-chats/10/2"));
        f.context.getBean(SimpMessagingTemplate.class).convertAndSend("/private-chats/10/2",Map.of("fixture","later"));
        assertTrue(longSession.await("MESSAGE").contains("later"));
        assertNull(shortSession.frames.poll(100,TimeUnit.MILLISECONDS));
        shortSession.send("SUBSCRIBE\nid:late\ndestination:/private-chats/10/2\n\n");
        assertFalse(f.context.getBean(ChatSessions.class).active("short"));
    }
    @Test void expiryBetweenOutboundEnqueueAndFinalTransportWriteDropsQueuedBusinessMessage() throws Exception {
        Wire wire = connect("queue",f.token(f.buyer,"USER",f.clock.instant().plusSeconds(10)));
        subscribe(wire,"/private-chats/10/2");
        // Place a transport-boundary gate ahead of the guarded socket. Broker routing occurs while token is live.
        var outbound = f.context.getBean("clientOutboundChannel",org.springframework.messaging.support.ExecutorSubscribableChannel.class);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        outbound.addInterceptor(new org.springframework.messaging.support.ExecutorChannelInterceptor() {
            @Override public org.springframework.messaging.Message<?> beforeHandle(org.springframework.messaging.Message<?> message,
                    org.springframework.messaging.MessageChannel channel, org.springframework.messaging.MessageHandler target) {
                if (SimpMessageType.MESSAGE.equals(message.getHeaders().get("simpMessageType"))) {
                    entered.countDown(); try { assertTrue(release.await(3,TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                } return message;
            }
        });
        f.context.getBean(SimpMessagingTemplate.class).convertAndSend("/private-chats/10/2",Map.of("fixture","queued"));
        assertTrue(entered.await(3,TimeUnit.SECONDS)); f.clock.advance(10); release.countDown();
        awaitCondition(() -> !wire.open.get());
        assertNull(wire.frames.poll(100,TimeUnit.MILLISECONDS));
        awaitCondition(() -> !subscribed("queue","/private-chats/10/2"));
    }
    Wire connect(String id,String token) throws Exception {
        Wire wire = open(id);
        wire.send("CONNECT\naccept-version:1.2\nhost:fixture\n"+(token==null?"":"Authorization:"+token+"\n")+"\n");
        wire.await("CONNECTED"); return wire;
    }
    Wire open(String id) throws Exception { Wire wire = new Wire(id); wires.add(wire); handler.afterConnectionEstablished(wire.socket); return wire; }
    void subscribe(Wire wire,String destination) throws Exception {
        wire.send("SUBSCRIBE\nid:"+destination+"\ndestination:"+destination+"\n\n");
        awaitCondition(() -> subscribed(wire.id,destination));
    }
    boolean subscribed(String session,String destination) {
        var h = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE); h.setDestination(destination);
        var subscriptions = f.context.getBean("simpleBrokerMessageHandler",SimpleBrokerMessageHandler.class).getSubscriptionRegistry()
                .findSubscriptions(MessageBuilder.createMessage(new byte[0],h.getMessageHeaders()));
        return subscriptions.containsKey(session);
    }
    static void awaitCondition(java.util.function.BooleanSupplier condition) throws Exception {
        long until = System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime()<until) Thread.sleep(10);
        assertTrue(condition.getAsBoolean());
    }
    final class Wire {
        final String id; final WebSocketSession socket = mock(WebSocketSession.class);
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>(); final AtomicBoolean open = new AtomicBoolean(true);
        Wire(String id) throws Exception {
            this.id=id; when(socket.getId()).thenReturn(id); when(socket.getAttributes()).thenReturn(new ConcurrentHashMap<>());
            when(socket.getUri()).thenReturn(URI.create("ws://fixture.invalid/chat-server")); when(socket.isOpen()).thenAnswer(i->open.get());
            when(socket.getAcceptedProtocol()).thenReturn("v12.stomp"); when(socket.getTextMessageSizeLimit()).thenReturn(65536);
            doAnswer(i->{frames.add(((TextMessage)i.getArgument(0)).getPayload());return null;}).when(socket).sendMessage(any());
            doAnswer(i->{open.set(false);return null;}).when(socket).close(any());
        }
        void send(String frame) throws Exception { handler.handleMessage(socket,new TextMessage(frame+'\0')); }
        String await(String type) throws Exception { String frame=frames.poll(3,TimeUnit.SECONDS); assertNotNull(frame,"expected "+type+" for "+id); assertTrue(frame.startsWith(type),frame); return frame; }
        void errorAndClosed() throws Exception { assertFalse(await("ERROR").contains("Bearer")); awaitCondition(()->!open.get()); }
    }
}
