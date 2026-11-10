package com.ngng.chat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ngng.chat.WebSocketConfig;
import com.ngng.chat.privateChat.respository.*;
import com.ngng.chat.privateChat.service.*;
import com.ngng.chat.product.entity.Product;
import com.ngng.chat.product.repository.ProductRepository;
import com.ngng.chat.publicChat.controller.PublicChatController;
import com.ngng.chat.publicChat.repository.PublicChatRepository;
import com.ngng.chat.publicChat.service.PublicChatService;
import com.ngng.chat.thumbnail.entity.Thumbnail;
import com.ngng.chat.transaction.entity.repository.TransactionDetailsRepository;
import com.ngng.chat.user.entity.User;
import com.ngng.chat.user.repository.UserRepository;
import com.ngng.chat.user.service.UserService;
import com.ngng.chat.privateChat.entity.*;
import com.ngng.chat.publicChat.entity.PublicChat;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import java.time.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

final class ChatSecurityFixture implements AutoCloseable {
    static final byte[] KEY = "RF022-only-synthetic-HMAC-key-material-0123456789-abcdefghijklmnop".getBytes();
    final AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    final FixtureClock clock;
    final UserRepository users;
    final ProductRepository products;
    final PrivateChatRoomRepository rooms;
    final PrivateChatMessageRepository messages;
    final PrivateChatMessageReadRepository reads;
    final PublicChatRepository publicChats;
    final User seller = User.builder().id(1L).email("seller@fixture.invalid").name("seller").build();
    final User buyer = User.builder().id(2L).email("buyer@fixture.invalid").name("buyer").build();
    final User other = User.builder().id(3L).email("other@fixture.invalid").name("other").build();
    final Product product = Product.builder().id(10L).user(seller).title("fixture").requestList(List.of())
            .thumbnail(Thumbnail.builder().thumbnailUrl("fixture").build()).build();
    final PrivateChatRoom room = PrivateChatRoom.builder().id(20L).product(product).seller(seller).buyer(buyer).build();
    ChatSecurityFixture() {
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("fixture", Map.of(
                "jwt.secret.key", Encoders.BASE64URL.encode(KEY), "chat.allowed-origin-patterns", "http://fixture.invalid")));
        context.register(Config.class);
        context.refresh();
        clock = context.getBean(FixtureClock.class);
        users = context.getBean(UserRepository.class);
        products = context.getBean(ProductRepository.class);
        rooms = context.getBean(PrivateChatRoomRepository.class);
        messages = context.getBean(PrivateChatMessageRepository.class);
        reads = context.getBean(PrivateChatMessageReadRepository.class);
        publicChats = context.getBean(PublicChatRepository.class);
        when(users.findAllByEmail(seller.getEmail())).thenReturn(List.of(seller));
        when(users.findAllByEmail(buyer.getEmail())).thenReturn(List.of(buyer));
        when(users.findAllByEmail(other.getEmail())).thenReturn(List.of(other));
        when(users.findById(1L)).thenReturn(Optional.of(seller));
        when(users.findById(2L)).thenReturn(Optional.of(buyer));
        when(products.findById(10L)).thenReturn(Optional.of(product));
        when(rooms.findById(20L)).thenReturn(Optional.of(room));
        when(rooms.findByProductIdAndBuyerId(10L,2L)).thenReturn(Optional.of(room));
        when(messages.findByPrivateChatRoomIdAndVisible(20L,true)).thenReturn(List.of());
        when(messages.save(any())).thenAnswer(invocation -> {
            PrivateChatMessage message = invocation.getArgument(0);
            message.setPrivateChatId(100L); return message;
        });
        when(reads.findByPrivateChatRoomIdAndUserId(anyLong(),anyLong())).thenAnswer(invocation -> PrivateChatMessageRead.builder()
                .privateChatRoom(room).user(invocation.<Long>getArgument(1).equals(1L) ? seller : buyer).build());
        when(rooms.save(any())).thenAnswer(invocation -> { PrivateChatRoom saved = invocation.getArgument(0); saved.setId(30L); return saved; });
        when(publicChats.save(any())).thenAnswer(invocation -> { PublicChat saved = invocation.getArgument(0); saved.setId(200L); return saved; });
        when(publicChats.findById(200L)).thenReturn(Optional.of(PublicChat.builder().id(200L).productId(10L).user(seller).message("fixture").visible(true).build()));
        clearInvocations(users, products, rooms, messages, reads, publicChats);
    }
    String token(User user) { return token(user, "USER", clock.instant().plusSeconds(3600)); }
    String token(User user, String role, Instant expiration) {
        return "Bearer " + Jwts.builder().setSubject(user.getEmail()).claim("role",role).claim("type","access")
                .setExpiration(Date.from(expiration)).signWith(Keys.hmacShaKeyFor(KEY)).compact();
    }
    static String signedClaims(Map<String,Object> claims) {
        try { return "Bearer "+Jwts.builder().setPayload(new ObjectMapper().writeValueAsString(claims))
                .signWith(Keys.hmacShaKeyFor(KEY)).compact(); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }
    static List<String> invalidTemporalTokens(Instant now) {
        List<String> tokens=new ArrayList<>();
        for(String claim:List.of("exp","nbf")) for(Object value:List.of("not-a-date","4102444800","2026-11-10T00:00:00Z",Map.of("bad",1),List.of(1))) {
            var claims=new LinkedHashMap<String,Object>(Map.of("sub","seller@fixture.invalid","role","USER","type","access","exp",now.plusSeconds(100).getEpochSecond()));
            claims.put(claim,value); tokens.add(signedClaims(claims));
        }
        return tokens;
    }
    @Override public void close() { context.close(); }
    static class FixtureClock extends Clock {
        private Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public synchronized Instant instant() { return now; }
        synchronized void advance(long seconds) { now = now.plusSeconds(seconds); }
    }
    static class Expirations {
        final List<Map.Entry<Instant,Runnable>> tasks = new CopyOnWriteArrayList<>();
        void runDue(Instant now) { for (var task : tasks) if (!now.isBefore(task.getKey()) && tasks.remove(task)) task.getValue().run(); }
    }
    @Configuration
    @EnableWebMvc
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @Import({WebSocketConfig.class, ChatChannelSecurity.class, ChatSessions.class, ChatAccess.class,
            ChatTokenVerifier.class, ChatHttpSecurity.class, PublicChatController.class, PublicChatService.class,
            PrivateChatRoomService.class, PrivateChatMessageService.class, PrivateChatMessageReadService.class, UserService.class})
    static class Config {
        @Bean @Primary FixtureClock fixtureClock() { return new FixtureClock(); }
        @Bean Expirations expirations() { return new Expirations(); }
        @Bean(name="chatExpiryScheduler") TaskScheduler scheduler(Expirations expirations) {
            var scheduler = mock(TaskScheduler.class);
            when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
                expirations.tasks.add(Map.entry(invocation.getArgument(1), invocation.getArgument(0)));
                return mock(ScheduledFuture.class);
            }); return scheduler;
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean ProductRepository products() { return mock(ProductRepository.class); }
        @Bean PrivateChatRoomRepository rooms() { return mock(PrivateChatRoomRepository.class); }
        @Bean PrivateChatMessageRepository messages() { return mock(PrivateChatMessageRepository.class); }
        @Bean PrivateChatMessageReadRepository reads() { return mock(PrivateChatMessageReadRepository.class); }
        @Bean PublicChatRepository publicChats() { return mock(PublicChatRepository.class); }
        @Bean TransactionDetailsRepository transactions() { return mock(TransactionDetailsRepository.class); }
    }
}
