package com.ngng.api.user.user.confirmation;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.global.security.jwt.util.JwtTokenProvider;
import com.ngng.api.user.user.dto.request.AccountConfirmRequest;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.*;
import com.ngng.api.user.user.service.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Direct invocation guards + real AuthService, with repository/token doubles; no external services. */
class ConfirmAccountServiceTest {
    UserRepository users;
    UserRoleRepository roles;
    JwtTokenProvider provider;
    ConfirmService service;
    User caller, target;
    MockHttpServletResponse response;
    @BeforeEach void setup() {
        users = mock(UserRepository.class); roles = mock(UserRoleRepository.class); provider = mock(JwtTokenProvider.class);
        service = new ConfirmService(users,roles,provider,new AuthService(users));
        caller = user(id(7001)); target = user(id(7001)); response = new MockHttpServletResponse();
        when(users.findUserByEmail("rf020@example.invalid")).thenReturn(Optional.of(caller));
        when(users.findById(7001L)).thenReturn(Optional.of(target));
        when(roles.findByRoleType("USER")).thenReturn(Role.builder().roleType("USER").build());
        when(provider.createAccessToken(any())).thenReturn("Bearer fixture-access");
        when(provider.createRefreshToken(any())).thenReturn("fixture-refresh");
        authenticate(new CustomUserDetails(user(null)), "UNCONFIRMED_USER", true);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @SuppressWarnings("removal") static Long id(long value) { return new Long(value); }
    User user(Long id) { return User.builder().userId(id).email("rf020@example.invalid").role(Role.builder().roleType("UNCONFIRMED_USER").build()).build(); }
    void authenticate(Object principal,String role,boolean authenticated) {
        var value = new UsernamePasswordAuthenticationToken(principal,"unused",List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        if (!authenticated) value.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(value);
    }
    void call(Long id) { service.accountConfirm(new AccountConfirmRequest(id,"fixture-bank","fixture-account"),new Cookie("refreshToken","unused"),response); }
    void denied(Long id, boolean callerLookup) {
        var error = assertThrows(ResponseStatusException.class, () -> call(id)); assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode());
        if (callerLookup) verify(users).findUserByEmail("rf020@example.invalid");
        verifyNoMoreInteractions(users); verifyNoInteractions(roles,provider);
        for (User value : List.of(caller,target)) {
            assertNull(value.getAccountBank()); assertNull(value.getAccountNumber()); assertEquals("UNCONFIRMED_USER",value.getRole().getRoleType());
        }
        assertTrue(response.getHeaderNames().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "unauthenticated", "anonymous", "anonymous-custom", "null-principal", "string",
            "null-user", "null-email", "blank-email", "USER", "ADMIN", "role-mismatch"})
    void invalidAuthenticationIs403BeforeRepositoryAccess(String kind) {
        switch (kind) {
            case "missing" -> SecurityContextHolder.clearContext();
            case "unauthenticated" -> authenticate(new CustomUserDetails(user(null)),"UNCONFIRMED_USER",false);
            case "anonymous", "anonymous-custom" -> SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key",
                    kind.equals("anonymous") ? "anonymousUser" : new CustomUserDetails(user(null)),List.of(new SimpleGrantedAuthority("ROLE_UNCONFIRMED_USER"))));
            case "null-principal" -> authenticate(null,"UNCONFIRMED_USER",true);
            case "string" -> authenticate("principal","UNCONFIRMED_USER",true);
            case "null-user" -> authenticate(new CustomUserDetails(null),"UNCONFIRMED_USER",true);
            case "null-email", "blank-email" -> {
                User value = user(null); value.setEmail(kind.equals("null-email") ? null : " ");
                authenticate(new CustomUserDetails(value),"UNCONFIRMED_USER",true);
            }
            case "USER", "ADMIN" -> authenticate(new CustomUserDetails(user(null)),kind,true);
            case "role-mismatch" -> authenticate(new CustomUserDetails(user(null)),"OTHER",true);
        }
        denied(7001L,false);
    }
    @ParameterizedTest @ValueSource(strings = {"other", "missing-target", "null-request", "missing-caller", "null-caller-id", "both-null"})
    void callerAndIdFailuresOnlyReadCaller(String kind) {
        Long requested = 8001L;
        switch (kind) {
            case "missing-target" -> requested = 9001L;
            case "null-request" -> requested = null;
            case "missing-caller" -> when(users.findUserByEmail("rf020@example.invalid")).thenReturn(Optional.empty());
            case "null-caller-id" -> caller.setUserId(null);
            case "both-null" -> { caller.setUserId(null); requested = null; }
        }
        denied(requested,true);
    }
    @Test void equalIdsOutsideLongCacheUseValueEqualityAndOriginalMutationOrder() {
        Long requested = id(7001); assertNotSame(requested,caller.getUserId()); assertEquals(requested,caller.getUserId());
        call(requested);
        var order = inOrder(users,roles,provider);
        order.verify(users).findUserByEmail("rf020@example.invalid"); order.verify(users).findById(requested);
        order.verify(users).existsByAccountNumber("fixture-account"); order.verify(roles).findByRoleType("USER");
        order.verify(provider).createAccessToken(argThat(details -> details.getUser() == target && "USER".equals(details.getRole())));
        order.verify(provider).createRefreshToken(argThat(details -> details.getUser() == target && "USER".equals(details.getRole())));
        verifyNoMoreInteractions(users,roles,provider);
        assertEquals("fixture-bank",target.getAccountBank()); assertEquals("fixture-account",target.getAccountNumber()); assertEquals("USER",target.getRole().getRoleType());
        assertNull(caller.getAccountNumber());
    }
    @Test void principalRoleFieldIsNotDereferencedBeforeAuthenticationAuthorities() {
        User principal = user(null); principal.setRole(null);
        authenticate(new CustomUserDetails(principal),"UNCONFIRMED_USER",true);
        call(7001L); assertEquals("USER",target.getRole().getRoleType());
    }
    @Test void matchingIdMissingTargetKeepsExistingException() {
        when(users.findById(7001L)).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> call(7001L));
        verify(users).findUserByEmail("rf020@example.invalid"); verify(users).findById(7001L);
        verifyNoMoreInteractions(users); verifyNoInteractions(roles,provider); assertNull(target.getAccountNumber());
    }
    @Test void repositoryFailureIsNotMisreportedAs403() {
        RuntimeException failure = new IllegalStateException("synthetic repository failure");
        when(users.findUserByEmail("rf020@example.invalid")).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> call(7001L)));
        verify(users).findUserByEmail("rf020@example.invalid"); verifyNoMoreInteractions(users); verifyNoInteractions(roles,provider);
    }
}
