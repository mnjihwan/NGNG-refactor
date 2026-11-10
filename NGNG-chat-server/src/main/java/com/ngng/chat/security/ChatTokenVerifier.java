package com.ngng.chat.security;

import com.ngng.chat.user.repository.UserRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.time.Clock;
import java.util.*;

@Component
public class ChatTokenVerifier {
    private final Key key;
    private final Clock clock;
    private final UserRepository users;

    public ChatTokenVerifier(@Value("${jwt.secret.key}") String secret, Clock clock, UserRepository users) {
        key = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(secret));
        this.clock = clock;
        this.users = users;
    }

    public ChatIdentity verify(List<String> authorization) {
        Claims claims = parse(authorization);
        String email = claims.getSubject();
        var matches = users.findAllByEmail(email);
        if (matches.size() != 1 || matches.get(0).getId() == null) throw invalid();
        return new ChatIdentity(matches.get(0).getId(), email, claims.get("role", String.class), claims.getExpiration().toInstant());
    }

    private Claims parse(List<String> authorization) {
        try {
            if (authorization == null || authorization.size() != 1) throw invalid();
            String header = authorization.get(0);
            if (header == null || !header.startsWith("Bearer ") || header.length() <= 7 ||
                    header.substring(7).chars().anyMatch(Character::isWhitespace)) throw invalid();
            Jws<Claims> jwt = Jwts.parserBuilder().setSigningKey(key)
                    .setClock(() -> Date.from(clock.instant())).build().parseClaimsJws(header.substring(7));
            if (!Set.of("HS256", "HS384", "HS512").contains(jwt.getHeader().getAlgorithm())) throw invalid();
            Claims claims = jwt.getBody();
            Object subject = claims.get("sub"), role = claims.get("role");
            if (!"access".equals(claims.get("type")) || !(subject instanceof String email) || email.isBlank() ||
                    !(role instanceof String roleName) || roleName.isBlank() || !(claims.get("exp") instanceof Number) ||
                    (claims.containsKey("nbf") && !(claims.get("nbf") instanceof Number)) || claims.getExpiration() == null ||
                    !clock.instant().isBefore(claims.getExpiration().toInstant())) throw invalid();
            return claims;
        } catch (JwtException | IllegalArgumentException | IllegalStateException ex) {
            throw invalid();
        }
    }

    private BadCredentialsException invalid() { return new BadCredentialsException("Invalid chat credential"); }
}
