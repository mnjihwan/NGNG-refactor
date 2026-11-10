package com.ngng.api.user.user.dropout;

import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.global.security.handler.*;
import com.ngng.api.global.security.jwt.util.*;
import com.ngng.api.user.user.controller.DropOutController;
import com.ngng.api.user.user.controller.AdminUserController;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.entity.TransactionStatus;
import com.ngng.api.transaction.service.TransactionDetailsService;
import com.ngng.api.product.product.service.ProductService;
import com.ngng.api.user.user.entity.*;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual security/filter/JWT/controller/services; repository and unused login manager are doubles. */
class DropOutSecurityChainTest {
    private static final String EMAIL = "rf042-caller@example.invalid";
    private static final String SECRET = Encoders.BASE64URL.encode(
            "rf042-synthetic-key-only-64-bytes-abcdefghijklmnopqrstuvwxyz-123456".getBytes(StandardCharsets.UTF_8));
    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;
    private UserRepository users;
    private User caller, target;
    private PointHistory balance;
    private TransactionDetails transaction;
    @BeforeAll static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "rf042-test-only", Map.of("jwt.expiration.access", "3600000", "jwt.expiration.refresh", "7200000")));
        context.setServletContext(new MockServletContext()); context.register(IsolatedConfiguration.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stop() { context.close(); }
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext(); users = context.getBean(UserRepository.class); reset(users);
        reset(context.getBean(PointHistoryService.class), context.getBean(TransactionDetailsService.class),
                context.getBean(ProductService.class), context.getBean(JoinService.class));
        caller = spy(DropOutServiceTest.fixture(7001L, "caller", true));
        target = spy(DropOutServiceTest.fixture(7002L, "target", true));
        balance = PointHistory.builder().id(8001L).user(caller).cost(5000L).build();
        transaction = TransactionDetails.builder().id(9001L).consumer(caller).seller(target).price(1000L)
                .status(TransactionStatus.builder().id(1L).build()).build();
        when(users.findUserByEmail(EMAIL)).thenAnswer(inv -> {
            var principal = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            assertNull(principal.getUser().getUserId(), "TokenFilter supplies email, not a database identity");
            return Optional.of(caller);
        });
        when(users.findById(7001L)).thenReturn(Optional.of(caller));
        when(users.findById(7002L)).thenReturn(Optional.of(target));
    }
    @AfterEach void cleared() { assertNull(SecurityContextHolder.getContext().getAuthentication()); }
    private static String token(String role) { return token(EMAIL, role, "access", 3600000); }
    private static String token(String email, String role, String type, long offset) {
        return Jwts.builder().setSubject(email).claim("role", role).claim("type", type)
                .setExpiration(new Date(System.currentTimeMillis() + offset))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SECRET))).compact();
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder dropout(String id) {
        return delete("/dropout").contentType("application/json").content("{\"id\":" + id + "}");
    }
    private void relatedStateUnchanged() {
        assertEquals(5000L, balance.getCost()); assertSame(caller, balance.getUser());
        assertEquals(1000L, transaction.getPrice()); assertEquals(1L, transaction.getStatus().getId());
        assertSame(caller, transaction.getConsumer()); assertSame(target, transaction.getSeller());
        verifyNoInteractions(context.getBean(PointHistoryService.class), context.getBean(TransactionDetailsService.class),
                context.getBean(ProductService.class), context.getBean(JoinService.class));
    }
    private void noMutation(List<Object> callerBefore, List<Object> targetBefore) {
        assertEquals(callerBefore, DropOutServiceTest.state(caller)); assertEquals(targetBefore, DropOutServiceTest.state(target));
        verify(users, never()).findById(any()); verify(users, never()).save(any());
        verify(caller, never()).changeVisible(); verify(target, never()).changeVisible(); relatedStateUnchanged();
    }
    static Stream<Arguments> selfMatrix() {
        return Stream.of("USER", "UNCONFIRMED_USER", "ADMIN", "FUTURE_ROLE").flatMap(role ->
                Stream.of(true, false).map(visible -> Arguments.of(role, visible)));
    }
    @ParameterizedTest @MethodSource("selfMatrix")
    void existingRolesCanToggleSelfWithNoRefreshCookie(String role, boolean visible) throws Exception {
        caller.setVisible(visible); List<Object> expected = new ArrayList<>(DropOutServiceTest.state(caller)); expected.set(7, !visible);
        mvc.perform(dropout("7001").header("Authorization", "Bearer " + token(role)))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization")).andExpect(header().doesNotExist("Set-Cookie"));
        assertEquals(expected, DropOutServiceTest.state(caller)); assertTrue(target.getVisible());
        var order = inOrder(users, caller); order.verify(users).findUserByEmail(EMAIL);
        order.verify(users).findById(7001L); order.verify(caller).changeVisible();
        verifyNoMoreInteractions(users); verify(target, never()).changeVisible(); relatedStateUnchanged();
    }
    static Stream<Arguments> otherMatrix() {
        return Stream.of("USER", "UNCONFIRMED_USER", "ADMIN").flatMap(role -> Stream.of("7002", "9999", "null")
                .flatMap(id -> Stream.of(true, false).map(visible -> Arguments.of(role, id, visible))));
    }
    @ParameterizedTest @MethodSource("otherMatrix")
    void authenticatedOtherAndNullIdsAreForbiddenBeforeLookup(String role, String id, boolean visible) throws Exception {
        target.setVisible(visible); var beforeCaller = DropOutServiceTest.state(caller); var beforeTarget = DropOutServiceTest.state(target);
        mvc.perform(dropout(id).header("Authorization", "Bearer " + token(role)))
                .andExpect(status().isForbidden()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization")).andExpect(header().doesNotExist("Set-Cookie"));
        verify(users).findUserByEmail(EMAIL); noMutation(beforeCaller, beforeTarget);
    }
    @Test void sameTokenCanStillReactivateSelfAsSeparatePolicyRemains() throws Exception {
        String access = token("USER");
        for (boolean visible : List.of(false, true)) {
            mvc.perform(delete("/dropout").contentType("application/json").content("{\"id\":7001}")
                            .header("Authorization", "Bearer " + access)).andExpect(status().isNoContent());
            assertEquals(visible, caller.getVisible());
        }
    }
    static Stream<Arguments> fallbackMatrix() {
        return Stream.of("missing", "expired").flatMap(access -> Stream.of("USER", "UNCONFIRMED_USER", "ADMIN")
                .flatMap(role -> Stream.of(true, false).map(own -> Arguments.of(access, role, own))));
    }
    @ParameterizedTest @MethodSource("fallbackMatrix")
    void fallbackOnlyIssuesCallerAccessAndStillChecksOwnership(String access, String role, boolean own) throws Exception {
        var beforeCaller = DropOutServiceTest.state(caller); var beforeTarget = DropOutServiceTest.state(target);
        var request = dropout(own ? "7001" : "7002")
                .cookie(new Cookie("refreshToken", token(EMAIL, role, "refresh", 3600000)));
        if (access.equals("expired")) request.header("Authorization", "Bearer " + token(EMAIL, role, "access", -60000));
        var result = mvc.perform(request).andExpect(status().is(own ? 204 : 403)).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        String generated = result.getResponse().getHeader("Authorization"); assertNotNull(generated);
        var claims = context.getBean(JwtTokenVerifier.class).parseRawToken(generated.substring(7));
        assertEquals(EMAIL, claims.getSubject()); assertEquals(role, claims.get("role")); assertEquals("access", claims.get("type"));
        verify(users).findUserByEmail(EMAIL);
        if (own) { assertFalse(caller.getVisible()); assertTrue(target.getVisible()); verify(target, never()).changeVisible(); relatedStateUnchanged(); }
        else noMutation(beforeCaller, beforeTarget);
    }
    @ParameterizedTest @ValueSource(strings = {"different-subject", "invalid"})
    void validAccessIgnoresIrrelevantRefreshCookie(String kind) throws Exception {
        String refresh = kind.equals("invalid") ? "invalid" : token(target.getEmail(), "ADMIN", "refresh", 3600000);
        mvc.perform(dropout("7002").header("Authorization", "Bearer " + token("USER")).cookie(new Cookie("refreshToken", refresh)))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Authorization"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        verify(users).findUserByEmail(EMAIL); verify(users, never()).findUserByEmail(target.getEmail());
        verify(users, never()).findById(any()); assertTrue(caller.getVisible()); assertTrue(target.getVisible());
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "malformed", "forged", "refresh-as-access", "blank-role", "blank-subject", "missing-exp", "missing-type"})
    void invalidAuthenticationNeverReachesRepositories(String kind) throws Exception {
        var request = dropout("7001");
        String access = switch (kind) {
            case "missing" -> null;
            case "malformed" -> "synthetic-invalid";
            case "forged" -> Jwts.builder().setSubject(EMAIL).claim("role", "USER").claim("type", "access")
                    .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                    .signWith(Keys.hmacShaKeyFor("wrong-synthetic-signing-key-abcdefghijklmnopqrstuvwxyz-123456789012345678".getBytes(StandardCharsets.UTF_8))).compact();
            case "refresh-as-access" -> token(EMAIL, "USER", "refresh", 3600000);
            case "blank-role" -> token(EMAIL, " ", "access", 3600000);
            case "blank-subject" -> token(" ", "USER", "access", 3600000);
            case "missing-exp" -> Jwts.builder().setSubject(EMAIL).claim("role", "USER").claim("type", "access")
                    .signWith(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SECRET))).compact();
            case "missing-type" -> token(EMAIL, "USER", null, 3600000);
            default -> throw new IllegalArgumentException(kind);
        };
        if (access != null) request.header("Authorization", "Bearer " + access);
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization")).andExpect(header().doesNotExist("Set-Cookie"));
        verifyNoInteractions(users); assertTrue(caller.getVisible()); assertTrue(target.getVisible());
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "null-id"})
    void unresolvedDatabaseCallerIsForbidden(String kind) throws Exception {
        if (kind.equals("missing")) doReturn(Optional.empty()).when(users).findUserByEmail(EMAIL);
        else caller.setUserId(null);
        var beforeCaller = DropOutServiceTest.state(caller); var beforeTarget = DropOutServiceTest.state(target);
        mvc.perform(dropout("7001").header("Authorization", "Bearer " + token("USER"))).andExpect(status().isForbidden());
        noMutation(beforeCaller, beforeTarget);
    }
    @ParameterizedTest @ValueSource(strings = {"", "{broken", "[]"})
    void malformedOrMissingBodyKeepsMvc400(String body) throws Exception {
        mvc.perform(delete("/dropout").contentType("application/json").content(body)
                .header("Authorization", "Bearer " + token("USER"))).andExpect(status().isBadRequest());
        verifyNoInteractions(users); verify(caller, never()).changeVisible(); verify(target, never()).changeVisible();
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "UNCONFIRMED_USER", "ADMIN"})
    void separateAdminDeleteRetainsExistingRoleAndFalseAssignment(String role) throws Exception {
        when(users.save(target)).thenReturn(target);
        var request = delete("/admin/users/7002").header("Authorization", "Bearer " + token(role));
        if (role.equals("ADMIN")) {
            mvc.perform(request).andExpect(status().isOk()).andExpect(content().string("7002"));
            assertFalse(target.getVisible()); verify(users).findById(7002L); verify(users).save(target);
            verify(target, never()).changeVisible(); verify(users, never()).findUserByEmail(any());
            mvc.perform(delete("/admin/users/7002").header("Authorization", "Bearer " + token(role))).andExpect(status().isOk());
            assertFalse(target.getVisible());
        } else {
            mvc.perform(request).andExpect(status().isForbidden()); verifyNoInteractions(users); assertTrue(target.getVisible());
        }
        assertTrue(caller.getVisible()); relatedStateUnchanged();
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, DropOutController.class, DropOutService.class, AuthService.class,
            AdminUserController.class, UserService.class})
    static class IsolatedConfiguration {
        @Bean @Primary AuthenticationConfiguration authenticationConfiguration() throws Exception {
            var configuration = mock(AuthenticationConfiguration.class);
            when(configuration.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class)); return configuration;
        }
        @Bean JwtTokenVerifier verifier() { return new JwtTokenVerifier(SECRET); }
        @Bean JwtTokenProvider provider() { return new JwtTokenProvider(SECRET); }
        @Bean CustomLogoutHandler logoutHandler() { return new CustomLogoutHandler(); }
        @Bean CustomLogoutSuccessHandler logoutSuccessHandler() { return new CustomLogoutSuccessHandler(); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean PointHistoryService points() { return mock(PointHistoryService.class); }
        @Bean TransactionDetailsService transactions() { return mock(TransactionDetailsService.class); }
        @Bean ProductService products() { return mock(ProductService.class); }
        @Bean JoinService join() { return mock(JoinService.class); }
    }
}
