package com.ngng.api.global.security;

import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.global.security.handler.CustomLogoutHandler;
import com.ngng.api.global.security.handler.CustomLogoutSuccessHandler;
import com.ngng.api.global.security.jwt.util.JwtTokenProvider;
import com.ngng.api.global.security.jwt.util.JwtTokenVerifier;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual SecurityConfig/TokenFilter/JWT; repository and login manager doubles, no Boot/network. */
class TokenStateSecurityChainTest {
    static final String EMAIL = "rf005@example.test";
    static AnnotationConfigWebApplicationContext context;
    static MockMvc mvc;
    static final AtomicInteger calls = new AtomicInteger();
    UserRepository users;
    User current;
    JwtTokenProvider provider;
    JwtTokenVerifier verifier;
    @BeforeAll static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "rf040-only", Map.of("jwt.expiration.access", "3600000", "jwt.expiration.refresh", "7200000")));
        context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stop() { context.close(); }
    @BeforeEach void setup() {
        calls.set(0); SecurityContextHolder.clearContext(); users = context.getBean(UserRepository.class); reset(users);
        provider = context.getBean(JwtTokenProvider.class); verifier = context.getBean(JwtTokenVerifier.class);
        clearInvocations(provider, verifier);
        current = spy(User.builder().userId(40L).email(EMAIL).visible(true).role(Role.builder().roleType("USER").build()).build());
        when(users.findUserByEmail(EMAIL)).thenAnswer(inv -> Optional.ofNullable(current));
    }
    @AfterEach void cleared() { assertNull(SecurityContextHolder.getContext().getAuthentication()); }
    static Stream<Arguments> roleMatrix() {
        return Stream.of("ADMIN", "USER", "UNCONFIRMED_USER").flatMap(db ->
                Stream.of("ADMIN", "USER", "UNCONFIRMED_USER", "LEGACY_ROLE").flatMap(claim ->
                        Stream.of("/admin/state", "/chat/state", "/my_page/state", "/confirm/account").flatMap(path ->
                                Stream.of("access", "missing", "empty", "expired").map(kind -> Arguments.of(db, claim, path, kind)))));
    }
    MockHttpServletRequestBuilder credentials(String path, String kind, String claimRole) {
        var request = get(path);
        if (kind.equals("access")) request.header("Authorization", "Bearer " + TokenFilterTest.token("access", claimRole, 3600));
        else if (kind.equals("expired")) request.header("Authorization", "Bearer " + TokenFilterTest.token("access", claimRole, -3600));
        else if (kind.equals("empty")) request.header("Authorization", "");
        return request.cookie(new Cookie("refreshToken", TokenFilterTest.token("refresh", claimRole, 3600)));
    }
    @ParameterizedTest @MethodSource("roleMatrix")
    void currentDatabaseRoleControlsAccessAndRefresh(String db, String claim, String path, String kind) throws Exception {
        current.setRole(Role.builder().roleType(db).build()); clearInvocations(current);
        boolean allowed = path.startsWith("/admin") ? db.equals("ADMIN") : path.startsWith("/chat") ? !db.equals("UNCONFIRMED_USER")
                : path.startsWith("/confirm") ? db.equals("UNCONFIRMED_USER") : true;
        var result = mvc.perform(credentials(path, kind, claim)).andExpect(status().is(allowed ? 200 : 403))
                .andExpect(content().string(allowed ? "ROLE_" + db : "")).andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertEquals(allowed ? 1 : 0, calls.get());
        verify(users).findUserByEmail(EMAIL); verifyNoMoreInteractions(users); verify(current).getVisible();
        String issued = result.getResponse().getHeader("Authorization");
        if (kind.equals("access")) { assertNull(issued); verifyNoInteractions(provider); verify(verifier).parseRawToken(anyString()); }
        else {
            assertNotNull(issued); assertTrue(issued.startsWith("Bearer "));
            assertEquals(db, verifier.getRole(issued)); assertEquals(EMAIL, verifier.getEmail(issued)); assertEquals("access", verifier.getType(issued));
            verify(provider).createAccessToken(any()); verify(provider, never()).createRefreshToken(any());
        }
    }
    static Stream<Arguments> stateMatrix() {
        return Stream.of("missing", "null-id", "false", "null-visible", "null-role", "null-role-type", "empty-role", "blank-role", "unknown-role")
                .flatMap(state -> Stream.of("access", "missing", "empty", "expired").map(kind -> Arguments.of(state, kind)));
    }
    @ParameterizedTest @MethodSource("stateMatrix")
    void invalidCurrentAccountStopsBeforeIssuanceOrBusinessAndCannotFallback(String state, String kind) throws Exception {
        switch (state) {
            case "missing" -> current = null;
            case "null-id" -> current.setUserId(null);
            case "false" -> current.setVisible(false);
            case "null-visible" -> current.setVisible(null);
            case "null-role" -> current.setRole(null);
            case "null-role-type" -> current.setRole(Role.builder().build());
            case "empty-role" -> current.setRole(Role.builder().roleType("").build());
            case "blank-role" -> current.setRole(Role.builder().roleType(" ").build());
            case "unknown-role" -> current.setRole(Role.builder().roleType("LEGACY_ROLE").build());
        }
        if (current != null) clearInvocations(current);
        mvc.perform(credentials("/my_page/state", kind, "ADMIN")).andExpect(status().isUnauthorized()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization")).andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        assertEquals(0, calls.get()); verifyNoInteractions(provider);
        verify(users).findUserByEmail(EMAIL); verifyNoMoreInteractions(users);
        verify(verifier, times(kind.equals("expired") ? 2 : 1)).parseRawToken(anyString());
        if (current != null) verify(current, times(state.equals("null-id") ? 0 : 1)).getVisible();
    }
    static Stream<Arguments> storageFailures() {
        return Stream.of("access", "missing").flatMap(kind -> Stream.of(
                new DataAccessResourceFailureException("synthetic unavailable"),
                new IncorrectResultSizeDataAccessException(1, 2),
                new IllegalArgumentException("synthetic repository failure"),
                new JwtException("synthetic repository failure")).map(failure -> Arguments.of(kind, failure)));
    }
    @ParameterizedTest @MethodSource("storageFailures")
    void databaseAndDuplicateIdentityFailuresPropagateOutsideCredentialCatch(String kind, RuntimeException failure) {
        doThrow(failure).when(users).findUserByEmail(EMAIL);
        assertSame(failure, assertThrows(failure.getClass(), () -> mvc.perform(credentials("/my_page/state", kind, "USER"))));
        verify(users).findUserByEmail(EMAIL); verifyNoMoreInteractions(users); verifyNoInteractions(provider); assertEquals(0, calls.get());
    }
    @ParameterizedTest @MethodSource("com.ngng.api.global.security.TokenFilterTest#badAccessHeaders")
    void invalidAccessWithValidRefreshDoesNotReadDatabase(String header) throws Exception {
        mvc.perform(get("/my_page/state").header("Authorization", header).cookie(new Cookie("refreshToken", TokenFilterTest.token("refresh", "ADMIN", 3600))))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Authorization"));
        verifyNoInteractions(users, provider); assertEquals(0, calls.get());
    }
    @ParameterizedTest @MethodSource("com.ngng.api.global.security.TokenFilterTest#badRefreshTokens")
    void invalidOrExpiredRefreshDoesNotReadDatabase(String raw) throws Exception {
        mvc.perform(get("/my_page/state").cookie(new Cookie("refreshToken", raw)))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Authorization"));
        verifyNoInteractions(users, provider); assertEquals(0, calls.get());
    }
    @ParameterizedTest @CsvSource({"GET,/main", "GET,/products/state", "GET,/products-other", "POST,/join/child", "GET,/search/child", "GET,/find/child", "GET,/category/child", "GET,/logout/child"})
    void skippedPublicPathsDoNotReadCurrentState(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "broken"))
                .andExpect(status().isOk()).andExpect(content().string("anonymous"));
        verifyNoInteractions(users, provider); assertEquals(1, calls.get());
    }
    @Test void corsPreflightDoesNotReadCurrentState() throws Exception {
        mvc.perform(options("/admin/state").header("Origin", "https://example.test").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk()); verifyNoInteractions(users, provider); assertEquals(0, calls.get());
    }
    @Test void reactivationCanReviveOldTokenAndDoesNotProvePermanentRevocation() throws Exception {
        String oldAccess = TokenFilterTest.token("access", "ADMIN", 3600);
        current.setVisible(false);
        mvc.perform(get("/my_page/state").header("Authorization", "Bearer " + oldAccess)).andExpect(status().isUnauthorized());
        current.setVisible(true);
        mvc.perform(get("/my_page/state").header("Authorization", "Bearer " + oldAccess)).andExpect(status().isOk());
        verify(users, times(2)).findUserByEmail(EMAIL); assertEquals(1, calls.get());
    }
    @Test void reusedEmailCanBindOldTokenToNewDatabaseIdentityAsKnownUnresolvedLimit() throws Exception {
        String oldAccess = TokenFilterTest.token("access", "USER", 3600);
        current = User.builder().userId(999L).email(EMAIL).visible(true).role(Role.builder().roleType("ADMIN").build()).build();
        mvc.perform(get("/admin/state").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isOk()).andExpect(content().string("ROLE_ADMIN"));
        verify(users).findUserByEmail(EMAIL); assertEquals(1, calls.get());
    }
    @Test void accessAndRefreshIssuedBeforeLogoutRemainUsableAfterCookieClearing() throws Exception {
        var principal = new CustomUserDetails(current);
        String copiedAccess = provider.createAccessToken(principal);
        String copiedRefresh = provider.createRefreshToken(principal);

        mvc.perform(get("/my_page/state").header("Authorization", copiedAccess))
                .andExpect(status().isOk()).andExpect(content().string("ROLE_USER"));
        mvc.perform(get("/my_page/state").cookie(new Cookie("refreshToken", copiedRefresh)))
                .andExpect(status().isOk()).andExpect(content().string("ROLE_USER"))
                .andExpect(header().string("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")));
        verify(users, times(2)).findUserByEmail(EMAIL);
        clearInvocations(users, provider);

        mvc.perform(post("/logout").header("Authorization", copiedAccess).cookie(new Cookie("refreshToken", copiedRefresh)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("refreshToken=;"), org.hamcrest.Matchers.containsString("Max-Age=0"))));
        verifyNoInteractions(users, provider);

        mvc.perform(get("/my_page/state").header("Authorization", copiedAccess))
                .andExpect(status().isOk()).andExpect(content().string("ROLE_USER"))
                .andExpect(header().doesNotExist("Authorization"));
        mvc.perform(get("/my_page/state").cookie(new Cookie("refreshToken", copiedRefresh)))
                .andExpect(status().isOk()).andExpect(content().string("ROLE_USER"))
                .andExpect(header().string("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")));
        verify(users, times(2)).findUserByEmail(EMAIL);
        verify(provider).createAccessToken(any());
        verify(provider, never()).createRefreshToken(any());
        assertEquals(4, calls.get());
    }
    @Configuration @EnableWebMvc @EnableWebSecurity @Import(SecurityConfig.class)
    static class Config {
        @Bean @Primary AuthenticationConfiguration authenticationConfiguration() throws Exception {
            var c = mock(AuthenticationConfiguration.class); when(c.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class)); return c;
        }
        @Bean JwtTokenVerifier verifier() { return spy(new JwtTokenVerifier(TokenFilterTest.SECRET)); }
        @Bean JwtTokenProvider provider() { var p = spy(new JwtTokenProvider(TokenFilterTest.SECRET)); ReflectionTestUtils.setField(p, "accessTokenExpirationMs", 3_600_000); return p; }
        @Bean CustomLogoutHandler logoutHandler() { return new CustomLogoutHandler(); }
        @Bean CustomLogoutSuccessHandler logoutSuccessHandler() { return new CustomLogoutSuccessHandler(); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean Probe probe() { return new Probe(); }
    }
    @RestController static class Probe {
        @RequestMapping("/**") String state(Authentication auth) { calls.incrementAndGet(); return auth == null ? "anonymous" : auth.getAuthorities().iterator().next().getAuthority(); }
    }
}
