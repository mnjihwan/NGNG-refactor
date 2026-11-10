package com.ngng.api.user.user.address;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.global.security.jwt.util.JwtTokenProvider;
import com.ngng.api.user.user.dto.request.AddressConfirmRequest;
import com.ngng.api.user.user.entity.*;
import com.ngng.api.user.user.repository.*;
import com.ngng.api.user.user.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real AuthService + repository/token doubles; no DB or external clients. */
class ConfirmAddressServiceTest {
    static final String EMAIL = "rf041@example.invalid";
    UserRepository users;
    UserRoleRepository roles;
    JwtTokenProvider tokens;
    ConfirmService service;
    User caller, target;
    @BeforeEach void setup() {
        users = mock(UserRepository.class); roles = mock(UserRoleRepository.class); tokens = mock(JwtTokenProvider.class);
        service = new ConfirmService(users,roles,tokens,new AuthService(users));
        caller = user(id(7001)); target = user(id(7001));
        when(users.findUserByEmail(EMAIL)).thenReturn(Optional.of(caller));
        when(users.findById(7001L)).thenReturn(Optional.of(target));
        authenticate(new CustomUserDetails(user(null)),true);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @SuppressWarnings("removal") static Long id(long value) { return new Long(value); }
    User user(Long id) {
        return User.builder().userId(id).email(EMAIL).address("old-address").visible(true)
                .accountBank("old-bank").accountNumber("old-account").role(Role.builder().roleType("USER").build()).build();
    }
    void authenticate(Object principal, boolean authenticated) {
        var auth = new UsernamePasswordAuthenticationToken(principal,"unused",List.of(new SimpleGrantedAuthority("ROLE_USER")));
        if (!authenticated) auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
    void unchanged() {
        for (User user : List.of(caller,target)) {
            assertEquals("old-address",user.getAddress()); assertEquals("old-bank",user.getAccountBank());
            assertEquals("old-account",user.getAccountNumber()); assertEquals("USER",user.getRole().getRoleType()); assertTrue(user.getVisible());
        }
        verifyNoInteractions(roles,tokens);
    }
    void deny(AddressConfirmRequest request, boolean callerLookup) {
        var error = assertThrows(ResponseStatusException.class, () -> service.addressConfirm(request));
        assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode());
        if (callerLookup) verify(users).findUserByEmail(EMAIL);
        verifyNoMoreInteractions(users); unchanged();
    }
    @ParameterizedTest @ValueSource(strings={"missing", "unauthenticated", "anonymous", "anonymous-custom", "null-principal", "string", "null-user", "null-email", "blank-email"})
    void invalidAuthenticationIs403BeforeRepository(String kind) {
        switch (kind) {
            case "missing" -> SecurityContextHolder.clearContext();
            case "unauthenticated" -> authenticate(new CustomUserDetails(user(null)),false);
            case "anonymous", "anonymous-custom" -> SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key",
                    kind.equals("anonymous") ? "anonymousUser" : new CustomUserDetails(user(null)),List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            case "null-principal" -> authenticate(null,true);
            case "string" -> authenticate("principal",true);
            case "null-user" -> authenticate(new CustomUserDetails(null),true);
            case "null-email", "blank-email" -> {
                User principal = user(null); principal.setEmail(kind.equals("null-email") ? null : " \t");
                authenticate(new CustomUserDetails(principal),true);
            }
        }
        deny(new AddressConfirmRequest(7001L,"new-address"),false);
    }
    @ParameterizedTest @ValueSource(strings={"other", "missing-target", "null-id", "missing-caller", "null-caller-id", "both-null", "null-request"})
    void identityFailuresReadOnlyCaller(String kind) {
        Long requested = 8001L;
        switch (kind) {
            case "missing-target" -> requested = 9001L;
            case "null-id" -> requested = null;
            case "missing-caller" -> when(users.findUserByEmail(EMAIL)).thenReturn(Optional.empty());
            case "null-caller-id" -> caller.setUserId(null);
            case "both-null" -> { caller.setUserId(null); requested = null; }
        }
        deny(kind.equals("null-request") ? null : new AddressConfirmRequest(requested,"new-address"),true);
    }
    @ParameterizedTest @ValueSource(strings={"USER", "UNCONFIRMED_USER", "ADMIN", "OTHER"})
    void sameValueDifferentLongsAndAllExistingRolesSucceed(String role) {
        User principal = user(null); principal.setRole(Role.builder().roleType(role).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(principal),null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        Long requested = id(7001); assertNotSame(requested,caller.getUserId()); assertNotSame(caller,target);
        assertTrue(service.addressConfirm(new AddressConfirmRequest(requested,"new-address")).isConfirmAddress());
        assertEquals("new-address",target.getAddress()); assertEquals("old-address",caller.getAddress());
        assertEquals("old-bank",target.getAccountBank()); assertEquals("old-account",target.getAccountNumber());
        assertEquals("USER",target.getRole().getRoleType()); assertTrue(target.getVisible());
        var order = inOrder(users); order.verify(users).findUserByEmail(EMAIL); order.verify(users).findById(requested);
        verifyNoMoreInteractions(users); verifyNoInteractions(roles,tokens);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ", "  fixture address  "})
    void ownAddressKeepsNullEmptyWhitespaceAndNoNormalization(String address) {
        service.addressConfirm(new AddressConfirmRequest(7001L,address)); assertEquals(address,target.getAddress());
        verify(users).findUserByEmail(EMAIL); verify(users).findById(7001L); verifyNoMoreInteractions(users); verifyNoInteractions(roles,tokens);
    }
    @Test void nullPrincipalRoleIsNotReadAndNoNewRoleRestrictionExists() {
        User principal = user(null); principal.setRole(null); authenticate(new CustomUserDetails(principal),true);
        service.addressConfirm(new AddressConfirmRequest(7001L,"new-address")); assertEquals("new-address",target.getAddress());
    }
    @Test void repositoryFailurePropagatesRatherThan403() {
        RuntimeException failure = new IllegalStateException("synthetic database failure");
        when(users.findUserByEmail(EMAIL)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.addressConfirm(new AddressConfirmRequest(7001L,"new-address"))));
        verify(users).findUserByEmail(EMAIL); verifyNoMoreInteractions(users); unchanged();
    }
    @Test void matchingCallerButMissingTargetKeepsExistingException() {
        when(users.findById(7001L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.addressConfirm(new AddressConfirmRequest(7001L,"new-address")));
        verify(users).findUserByEmail(EMAIL); verify(users).findById(7001L); verifyNoMoreInteractions(users); unchanged();
    }
}
