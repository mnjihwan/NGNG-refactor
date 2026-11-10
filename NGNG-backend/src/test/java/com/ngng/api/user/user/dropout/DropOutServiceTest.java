package com.ngng.api.user.user.dropout;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.user.user.dto.request.DropOutRequest;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import com.ngng.api.user.user.service.DropOutService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Repository/AuthService doubles: no datasource, transaction resource, or physical commit. */
class DropOutServiceTest {
    private UserRepository users;
    private AuthService auth;
    private DropOutService service;
    private User caller, target;
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext(); users = mock(UserRepository.class); auth = mock(AuthService.class);
        service = new DropOutService(users, auth);
        caller = spy(fixture(7001L, "caller", true)); target = spy(fixture(7002L, "target", true));
        authenticate("USER"); when(auth.readAuthUser()).thenReturn(caller);
        when(users.findById(7001L)).thenReturn(Optional.of(caller));
        when(users.findById(7002L)).thenReturn(Optional.of(target));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    static User fixture(Long id, String suffix, Boolean visible) {
        return User.builder().userId(id).email("rf042-" + suffix + "@example.invalid").name("fixture-name")
                .nickname("fixture-nickname").password("fixture-password").address("fixture-address")
                .phoneNumber("fixture-phone").accountBank("fixture-bank").accountNumber("fixture-account")
                .visible(visible).role(Role.builder().roleId(1L).roleType("USER").build()).build();
    }
    static List<Object> state(User user) {
        return Arrays.asList(user.getUserId(), user.getName(), user.getNickname(), user.getEmail(), user.getPassword(),
                user.getAddress(), user.getPhoneNumber(), user.getVisible(), user.getCreatedAt(), user.getUpdatedAt(),
                user.getAccountBank(), user.getAccountNumber(), user.getRole(), user.getRole().getRoleType());
    }
    private void authenticate(String role) {
        var details = new CustomUserDetails(User.builder().email(caller.getEmail())
                .role(Role.builder().roleType(role).build()).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
    private void denied(DropOutRequest request) {
        List<Object> beforeCaller = state(caller), beforeTarget = state(target);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, () -> service.dropOut(request)).getStatusCode());
        assertEquals(beforeCaller, state(caller)); assertEquals(beforeTarget, state(target));
        verifyNoInteractions(users); verify(caller, never()).changeVisible(); verify(target, never()).changeVisible();
    }
    static Stream<Arguments> ownMatrix() {
        return Stream.of("USER", "UNCONFIRMED_USER", "ADMIN", "FUTURE_ROLE").flatMap(role ->
                Stream.of(true, false).map(visible -> Arguments.of(role, visible)));
    }
    @ParameterizedTest @MethodSource("ownMatrix")
    void selfOnlyChangesVisibleWithEveryExistingRole(String role, boolean visible) {
        authenticate(role); caller.setVisible(visible); List<Object> before = new ArrayList<>(state(caller));
        assertTrue(service.dropOut(new DropOutRequest(7001L)).isDropOut());
        before.set(7, !visible); assertEquals(before, state(caller)); assertTrue(target.getVisible());
        var order = inOrder(auth, users, caller); order.verify(auth).readAuthUser();
        order.verify(users).findById(7001L); order.verify(caller).changeVisible();
        verifyNoMoreInteractions(auth, users); verify(target, never()).changeVisible();
    }
    static Stream<Arguments> others() {
        return Stream.of("USER", "UNCONFIRMED_USER", "ADMIN").flatMap(role -> Stream.of(7002L, 9999L)
                .flatMap(id -> Stream.of(true, false).map(visible -> Arguments.of(role, id, visible))));
    }
    @ParameterizedTest @MethodSource("others")
    void otherIncludingMissingIsForbiddenBeforeTargetLookup(String role, long id, boolean targetVisible) {
        authenticate(role); target.setVisible(targetVisible); denied(new DropOutRequest(id)); verify(auth).readAuthUser();
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "unauthenticated", "anonymous", "string", "null-principal", "null-user", "null-email", "blank-email"})
    void invalidPrincipalNeverResolvesDatabaseCaller(String kind) {
        Authentication authentication = switch (kind) {
            case "missing" -> null;
            case "unauthenticated" -> new UsernamePasswordAuthenticationToken(new CustomUserDetails(caller), null);
            case "anonymous" -> new AnonymousAuthenticationToken("fixture", new CustomUserDetails(caller), List.of(new SimpleGrantedAuthority("ROLE_USER")));
            case "string" -> new UsernamePasswordAuthenticationToken("fixture", null, List.of());
            case "null-principal" -> new UsernamePasswordAuthenticationToken(null, null, List.of());
            case "null-user" -> new UsernamePasswordAuthenticationToken(new CustomUserDetails(null), null, List.of());
            case "null-email" -> new UsernamePasswordAuthenticationToken(new CustomUserDetails(User.builder().build()), null, List.of());
            case "blank-email" -> new UsernamePasswordAuthenticationToken(new CustomUserDetails(User.builder().email(" \t").build()), null, List.of());
            default -> throw new IllegalArgumentException(kind);
        };
        SecurityContextHolder.getContext().setAuthentication(authentication); denied(new DropOutRequest(7001L)); verifyNoInteractions(auth);
    }
    @ParameterizedTest @ValueSource(strings = {"missing-caller", "null-caller-id", "null-request-id", "both-null", "null-request"})
    void invalidIdentityIsForbidden(String kind) {
        if (kind.equals("missing-caller")) when(auth.readAuthUser()).thenReturn(null);
        if (kind.equals("null-caller-id") || kind.equals("both-null")) caller.setUserId(null);
        denied(kind.equals("null-request") ? null : new DropOutRequest(
                kind.equals("null-request-id") || kind.equals("both-null") ? null : 7001L));
    }
    @Test @SuppressWarnings({"removal", "deprecation"})
    void equalLongValuesFromDifferentInstancesAllowSelf() {
        caller.setUserId(new Long(7001L)); Long requested = new Long(7001L);
        assertNotSame(caller.getUserId(), requested); assertTrue(service.dropOut(new DropOutRequest(requested)).isDropOut());
    }
    @Test void forgedPrincipalIdDoesNotReplaceDatabaseIdentity() {
        var principal = new CustomUserDetails(fixture(7002L, "caller", true));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        denied(new DropOutRequest(7002L));
    }
    @Test void repeatedSelfRequestsKeepExistingToggle() {
        assertTrue(service.dropOut(new DropOutRequest(7001L)).isDropOut()); assertFalse(caller.getVisible());
        assertTrue(service.dropOut(new DropOutRequest(7001L)).isDropOut()); assertTrue(caller.getVisible());
        verify(caller, times(2)).changeVisible(); verify(users, never()).save(any());
    }
    @Test void databaseFailureIsNotHiddenAsAuthorizationRefusal() {
        var failure = new IllegalStateException("synthetic database failure"); when(auth.readAuthUser()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.dropOut(new DropOutRequest(7001L))));
        verifyNoInteractions(users); verify(caller, never()).changeVisible();
    }
    @Test void missingOwnTargetKeepsExistingFailure() {
        when(users.findById(7001L)).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.dropOut(new DropOutRequest(7001L)));
        verify(caller, never()).changeVisible();
    }
    @Test void nullableVisibleKeepsExistingUnboxingFailure() {
        caller.setVisible(null); assertThrows(NullPointerException.class, () -> service.dropOut(new DropOutRequest(7001L)));
        assertNull(caller.getVisible()); verify(users, never()).save(any());
    }
}
