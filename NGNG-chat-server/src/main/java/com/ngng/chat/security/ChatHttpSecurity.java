package com.ngng.chat.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.*;

@Configuration
public class ChatHttpSecurity {
    @Bean public Clock chatClock() { return Clock.systemUTC(); }

    @Bean SecurityFilterChain chatSecurity(HttpSecurity http, ChatTokenVerifier tokens) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers(HttpMethod.GET, "/public-chats/*", "/chat-server", "/chat-server/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> res.sendError(401))
                        .accessDeniedHandler((req, res, ex) -> res.sendError(403)))
                .addFilterBefore(new BearerFilter(tokens), UsernamePasswordAuthenticationFilter.class).build();
    }

    static final class BearerFilter extends OncePerRequestFilter {
        private final ChatTokenVerifier tokens;
        BearerFilter(ChatTokenVerifier tokens) { this.tokens = tokens; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                   FilterChain chain) throws ServletException, IOException {
            var headers = Collections.list(request.getHeaders("Authorization"));
            if (!headers.isEmpty()) {
                try {
                    var identity = tokens.verify(headers);
                    var auth = UsernamePasswordAuthenticationToken.authenticated(identity, null, List.of());
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(auth);
                    SecurityContextHolder.setContext(context);
                } catch (BadCredentialsException ex) { response.sendError(401); return; }
            }
            chain.doFilter(request, response);
        }
    }
}
