package com.ngng.api.global.security;

import com.ngng.api.global.security.filter.TokenFilter;
import com.ngng.api.global.security.jwt.util.JwtTokenProvider;
import com.ngng.api.global.security.jwt.util.JwtTokenVerifier;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenFilterTest {
    static final String SECRET = Encoders.BASE64URL.encode("rf005-synthetic-key-only-64-bytes-abcdefghijklmnopqrstuvwxyz-123456".getBytes(StandardCharsets.UTF_8));
    static final Key KEY = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64URL.decode(SECRET));
    JwtTokenVerifier verifier;
    JwtTokenProvider provider;
    TokenFilter filter;
    MockHttpServletRequest request;
    MockHttpServletResponse response;
    FilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        verifier = new JwtTokenVerifier(SECRET);
        provider = new JwtTokenProvider(SECRET);
        ReflectionTestUtils.setField(provider, "accessTokenExpirationMs", 3_600_000);
        filter = new TokenFilter(provider, verifier);
        request = new MockHttpServletRequest("GET", "/my_page/profile");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    static String token(String type, String role, long seconds) {
        return Jwts.builder().setSubject("rf005@example.test").claim("role", role).claim("type", type)
                .setExpiration(new Date(System.currentTimeMillis() + seconds * 1000)).signWith(KEY).compact();
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "valid", "invalid"})
    void validAccessKeepsPrincipalAndTakesPriorityOverRefresh(String refresh) throws Exception {
        request.addHeader("Authorization", "Bearer " + token("access", "USER", 3600));
        if (refresh.equals("valid")) request.setCookies(new Cookie("refreshToken", token("refresh", "ADMIN", 3600)));
        if (refresh.equals("invalid")) request.setCookies(new Cookie("refreshToken", "broken"));
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("rf005@example.test");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        assertThat(response.getHeader("Authorization")).isNull();
    }

    @Test void expiredAccessRefreshesInSameRequest() throws Exception {
        request.addHeader("Authorization", "Bearer " + token("access", "USER", -3600));
        request.setCookies(new Cookie("refreshToken", token("refresh", "ADMIN", 3600)));
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        String issued = response.getHeader("Authorization");
        assertThat(issued).startsWith("Bearer ");
        assertThat(verifier.getType(issued)).isEqualTo("access");
        assertThat(verifier.getRole(issued)).isEqualTo("ADMIN");
        assertThat(verifier.getEmail(issued)).isEqualTo("rf005@example.test");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @ParameterizedTest @NullAndEmptySource
    void missingOrEmptyAccessFallsBackToRefresh(String access) throws Exception {
        if (access != null) request.addHeader("Authorization", access);
        request.setCookies(new Cookie("refreshToken", token("refresh", "USER", 3600)));
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(verifier.getType(response.getHeader("Authorization"))).isEqualTo("access");
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "empty", "other"})
    void missingRefreshIsEmpty401(String cookies) throws Exception {
        request.addHeader("Authorization", "Bearer " + token("access", "USER", -3600));
        if (cookies.equals("empty")) request.setCookies(new Cookie[0]);
        if (cookies.equals("other")) request.setCookies(new Cookie("other", "value"));
        assertRejected();
    }

    @ParameterizedTest @MethodSource("badAccessHeaders")
    void invalidAccessCannotUseValidRefresh(String access) throws Exception {
        request.addHeader("Authorization", access);
        request.setCookies(new Cookie("refreshToken", token("refresh", "USER", 3600)));
        assertRejected();
    }

    static Stream<String> badAccessHeaders() {
        String valid = token("access", "USER", 3600);
        String expired = token("access", "USER", -3600);
        return Stream.concat(Stream.of(" ", "undefined", "Basic " + valid, "bearer " + valid, valid,
                        "Bearer ", "Bearer  " + valid, "Bearer " + valid + " ", "Bearer broken", "Bearer null",
                        "Bearer " + token("refresh", "USER", 3600), "Bearer " + token("refresh", "USER", -3600),
                        "Bearer " + tampered(valid), "Bearer " + tampered(expired), "Bearer " + wrongKeyToken(3600), "Bearer " + wrongKeyToken(-3600)),
                invalidClaimTokens("access").map(value -> "Bearer " + value));
    }

    @ParameterizedTest @MethodSource("badRefreshTokens")
    void invalidRefreshIsEmpty401(String refresh) throws Exception {
        request.addHeader("Authorization", "");
        request.setCookies(new Cookie("refreshToken", refresh));
        assertRejected();
    }

    static Stream<String> badRefreshTokens() {
        return Stream.concat(Stream.of("", " ", "undefined", "broken", "Bearer " + token("refresh", "USER", 3600),
                        token("refresh", "USER", 3600) + " ", token("access", "USER", 3600), token("refresh", "USER", -3600),
                        tampered(token("refresh", "USER", 3600)), wrongKeyToken(3600)), invalidClaimTokens("refresh"));
    }

    static Stream<String> invalidClaimTokens(String type) {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (String claim : new String[]{"sub", "role", "type", "exp"}) {
            Map<String, Object> claims = claims(type);
            claims.remove(claim);
            values.add(signClaims(claims));
            claims.put(claim, java.util.List.of("wrong-type"));
            values.add(signClaims(claims));
            if (!claim.equals("exp")) {
                claims.put(claim, " ");
                values.add(signClaims(claims));
            }
        }
        Map<String, Object> claims = claims(type);
        claims.put("exp", "4102444800");
        values.add(signClaims(claims));
        claims = claims(type);
        claims.put("type", "unknown");
        values.add(signClaims(claims));
        for (String claim : new String[]{"sub", "role", "type"}) {
            claims = claims(type);
            claims.put("exp", System.currentTimeMillis() / 1000 - 3600);
            claims.remove(claim);
            values.add(signClaims(claims));
        }
        return values.stream();
    }

    static Map<String, Object> claims(String type) {
        return new LinkedHashMap<>(Map.of("sub", "rf005@example.test", "role", "USER", "type", type,
                "exp", System.currentTimeMillis() / 1000 + 3600));
    }

    static String signClaims(Map<String, Object> claims) {
        try {
            return Jwts.builder().setPayload(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(claims)).signWith(KEY).compact();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String tampered(String token) {
        int start = token.lastIndexOf('.') + 1;
        return token.substring(0, start) + (token.charAt(start) == 'A' ? 'B' : 'A') + token.substring(start + 1);
    }

    static String wrongKeyToken(long seconds) {
        return Jwts.builder().setSubject("rf005@example.test").claim("role", "USER").claim("type", "access")
                .setExpiration(new Date(System.currentTimeMillis() + seconds * 1000))
                .signWith(Keys.hmacShaKeyFor("different-test-key-only-abcdefghijklmnopqrstuvwxyz-1234567890123456".getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Test void firstRefreshCookieStillWins() throws Exception {
        request.addHeader("Authorization", "");
        request.setCookies(new Cookie("refreshToken", "broken"), new Cookie("refreshToken", token("refresh", "USER", 3600)));
        assertRejected();
    }

    @Test void firstValidRefreshCookieIgnoresLaterInvalidDuplicate() throws Exception {
        request.setCookies(new Cookie("other", "x"), new Cookie("refreshToken", token("refresh", "USER", 3600)), new Cookie("refreshToken", "broken"));
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test void existingRoleValuesAreNotWhitelisted() throws Exception {
        request.addHeader("Authorization", "Bearer " + token("access", "LEGACY_ROLE", 3600));
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).extracting("authority").containsExactly("ROLE_LEGACY_ROLE");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void downstreamExceptionsAreNotConvertedTo401(boolean refresh) throws Exception {
        request.addHeader("Authorization", "Bearer " + token("access", "USER", refresh ? -3600 : 3600));
        request.setCookies(new Cookie("refreshToken", token("refresh", "USER", 3600)));
        io.jsonwebtoken.JwtException failure = new io.jsonwebtoken.JwtException("downstream failure");
        doThrow(failure).when(chain).doFilter(request, response);
        assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isSameAs(failure);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Authorization") != null).isEqualTo(refresh);
        verify(chain).doFilter(request, response);
    }

    void assertRejected() throws Exception {
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Authorization")).isNull();
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(chain);
    }
}
