package com.ngng.api.user.user.address;

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
class ConfirmAddressSecurityChainTest {
    static final String EMAIL = "rf041-caller@example.invalid";
    static final String SECRET = Encoders.BASE64URL.encode("rf041-only-synthetic-key-abcdefghijklmnopqrstuvwxyz-0123456789-abcdef".getBytes(StandardCharsets.UTF_8));
    static AnnotationConfigWebApplicationContext context;
    static MockMvc mvc;
    UserRepository users;
    UserRoleRepository roles;
    JwtTokenProvider provider;
    User caller, other;
    @BeforeAll static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "rf041-only", Map.of("jwt.expiration.access", "3600000", "jwt.expiration.refresh", "7200000")));
        context.setServletContext(new MockServletContext()); context.register(IsolatedConfiguration.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stop() { context.close(); }
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        users = context.getBean(UserRepository.class); roles = context.getBean(UserRoleRepository.class);
        provider = context.getBean(JwtTokenProvider.class); reset(users, roles); clearInvocations(provider);
        caller = user(7001L, EMAIL); other = user(8001L, "rf041-other@example.invalid");
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
        return put("/confirm/address").contentType("application/json").content("{\"id\":" + id + ",\"address\":\"new-address\"}");
    }
    @ParameterizedTest @CsvSource({"USER,7001", "USER,8001", "UNCONFIRMED_USER,7001", "UNCONFIRMED_USER,8001", "ADMIN,7001", "ADMIN,8001", "OTHER,7001", "OTHER,8001"})
    void beforeValidRolesCanChangeOwnOrOtherAddress(String role, long id) throws Exception {
        mvc.perform(request(id).header("Authorization","Bearer " + token(EMAIL,role,"access",3600)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertEquals("new-address",(id == 7001L ? caller : other).getAddress());
        verify(users).findById(id); verifyNoMoreInteractions(users); verifyNoInteractions(roles,provider);
    }
    @Test void beforeUnauthenticatedIs401() throws Exception {
        mvc.perform(request(8001L)).andExpect(status().isUnauthorized()); verifyNoInteractions(users,roles,provider);
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

