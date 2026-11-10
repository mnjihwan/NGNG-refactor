package com.ngng.api.user.user.dropout;

import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.global.security.handler.*;
import com.ngng.api.global.security.jwt.util.*;
import com.ngng.api.user.user.controller.DropOutController;
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
        caller = spy(User.builder().userId(7001L).email(EMAIL).visible(true).role(Role.builder().roleType("USER").build()).build());
        target = spy(User.builder().userId(7002L).email("rf042-target@example.invalid").visible(true)
                .role(Role.builder().roleType("USER").build()).build());
        when(users.findUserByEmail(EMAIL)).thenReturn(Optional.of(caller));
        when(users.findById(7001L)).thenReturn(Optional.of(caller));
        when(users.findById(7002L)).thenReturn(Optional.of(target));
    }
    @AfterEach void cleared() { assertNull(SecurityContextHolder.getContext().getAuthentication()); }
    private static String token(String role) {
        return Jwts.builder().setSubject(EMAIL).claim("role", role).claim("type", "access")
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SECRET))).compact();
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "UNCONFIRMED_USER", "ADMIN"})
    void beforeAuthenticatedOtherUserCanBeToggled(String role) throws Exception {
        mvc.perform(delete("/dropout").contentType("application/json").content("{\"id\":7002}")
                        .header("Authorization", "Bearer " + token(role)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertTrue(caller.getVisible()); assertFalse(target.getVisible());
        verify(users).findById(7002L); verify(target).changeVisible(); verify(users, never()).findUserByEmail(any());
    }
    @Test void beforeSameTokenCanReactivateSelf() throws Exception {
        String access = token("USER");
        for (boolean visible : List.of(false, true)) {
            mvc.perform(delete("/dropout").contentType("application/json").content("{\"id\":7001}")
                            .header("Authorization", "Bearer " + access)).andExpect(status().isNoContent());
            assertEquals(visible, caller.getVisible());
        }
    }
    @Test void beforeUnknownNonblankRoleCanToggleSelf() throws Exception {
        mvc.perform(delete("/dropout").contentType("application/json").content("{\"id\":7001}")
                .header("Authorization", "Bearer " + token("FUTURE_ROLE"))).andExpect(status().isNoContent());
        assertFalse(caller.getVisible());
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, DropOutController.class, DropOutService.class, AuthService.class})
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
    }
}
