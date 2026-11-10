package com.ngng.api.user.user.confirmation;

import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.global.security.handler.*;
import com.ngng.api.global.security.jwt.util.*;
import com.ngng.api.user.user.controller.ConfirmController;
import com.ngng.api.user.user.entity.*;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.repository.*;
import com.ngng.api.user.user.service.*;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit MVC/security/JWT/controller/service/AuthService beans; repositories and unused login manager
 * are doubles. No Boot/component scan, datasource, Redis, mail, SMS, or network clients. */
class ConfirmAccountSecurityChainTest {
    static final String EMAIL = "rf020-caller@example.invalid";
    static final String SECRET = Encoders.BASE64URL.encode("rf020-only-synthetic-key-abcdefghijklmnopqrstuvwxyz-0123456789-abcdef".getBytes(StandardCharsets.UTF_8));
    static AnnotationConfigWebApplicationContext context;
    static MockMvc mvc;
    UserRepository users;
    UserRoleRepository roles;
    JwtTokenProvider provider;
    User caller, other;
    @BeforeAll static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "rf020-only", Map.of("jwt.expiration.access", "3600000", "jwt.expiration.refresh", "7200000")));
        context.setServletContext(new MockServletContext()); context.register(IsolatedConfiguration.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stop() { context.close(); }
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        users = context.getBean(UserRepository.class); roles = context.getBean(UserRoleRepository.class);
        provider = context.getBean(JwtTokenProvider.class); reset(users, roles); clearInvocations(provider);
        caller = user(7001L, EMAIL); other = user(8001L, "rf020-other@example.invalid");
        when(users.findUserByEmail(EMAIL)).thenAnswer(inv -> {
            assertNull(((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser().getUserId());
            return Optional.of(caller);
        });
        when(users.findById(7001L)).thenReturn(Optional.of(caller));
        when(users.findById(8001L)).thenReturn(Optional.of(other));
        when(roles.findByRoleType("USER")).thenReturn(Role.builder().roleType("USER").build());
    }
    @AfterEach void noContextLeak() { assertNull(SecurityContextHolder.getContext().getAuthentication()); }
    static User user(Long id, String email) { return User.builder().userId(id).email(email).role(Role.builder().roleType("UNCONFIRMED_USER").build()).build(); }
    static String token(String email, String role, String type, int seconds) {
        return Jwts.builder().setSubject(email).claim("role", role).claim("type", type)
                .setExpiration(new Date(System.currentTimeMillis() + seconds * 1000L))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SECRET))).compact();
    }
    MockHttpServletRequestBuilder request(Long id) {
        return put("/confirm/account").contentType("application/json")
                .content("{\"userId\":" + id + ",\"accountBank\":\"fixture-bank\",\"accountNumber\":\"fixture-account\"}");
    }
    MockHttpServletRequestBuilder access(Long id) {
        return request(id).header("Authorization", "Bearer " + token(EMAIL,"UNCONFIRMED_USER","access",3600))
                .cookie(new Cookie("refreshToken", "existing-unexamined-cookie"));
    }
    Claims claims(String raw) { return Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SECRET))).build().parseClaimsJws(raw).getBody(); }
    void assertToken(String raw, String email, String role, String type) {
        Claims value = claims(raw); assertEquals(email, value.getSubject()); assertEquals(role, value.get("role")); assertEquals(type, value.get("type"));
    }
    void success(MvcResult result, User target) throws Exception {
        assertEquals("", result.getResponse().getContentAsString());
        String access = result.getResponse().getHeader("Authorization"); assertNotNull(access); assertTrue(access.startsWith("Bearer "));
        assertToken(access.substring(7), target.getEmail(), "USER", "access");
        String cookie = result.getResponse().getHeader("Set-Cookie"); assertNotNull(cookie);
        assertToken(cookie.substring("refreshToken=".length(), cookie.indexOf(';')), target.getEmail(), "USER", "refresh");
        for (String attribute : List.of("Path=/", "Domain=localhost", "Secure", "HttpOnly")) assertTrue(cookie.contains(attribute));
        assertEquals("fixture-bank", target.getAccountBank()); assertEquals("fixture-account", target.getAccountNumber()); assertEquals("USER", target.getRole().getRoleType());
        verify(users).findById(target.getUserId()); verify(users).existsByAccountNumber("fixture-account"); verify(roles).findByRoleType("USER");
        verify(provider).createRefreshToken(any()); verify(users, never()).save(any());
    }
    void unchanged() {
        for (User user : List.of(caller, other)) {
            assertNull(user.getAccountBank()); assertNull(user.getAccountNumber()); assertEquals("UNCONFIRMED_USER", user.getRole().getRoleType());
        }
        verifyNoInteractions(roles); verify(users, never()).save(any()); verify(provider, never()).createRefreshToken(any());
    }
    @Test void ownAccountRetains204HeadersAndState() throws Exception {
        success(mvc.perform(access(7001L)).andExpect(status().isNoContent()).andReturn(), caller);
        verify(users).findUserByEmail(EMAIL); verify(provider).createAccessToken(any());
        assertNull(other.getAccountNumber()); assertEquals("UNCONFIRMED_USER", other.getRole().getRoleType());
    }
    @ParameterizedTest @ValueSource(strings = {"other", "registered-other", "duplicate-other", "missing-target", "null-id", "missing-caller", "null-caller-id"})
    void objectDenialPrecedesAllTargetAndTokenAccess(String kind) throws Exception {
        Long id = 8001L;
        if (kind.equals("registered-other")) other.setAccountNumber("existing-other");
        if (kind.equals("duplicate-other")) when(users.existsByAccountNumber("fixture-account")).thenReturn(true);
        if (kind.equals("missing-target")) id = 9001L;
        if (kind.equals("null-id")) id = null;
        if (kind.equals("missing-caller")) doReturn(Optional.empty()).when(users).findUserByEmail(EMAIL);
        if (kind.equals("null-caller-id")) caller.setUserId(null);
        mvc.perform(access(id)).andExpect(status().isForbidden()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization")).andExpect(header().doesNotExist("Set-Cookie"));
        verify(users).findUserByEmail(EMAIL); verifyNoMoreInteractions(users); verifyNoInteractions(roles, provider);
        assertNull(caller.getAccountBank()); assertNull(caller.getAccountNumber());
        assertEquals("UNCONFIRMED_USER", caller.getRole().getRoleType());
        assertNull(other.getAccountBank()); assertEquals(kind.equals("registered-other") ? "existing-other" : null, other.getAccountNumber());
        assertEquals("UNCONFIRMED_USER", other.getRole().getRoleType());
    }
    @ParameterizedTest @CsvSource({"missing,true", "expired,true", "missing,false", "expired,false"})
    void refreshFallbackPreservesCallerTokenOnDenialAndOwnSuccess(String accessKind, boolean own) throws Exception {
        var request = request(own ? 7001L : 8001L).cookie(new Cookie("refreshToken",token(EMAIL,"UNCONFIRMED_USER","refresh",3600)));
        if (accessKind.equals("expired")) request.header("Authorization","Bearer " + token(EMAIL,"UNCONFIRMED_USER","access",-3600));
        var result = mvc.perform(request).andExpect(status().is(own ? 204 : 403)).andExpect(content().string("")).andReturn();
        if (own) {
            success(result, caller); verify(provider,times(2)).createAccessToken(any());
        } else {
            assertToken(result.getResponse().getHeader("Authorization").substring(7), EMAIL,"UNCONFIRMED_USER","access");
            assertNull(result.getResponse().getHeader("Set-Cookie"));
            verify(users).findUserByEmail(EMAIL); verifyNoMoreInteractions(users); verify(provider).createAccessToken(any()); unchanged();
        }
    }
    @ParameterizedTest @ValueSource(strings = {"registered", "duplicate"})
    void refreshFallbackOwn400KeepsOnlyCallerAccess(String kind) throws Exception {
        if (kind.equals("registered")) caller.setAccountNumber("existing-account");
        else when(users.existsByAccountNumber("fixture-account")).thenReturn(true);
        var result = mvc.perform(request(7001L).cookie(new Cookie("refreshToken",token(EMAIL,"UNCONFIRMED_USER","refresh",3600))))
                .andExpect(status().isBadRequest()).andExpect(content().string("")).andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertToken(result.getResponse().getHeader("Authorization").substring(7),EMAIL,"UNCONFIRMED_USER","access");
        assertNull(caller.getAccountBank()); assertEquals(kind.equals("registered") ? "existing-account" : null,caller.getAccountNumber());
        assertEquals("UNCONFIRMED_USER",caller.getRole().getRoleType()); verify(provider).createAccessToken(any());
        verify(provider,never()).createRefreshToken(any()); verifyNoInteractions(roles); verify(users,never()).save(any());
    }
    @ParameterizedTest @CsvSource({"other,true", "malformed,true", "other,false", "malformed,false"})
    void validAccessIdentityWinsOverPresentRefreshCookie(String cookieKind, boolean own) throws Exception {
        String refresh = cookieKind.equals("other") ? token(other.getEmail(),"ADMIN","refresh",3600) : "malformed";
        var request = request(own ? 7001L : 8001L).header("Authorization","Bearer " + token(EMAIL,"UNCONFIRMED_USER","access",3600))
                .cookie(new Cookie("refreshToken",refresh));
        var result = mvc.perform(request).andExpect(status().is(own ? 204 : 403)).andReturn();
        if (own) success(result,caller);
        else {
            assertNull(result.getResponse().getHeader("Authorization")); assertNull(result.getResponse().getHeader("Set-Cookie"));
            verify(users).findUserByEmail(EMAIL); verifyNoMoreInteractions(users); verifyNoInteractions(provider); unchanged();
        }
    }
    @ParameterizedTest @CsvSource({"USER,7001", "USER,8001", "ADMIN,7001", "ADMIN,8001"})
    void existingRolesDeniedBeforeService(String role, long id) throws Exception {
        mvc.perform(request(id).header("Authorization","Bearer " + token(EMAIL,role,"access",3600)).cookie(new Cookie("refreshToken","unused")))
                .andExpect(status().isForbidden()).andExpect(content().string(""));
        verifyNoInteractions(users, provider); unchanged();
    }
    @Test void mandatoryCookieRemainsRequiredWithValidAccess() throws Exception {
        mvc.perform(request(7001L).header("Authorization","Bearer " + token(EMAIL,"UNCONFIRMED_USER","access",3600)))
                .andExpect(status().isBadRequest()); verifyNoInteractions(users, provider); unchanged();
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "malformed", "access-type", "refresh-type", "bad-refresh"})
    void invalidCredentialsNeverReachService(String kind) throws Exception {
        var request = request(7001L);
        switch (kind) {
            case "malformed" -> request.header("Authorization","Bearer invalid");
            case "access-type" -> request.header("Authorization","Bearer " + token(EMAIL,"UNCONFIRMED_USER","refresh",3600));
            case "refresh-type" -> request.cookie(new Cookie("refreshToken",token(EMAIL,"UNCONFIRMED_USER","access",3600)));
            case "bad-refresh" -> request.cookie(new Cookie("refreshToken","invalid"));
        }
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization")).andExpect(header().doesNotExist("Set-Cookie"));
        verifyNoInteractions(users, provider); unchanged();
    }
    @ParameterizedTest @ValueSource(strings = {"registered", "duplicate"})
    void ownExistingAccountFailuresRemain400(String kind) throws Exception {
        if (kind.equals("registered")) caller.setAccountNumber("existing-account");
        else when(users.existsByAccountNumber("fixture-account")).thenReturn(true);
        mvc.perform(access(7001L)).andExpect(status().isBadRequest()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization")).andExpect(header().doesNotExist("Set-Cookie"));
        assertNull(caller.getAccountBank()); assertEquals(kind.equals("registered") ? "existing-account" : null, caller.getAccountNumber());
        assertEquals("UNCONFIRMED_USER",caller.getRole().getRoleType()); verifyNoInteractions(roles, provider); verify(users,never()).save(any());
        if (kind.equals("registered")) verify(users,never()).existsByAccountNumber(any());
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, ConfirmController.class, ConfirmService.class, AuthService.class})
    static class IsolatedConfiguration {
        @Bean @Primary AuthenticationConfiguration authenticationConfiguration() throws Exception {
            var value = mock(AuthenticationConfiguration.class); when(value.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class)); return value;
        }
        @Bean JwtTokenVerifier verifier() { return new JwtTokenVerifier(SECRET); }
        @Bean JwtTokenProvider provider() { return spy(new JwtTokenProvider(SECRET)); }
        @Bean CustomLogoutHandler logoutHandler() { return new CustomLogoutHandler(); }
        @Bean CustomLogoutSuccessHandler logoutSuccessHandler() { return new CustomLogoutSuccessHandler(); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean UserRoleRepository roles() { return mock(UserRoleRepository.class); }
    }
}
