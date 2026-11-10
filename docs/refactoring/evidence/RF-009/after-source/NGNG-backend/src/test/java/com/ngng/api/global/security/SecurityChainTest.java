package com.ngng.api.global.security;

import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.handler.CustomLogoutHandler;
import com.ngng.api.global.security.handler.CustomLogoutSuccessHandler;
import com.ngng.api.global.security.jwt.util.JwtTokenProvider;
import com.ngng.api.global.security.jwt.util.JwtTokenVerifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit beans only: no Boot/application scan, properties, repositories, or external clients. */
class SecurityChainTest {
    static AnnotationConfigWebApplicationContext context;
    static MockMvc mvc;
    static final AtomicInteger calls = new AtomicInteger();

    @BeforeAll static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "rf005-test-only", java.util.Map.of("jwt.expiration.access", "3600000", "jwt.expiration.refresh", "7200000")));
        context.setServletContext(new MockServletContext());
        context.register(TestConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterAll static void stop() { context.close(); }
    @BeforeEach void reset() { calls.set(0); SecurityContextHolder.clearContext(); }
    @AfterEach void noLeakedContext() { assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull(); }

    @ParameterizedTest
    @CsvSource({
            "ADMIN,GET,/admin/probe,200", "USER,GET,/admin/probe,403", "UNCONFIRMED_USER,GET,/admin/probe,403",
            "ADMIN,GET,/chat/probe,200", "USER,GET,/chat/probe,200", "UNCONFIRMED_USER,GET,/chat/probe,403",
            "ADMIN,POST,/products/probe,200", "USER,POST,/products/probe,200", "UNCONFIRMED_USER,POST,/products/probe,403",
            "ADMIN,PUT,/products/probe,200", "USER,PUT,/products/probe,200", "UNCONFIRMED_USER,PUT,/products/probe,403",
            "ADMIN,DELETE,/products/probe,200", "USER,DELETE,/products/probe,200", "UNCONFIRMED_USER,DELETE,/products/probe,403",
            "ADMIN,GET,/my_page/probe,200", "USER,GET,/my_page/probe,200", "UNCONFIRMED_USER,GET,/my_page/probe,200",
            "ADMIN,POST,/confirm/account,403", "USER,POST,/confirm/account,403", "UNCONFIRMED_USER,POST,/confirm/account,200"
    })
    void preservesRoleTable(String role, String method, String path, int status) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)
                .header("Authorization", "Bearer " + TokenFilterTest.token("access", role, 3600)))
                .andExpect(status().is(status));
        assertThat(calls.get()).isEqualTo(status == 200 ? 1 : 0);
    }

    @ParameterizedTest
    @CsvSource({"GET,/products/probe", "GET,/products-other", "GET,/main/child", "POST,/join/child", "GET,/search/probe",
            "GET,/find/probe", "GET,/category/probe", "GET,/login", "GET,/main"})
    void existingSkippedPathsIgnoreMalformedToken(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "broken"))
                .andExpect(status().isOk()).andExpect(content().string("anonymous"));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void existingLogoutStatusAndCookieArePreserved() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/logout")).andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refreshToken=;")));
        assertThat(calls.get()).isZero();
    }

    @Test void refreshHeaderSurvivesDownstreamAuthorizationDenial() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/admin/probe")
                .header("Authorization", "Bearer " + TokenFilterTest.token("access", "USER", -3600))
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", TokenFilterTest.token("refresh", "USER", 3600))))
                .andExpect(status().isForbidden()).andExpect(content().string(""))
                .andExpect(header().string("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")));
        assertThat(calls.get()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"GET,/", "GET,/unknown", "GET,/main-other", "POST,/products/probe", "OPTIONS,/unknown", "GET,/my_page/probe"})
    void unskippedRequestsWithoutCredentialsAreEmpty401(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isUnauthorized())
                .andExpect(content().string("")).andExpect(header().doesNotExist("Authorization"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        assertThat(calls.get()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"GET,/", "GET,/unknown", "GET,/main-other", "OPTIONS,/unknown"})
    void unskippedPermitAllPathsStillAcceptValidCredentials(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer " + TokenFilterTest.token("access", "USER", 3600)))
                .andExpect(status().isOk());
        // MVC의 기본 OPTIONS handler는 controller 본문을 호출하지 않는다.
        assertThat(calls.get()).isEqualTo(method.equals("OPTIONS") ? 0 : 1);
    }

    @Test void invalidAccessDoesNotReachEndpointEvenWithValidRefresh() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/my_page/probe").header("Authorization", "Bearer broken")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", TokenFilterTest.token("refresh", "USER", 3600))))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization"));
        assertThat(calls.get()).isZero();
    }

    @Test void missingAccessRefreshesThroughActualChain() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/my_page/probe")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", TokenFilterTest.token("refresh", "USER", 3600))))
                .andExpect(status().isOk()).andExpect(content().string("rf005@example.test"))
                .andExpect(header().string("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void consecutiveRequestsDoNotRetainAuthentication() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/admin/probe")
                        .header("Authorization", "Bearer " + TokenFilterTest.token("access", "ADMIN", 3600)))
                .andExpect(status().isOk());
        mvc.perform(request(HttpMethod.GET, "/admin/probe")).andExpect(status().isUnauthorized());
        mvc.perform(request(HttpMethod.GET, "/main")).andExpect(status().isOk()).andExpect(content().string("anonymous"));
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void existingCorsPreflightBypassesAuthentication() throws Exception {
        mvc.perform(request(HttpMethod.OPTIONS, "/admin/probe").header("Origin", "https://rf005.example.test")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://rf005.example.test"));
        assertThat(calls.get()).isZero();
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfiguration {
        @Bean @Primary AuthenticationConfiguration testAuthenticationConfiguration() throws Exception {
            AuthenticationConfiguration configuration = mock(AuthenticationConfiguration.class);
            when(configuration.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class));
            return configuration;
        }
        @Bean JwtTokenVerifier verifier() { return new JwtTokenVerifier(TokenFilterTest.SECRET); }
        @Bean JwtTokenProvider provider() {
            JwtTokenProvider provider = new JwtTokenProvider(TokenFilterTest.SECRET);
            ReflectionTestUtils.setField(provider, "accessTokenExpirationMs", 3_600_000);
            return provider;
        }
        @Bean CustomLogoutHandler logoutHandler() { return new CustomLogoutHandler(); }
        @Bean CustomLogoutSuccessHandler logoutSuccessHandler() { return new CustomLogoutSuccessHandler(); }
        @Bean ProbeController controller() { return new ProbeController(); }
    }

    @RestController
    static class ProbeController {
        @RequestMapping("/**") String probe(Authentication authentication) {
            calls.incrementAndGet();
            return authentication == null ? "anonymous" : authentication.getName();
        }
    }
}
