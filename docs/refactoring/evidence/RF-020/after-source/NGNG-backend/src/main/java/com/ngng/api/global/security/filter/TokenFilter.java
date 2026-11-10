package com.ngng.api.global.security.filter;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.global.security.jwt.util.JwtTokenProvider;
import com.ngng.api.global.security.jwt.util.JwtTokenVerifier;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class TokenFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final JwtTokenVerifier tokenVerifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        Claims claims;
        boolean refresh;
        try {
            claims = readAccessClaims(request.getHeader(HttpHeaders.AUTHORIZATION));
            refresh = claims == null;
            if (refresh) {
                claims = tokenVerifier.parseRawToken(findRefreshToken(request.getCookies()));
                validateClaims(claims, "refresh");
            }
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        CustomUserDetails customUserDetails = createCustomUserDetails(claims);
        if (refresh) {
            response.setHeader(HttpHeaders.AUTHORIZATION, tokenProvider.createAccessToken(customUserDetails));
        }
        Authentication authentication = new UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // 업무/인가 예외를 JWT 거부 응답으로 바꾸지 않는다.
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 해당 패턴과 일치한 요청일 경우 TokenFilter를 거치지 않도록 설정
        Pattern uriPattern = Pattern.compile("/(login|logout|search|join|find|category|main)(/.*)?");

        return (uri.startsWith("/products") && method.equals("GET")) || uriPattern.matcher(uri).matches();
    }

    // createAccessToken이 UserDetails를 인자로 받기 때문에 token의 정보를 기준으로 UserDetails를 생성하는 메서드
    private CustomUserDetails createCustomUserDetails(Claims claims) {

        String email = claims.getSubject();
        String role = claims.get("role", String.class);

        return new CustomUserDetails(User.builder()
                .email(email)
                .role(Role.builder()
                        .roleType(role)
                        .build())
                .build());
    }

    private Claims readAccessClaims(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (!token.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid access token scheme");
        }
        try {
            Claims claims = tokenVerifier.parseRawToken(token.substring(7));
            validateClaims(claims, "access");
            return claims;
        } catch (ExpiredJwtException e) {
            // 만료됐어도 서명·type·필수 claim이 유효한 access만 fallback한다.
            validateClaims(e.getClaims(), "access");
            return null;
        }
    }

    private String findRefreshToken(Cookie[] cookies) {
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        throw new IllegalArgumentException("Missing refresh token");
    }

    private void validateClaims(Claims claims, String expectedType) {
        if (!expectedType.equals(claims.get("type"))
                || !(claims.get(Claims.SUBJECT) instanceof String subject) || subject.isBlank()
                || !(claims.get("role") instanceof String role) || role.isBlank()
                || !(claims.get(Claims.EXPIRATION) instanceof Number) || claims.getExpiration() == null) {
            throw new IllegalArgumentException("Invalid token claims");
        }
    }
}
