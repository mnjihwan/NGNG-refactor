package com.ngng.api.transaction.read;

import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.global.security.handler.CustomLogoutHandler;
import com.ngng.api.global.security.handler.CustomLogoutSuccessHandler;
import com.ngng.api.global.security.jwt.util.JwtTokenProvider;
import com.ngng.api.global.security.jwt.util.JwtTokenVerifier;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.controller.TransactionController;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.transaction.repository.TransactionStatusRepository;
import com.ngng.api.transaction.service.TransactionDetailsService;
import com.ngng.api.transaction.service.TransactionRequestService;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Real SecurityConfig, JWT filter/provider/verifier, AuthService, controller and service.
 * Explicit beans only; repositories are mocks. No Boot scan, DB, Redis, ES or external clients. */
abstract class ReadChainHarness {
    static final String EMAIL = "rf023@example.test";
    static final String SECRET = Encoders.BASE64URL.encode(
            "rf023-synthetic-key-only-64-bytes-abcdefghijklmnopqrstuvwxyz-123456".getBytes(StandardCharsets.UTF_8));
    static AnnotationConfigWebApplicationContext context;
    static MockMvc mvc;
    TransactionDetailsRepository details;
    TransactionStatusRepository statuses;
    ProductRepository products;
    UserRepository users;
    TransactionRequestService requests;
    TransactionDetails transaction;
    @BeforeAll static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "rf023-test-only", Map.of("jwt.expiration.access", "3600000", "jwt.expiration.refresh", "7200000")));
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stop() { context.close(); }
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        details = context.getBean(TransactionDetailsRepository.class);
        statuses = context.getBean(TransactionStatusRepository.class);
        products = context.getBean(ProductRepository.class);
        users = context.getBean(UserRepository.class);
        requests = context.getBean(TransactionRequestService.class);
        reset(details, statuses, products, users, requests);
        transaction = spy(ReadFixture.graph());
        when(details.findById(88L)).thenReturn(Optional.of(transaction));
    }
    @AfterEach void contextClearedAndReadOnly() {
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(details, never()).save(any());
        verify(details, never()).saveAll(any());
        verify(details, never()).delete(any());
        verify(transaction, never()).setStatus(any());
        verifyNoInteractions(statuses, products, requests);
    }
    void caller(Long userId) {
        User caller = ReadFixture.user(userId);
        when(users.findUserByEmail(EMAIL)).thenAnswer(invocation -> {
            var principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            assertNull(principal.getUser().getUserId(), "ID must be resolved from the token email by AuthService");
            assertEquals(EMAIL, principal.getUser().getEmail());
            return Optional.of(caller);
        });
    }
    static String token(String type, String role, long seconds) {
        return Jwts.builder().setSubject(EMAIL).claim("role", role).claim("type", type)
                .setExpiration(new Date(System.currentTimeMillis() + seconds * 1000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SECRET))).compact();
    }
    @Configuration @EnableWebMvc @EnableWebSecurity @Import(SecurityConfig.class)
    static class Config {
        @Bean @Primary AuthenticationConfiguration authenticationConfiguration() throws Exception {
            var configuration = mock(AuthenticationConfiguration.class);
            when(configuration.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class));
            return configuration;
        }
        @Bean JwtTokenVerifier verifier() { return new JwtTokenVerifier(SECRET); }
        @Bean JwtTokenProvider provider() {
            var provider = new JwtTokenProvider(SECRET);
            ReflectionTestUtils.setField(provider, "accessTokenExpirationMs", 3_600_000);
            return provider;
        }
        @Bean CustomLogoutHandler logoutHandler() { return new CustomLogoutHandler(); }
        @Bean CustomLogoutSuccessHandler logoutSuccessHandler() { return new CustomLogoutSuccessHandler(); }
        @Bean TransactionDetailsRepository details() { return mock(TransactionDetailsRepository.class); }
        @Bean TransactionStatusRepository statuses() { return mock(TransactionStatusRepository.class); }
        @Bean ProductRepository products() { return mock(ProductRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean AuthService authService(UserRepository users) { return new AuthService(users); }
        @Bean TransactionDetailsService service(TransactionDetailsRepository details, TransactionStatusRepository statuses,
                                               ProductRepository products, AuthService auth) {
            return new TransactionDetailsService(details, statuses, products, auth);
        }
        @Bean TransactionRequestService requests() { return mock(TransactionRequestService.class); }
        @Bean TransactionController controller(TransactionDetailsService service, TransactionRequestService requests) {
            return new TransactionController(service, requests);
        }
    }
}
