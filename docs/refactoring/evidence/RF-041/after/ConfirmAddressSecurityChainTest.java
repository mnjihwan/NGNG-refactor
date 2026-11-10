package com.ngng.api.user.user.address;

import com.ngng.api.global.security.config.SecurityConfig;
import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.global.security.handler.*;
import com.ngng.api.global.security.jwt.util.*;
import com.ngng.api.user.user.controller.ConfirmController;
import com.ngng.api.user.user.controller.AdminUserController;
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
        caller.setAddress("old-caller"); other.setAddress("old-other");
        caller.setAccountBank("old-bank"); caller.setAccountNumber("old-account");
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
    MockHttpServletRequestBuilder access(Long id, String role) {
        return request(id).header("Authorization","Bearer " + token(EMAIL,role,"access",3600));
    }
    void assertAccessToken(String raw,String role) {
        assertNotNull(raw); assertTrue(raw.startsWith("Bearer "));
        Claims claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SECRET))).build().parseClaimsJws(raw.substring(7)).getBody();
        assertEquals(EMAIL,claims.getSubject()); assertEquals(role,claims.get("role")); assertEquals("access",claims.get("type"));
    }
    void unchanged() {
        assertEquals("old-caller",caller.getAddress()); assertEquals("old-other",other.getAddress());
        assertEquals("old-bank",caller.getAccountBank()); assertEquals("old-account",caller.getAccountNumber());
        assertNull(other.getAccountBank()); assertNull(other.getAccountNumber());
        assertEquals("UNCONFIRMED_USER",caller.getRole().getRoleType()); assertEquals("UNCONFIRMED_USER",other.getRole().getRoleType());
        verifyNoInteractions(roles); verify(provider,never()).createRefreshToken(any()); verify(users,never()).save(any());
    }
    void ownSuccess() {
        assertEquals("new-address",caller.getAddress()); assertEquals("old-other",other.getAddress());
        assertEquals("old-bank",caller.getAccountBank()); assertEquals("old-account",caller.getAccountNumber());
        assertEquals("UNCONFIRMED_USER",caller.getRole().getRoleType()); assertEquals("UNCONFIRMED_USER",other.getRole().getRoleType());
        var order=inOrder(users); order.verify(users).findUserByEmail(EMAIL); order.verify(users).findById(7001L);
        verifyNoMoreInteractions(users); verifyNoInteractions(roles); verify(provider,never()).createRefreshToken(any());
    }
    @ParameterizedTest @ValueSource(strings={"USER", "UNCONFIRMED_USER", "ADMIN", "OTHER"})
    void ownAddressAllowsEveryPreviouslyAcceptedRoleWithoutRefreshCookie(String role) throws Exception {
        mvc.perform(access(7001L,role)).andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization")).andExpect(header().doesNotExist("Set-Cookie"));
        ownSuccess(); verifyNoInteractions(provider);
    }
    @ParameterizedTest @CsvSource({"USER,8001", "USER,9001", "UNCONFIRMED_USER,8001", "UNCONFIRMED_USER,9001", "ADMIN,8001", "ADMIN,9001", "OTHER,8001", "OTHER,9001"})
    void otherOrMissingTargetIs403BeforeTargetReadForEveryRole(String role,long id) throws Exception {
        mvc.perform(access(id,role)).andExpect(status().isForbidden()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization")).andExpect(header().doesNotExist("Set-Cookie"));
        verify(users).findUserByEmail(EMAIL); verifyNoMoreInteractions(users); unchanged(); verifyNoInteractions(provider);
    }
    @ParameterizedTest @ValueSource(strings={"null-id", "missing-caller", "null-caller-id", "both-null"})
    void missingIdentityIs403AndNoWrite(String kind) throws Exception {
        Long id=7001L;
        if (kind.equals("null-id") || kind.equals("both-null")) id=null;
        if (kind.equals("missing-caller")) doReturn(Optional.empty()).when(users).findUserByEmail(EMAIL);
        if (kind.equals("null-caller-id") || kind.equals("both-null")) caller.setUserId(null);
        mvc.perform(access(id,"USER")).andExpect(status().isForbidden());
        verify(users).findUserByEmail(EMAIL); verifyNoMoreInteractions(users); unchanged(); verifyNoInteractions(provider);
    }
    @ParameterizedTest @CsvSource({"missing,USER,true", "expired,USER,true", "missing,USER,false", "expired,USER,false",
            "missing,UNCONFIRMED_USER,true", "expired,UNCONFIRMED_USER,false", "missing,ADMIN,true", "expired,ADMIN,false", "missing,OTHER,true", "expired,OTHER,false"})
    void refreshFallbackKeepsOnlyCallerAccessOnOwn204OrOther403(String accessKind,String role,boolean own) throws Exception {
        var request=request(own ? 7001L : 8001L).cookie(new Cookie("refreshToken",token(EMAIL,role,"refresh",3600)));
        if (accessKind.equals("expired")) request.header("Authorization","Bearer " + token(EMAIL,role,"access",-3600));
        var result=mvc.perform(request).andExpect(status().is(own ? 204 : 403)).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertAccessToken(result.getResponse().getHeader("Authorization"),role);
        verify(provider).createAccessToken(any()); verify(provider,never()).createRefreshToken(any());
        if (own) ownSuccess(); else { verify(users).findUserByEmail(EMAIL); verifyNoMoreInteractions(users); unchanged(); }
    }
    @ParameterizedTest @CsvSource({"other,true", "malformed,true", "other,false", "malformed,false"})
    void validAccessIdentityWinsOverPresentRefresh(String cookieKind,boolean own) throws Exception {
        String refresh=cookieKind.equals("other") ? token(other.getEmail(),"ADMIN","refresh",3600) : "malformed";
        mvc.perform(access(own ? 7001L : 8001L,"USER").cookie(new Cookie("refreshToken",refresh)))
                .andExpect(status().is(own ? 204 : 403)).andExpect(header().doesNotExist("Authorization")).andExpect(header().doesNotExist("Set-Cookie"));
        if (own) ownSuccess(); else { verify(users).findUserByEmail(EMAIL); verifyNoMoreInteractions(users); unchanged(); }
        verifyNoInteractions(provider);
    }
    @ParameterizedTest @ValueSource(strings={"missing", "malformed", "signature", "access-type", "refresh-type", "bad-refresh", "blank-role", "blank-subject"})
    void invalidCredentialsAre401WithoutServiceAccess(String kind) throws Exception {
        var request=request(8001L);
        switch(kind) {
            case "malformed" -> request.header("Authorization","Bearer invalid");
            case "signature" -> { String raw=token(EMAIL,"USER","access",3600); int dot=raw.lastIndexOf('.')+1; request.header("Authorization","Bearer " + raw.substring(0,dot)+(raw.charAt(dot)=='A' ? "B" : "A")+raw.substring(dot+1)); }
            case "access-type" -> request.header("Authorization","Bearer " + token(EMAIL,"USER","refresh",3600));
            case "refresh-type" -> request.cookie(new Cookie("refreshToken",token(EMAIL,"USER","access",3600)));
            case "bad-refresh" -> request.cookie(new Cookie("refreshToken","invalid"));
            case "blank-role" -> request.header("Authorization","Bearer " + token(EMAIL," ","access",3600));
            case "blank-subject" -> request.header("Authorization","Bearer " + token(" ","USER","access",3600));
        }
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization")).andExpect(header().doesNotExist("Set-Cookie"));
        verifyNoInteractions(users,provider); unchanged();
    }
    @ParameterizedTest @ValueSource(strings={"malformed", "missing", "null", "invalid-id"})
    void malformedOrMissingBodyRetainsMvc400(String kind) throws Exception {
        String body=switch(kind) { case "malformed" -> "{"; case "missing" -> ""; case "null" -> "null"; default -> "{\"id\":\"not-number\"}"; };
        mvc.perform(put("/confirm/address").contentType("application/json").content(body)
                .header("Authorization","Bearer " + token(EMAIL,"USER","access",3600))).andExpect(status().isBadRequest());
        verifyNoInteractions(users,provider); unchanged();
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "  new-address  "})
    void ownNullEmptyWhitespaceAddressPreservesOriginalAssignment(String address) throws Exception {
        String jsonAddress=address == null ? "null" : "\""+address+"\"";
        mvc.perform(put("/confirm/address").contentType("application/json").content("{\"id\":7001,\"address\":"+jsonAddress+"}")
                .header("Authorization","Bearer " + token(EMAIL,"USER","access",3600))).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertEquals(address,caller.getAddress()); assertEquals("old-other",other.getAddress());
        verify(users).findUserByEmail(EMAIL); verify(users).findById(7001L); verifyNoMoreInteractions(users); verifyNoInteractions(roles,provider);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN", "USER", "UNCONFIRMED_USER", "OTHER"})
    void separateAdminEndpointRetainsAdminOtherAddressEditAndRoleBoundary(String role) throws Exception {
        mvc.perform(put("/admin/users/8001").contentType("application/json").content("{\"userId\":8001,\"address\":\"admin-address\"}")
                .header("Authorization","Bearer " + token(EMAIL,role,"access",3600)))
                .andExpect(status().is(role.equals("ADMIN") ? 200 : 403));
        if (role.equals("ADMIN")) {
            assertEquals("admin-address",other.getAddress()); assertEquals("old-caller",caller.getAddress());
            verify(users,times(2)).findById(8001L); verifyNoMoreInteractions(users);
        } else { verifyNoInteractions(users); unchanged(); }
        verifyNoInteractions(roles,provider);
    }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, ConfirmController.class, ConfirmService.class, AuthService.class, AdminUserController.class})
    static class IsolatedConfiguration {
        @Bean @Primary AuthenticationConfiguration authenticationConfiguration() throws Exception {
            var value = mock(AuthenticationConfiguration.class); when(value.getAuthenticationManager()).thenReturn(mock(AuthenticationManager.class)); return value;
        }
        @Bean JwtTokenVerifier verifier() { return new JwtTokenVerifier(SECRET); }
        @Bean JwtTokenProvider provider() { return spy(new JwtTokenProvider(SECRET)); }
        @Bean CustomLogoutHandler logoutHandler() { return new CustomLogoutHandler(); }
        @Bean CustomLogoutSuccessHandler logoutSuccessHandler() { return new CustomLogoutSuccessHandler(); }
        @Bean com.ngng.api.user.point.service.PointHistoryService points() { return mock(com.ngng.api.user.point.service.PointHistoryService.class); }
        @Bean JoinService joinService() { return mock(JoinService.class); }
        @Bean UserService userService(UserRepository users, AuthService auth, com.ngng.api.user.point.service.PointHistoryService points) {
            return new UserService(users,points,mock(com.ngng.api.transaction.service.TransactionDetailsService.class),
                    mock(com.ngng.api.product.product.service.ProductService.class),mock(org.springframework.security.crypto.password.PasswordEncoder.class),auth);
        }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean UserRoleRepository roles() { return mock(UserRoleRepository.class); }
    }
}


