package com.ngng.chat.security;

import com.ngng.chat.user.entity.User;
import com.ngng.chat.user.repository.UserRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.BadCredentialsException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatTokenVerifierTest {
    final ChatSecurityFixture.FixtureClock clock = new ChatSecurityFixture.FixtureClock();
    final UserRepository users = mock(UserRepository.class);
    final ChatTokenVerifier verifier = new ChatTokenVerifier(Encoders.BASE64URL.encode(ChatSecurityFixture.KEY),clock,users);
    @BeforeEach void users() { when(users.findAllByEmail("seller@fixture.invalid")).thenReturn(List.of(User.builder().id(1L).build())); }
    JwtBuilder valid() { return Jwts.builder().setSubject("seller@fixture.invalid").claim("role","USER").claim("type","access")
            .setExpiration(Date.from(clock.instant().plusSeconds(100))); }
    String sign(JwtBuilder builder) { return "Bearer "+builder.signWith(Keys.hmacShaKeyFor(ChatSecurityFixture.KEY)).compact(); }
    @Test void backendHmacAlgorithmsResolveEmailToServerId() {
        for (var algorithm: List.of(SignatureAlgorithm.HS256,SignatureAlgorithm.HS384,SignatureAlgorithm.HS512)) {
            String token = "Bearer "+valid().signWith(Keys.hmacShaKeyFor(ChatSecurityFixture.KEY),algorithm).compact();
            var principal = verifier.verify(List.of(token));
            assertEquals(1L,principal.userId()); assertEquals("seller@fixture.invalid",principal.getName());
            assertFalse(principal.toString().contains(token));
        }
    }
    @Test void missingWrongTypeAndMalformedRequiredClaimsAreRejected() {
        List<String> invalid = List.of(
                sign(valid().claim("type","refresh")), sign(valid().claim("type",null)),
                sign(valid().claim("sub",1)), sign(valid().setSubject(" ")), sign(valid().setSubject(null)),
                sign(valid().claim("role",1)), sign(valid().claim("role","")), sign(valid().claim("role",null)),
                sign(valid().setExpiration(null)), sign(valid().setExpiration(Date.from(clock.instant()))),
                sign(valid().setExpiration(Date.from(clock.instant().minusSeconds(1)))),
                "Bearer "+valid().compact());
        for (String token:invalid) assertThrows(BadCredentialsException.class,()->verifier.verify(List.of(token)));
    }
    @Test void malformedWrongSignatureOrSchemeAndDuplicateHeadersAreRejected() {
        String token=sign(valid());
        byte[] other = "other-synthetic-signing-key-0123456789-abcdefghijklmnopqrstuvxyz-12345".getBytes();
        for(String invalid:List.of("", "Bearer ",token.substring(7),"bearer "+token.substring(7),"Bearer garbage",
                token+" ","Bearer "+valid().signWith(Keys.hmacShaKeyFor(other)).compact()))
            assertThrows(BadCredentialsException.class,()->verifier.verify(List.of(invalid)));
        assertThrows(BadCredentialsException.class,()->verifier.verify(List.of(token,token)));
        assertThrows(BadCredentialsException.class,()->verifier.verify(List.of()));
    }
    @Test void missingOrAmbiguousEmailMappingFailsClosed() {
        when(users.findAllByEmail("seller@fixture.invalid")).thenReturn(List.of());
        assertThrows(BadCredentialsException.class,()->verifier.verify(List.of(sign(valid()))));
        when(users.findAllByEmail("seller@fixture.invalid")).thenReturn(List.of(User.builder().id(1L).build(),User.builder().id(2L).build()));
        assertThrows(BadCredentialsException.class,()->verifier.verify(List.of(sign(valid()))));
    }
    @Test void malformedTemporalClaimsBecomeAuthenticationFailureWithoutMaskingDatabaseErrors() {
        for(String token:ChatSecurityFixture.invalidTemporalTokens(clock.instant()))
            assertThrows(BadCredentialsException.class,()->verifier.verify(List.of(token)));
        String malformed=ChatSecurityFixture.signedClaims(Map.of("sub","seller@fixture.invalid","role","USER","type","access","exp",Map.of("bad",1)));
        assertThrows(IllegalStateException.class,()->Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(ChatSecurityFixture.KEY)).build()
                .parseClaimsJws(malformed.substring(7)));
        var databaseFailure=new IllegalStateException("synthetic repository outage");
        when(users.findAllByEmail("seller@fixture.invalid")).thenThrow(databaseFailure);
        assertSame(databaseFailure,assertThrows(IllegalStateException.class,()->verifier.verify(List.of(sign(valid())))));
    }
    @Test void asymmetricTokensAndUnknownAlgorithmsAreRejected() {
        var keyPair=Keys.keyPairFor(SignatureAlgorithm.RS256);
        assertThrows(BadCredentialsException.class,()->verifier.verify(List.of("Bearer "+valid().signWith(keyPair.getPrivate()).compact())));
        String[] pieces=sign(valid()).substring(7).split("\\.");
        String unknown="Bearer "+Encoders.BASE64URL.encode("{\"alg\":\"HS999\"}".getBytes())+"."+pieces[1]+"."+pieces[2];
        assertThrows(BadCredentialsException.class,()->verifier.verify(List.of(unknown)));
    }
}
