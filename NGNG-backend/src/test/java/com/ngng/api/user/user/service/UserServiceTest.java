package com.ngng.api.user.user.service;

import com.ngng.api.product.product.service.ProductService;
import com.ngng.api.transaction.service.TransactionDetailsService;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.user.user.dto.UserReadResponseDTO;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository users;
    @Mock private PointHistoryService points;
    @Mock private TransactionDetailsService transactions;
    @Mock private ProductService products;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthService auth;
    @InjectMocks private UserService service;

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void findAllPreservesUserDetailsBalancesAndPageMetadata(int pageNumber) {
        Role firstRole = Role.builder().roleId(2L).roleType("USER").build();
        Role secondRole = Role.builder().roleId(3L).roleType("ADMIN").build();
        User first = user(7L, "Alice", "alice-nick", "010-1000", "alice@example.test", "Bank A", "111", "Address A", firstRole);
        User second = user(42L, "Bob", "bob-nick", "010-2000", "bob@example.test", "Bank B", "222", "Address B", secondRole);
        Pageable returnedPage = PageRequest.of(pageNumber, 5, Sort.by("userId").ascending());
        long total = pageNumber == 0 ? 2 : 12;
        when(users.findByVisible(eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), returnedPage, total));
        // Batch result order is deliberately different from the user Page order.
        when(points.readCostsByUserIds(List.of(7L, 42L))).thenReturn(List.of(
                PointHistory.builder().id(103L).user(second).cost(920L).build(),
                PointHistory.builder().id(109L).user(first).cost(150L).build()));

        Page<UserReadResponseDTO> result = service.findAll(pageNumber);

        assertThat(result.getContent()).hasSize(2);
        assertDto(result.getContent().get(0), 7L, "Alice", "alice-nick", "010-1000", "alice@example.test", "Bank A", "111", "Address A", 2L, "USER", 150L);
        assertDto(result.getContent().get(1), 42L, "Bob", "bob-nick", "010-2000", "bob@example.test", "Bank B", "222", "Address B", 3L, "ADMIN", 920L);
        assertThat(result.getNumber()).isEqualTo(pageNumber);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getTotalElements()).isEqualTo(total);
        assertThat(result.getTotalPages()).isEqualTo(pageNumber == 0 ? 1 : 3);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(users).findByVisible(eq(true), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(pageNumber);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "userId"));
        verify(points).readCostsByUserIds(List.of(7L, 42L));
        verify(points, never()).readCostByUser(any());
        assertNoWrites();
    }

    @Test
    void emptyPagePreservesMetadataAndDoesNotReadPoints() {
        Pageable pageable = PageRequest.of(2, 5, Sort.by("userId").ascending());
        when(users.findByVisible(eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 8));

        Page<UserReadResponseDTO> result = service.findAll(2);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getTotalElements()).isEqualTo(8);
        assertThat(result.getTotalPages()).isEqualTo(2);
        verifyNoInteractions(points);
        verify(users).findByVisible(eq(true), eq(pageable));
        assertNoRepositoryWrites();
        verifyNoInteractions(transactions, products, passwordEncoder, auth);
    }

    @Test
    void missingPointHistoryCurrentlyFailsDuringMapping() {
        User user = User.builder().userId(7L).build();
        when(users.findByVisible(eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 5), 1));
        when(points.readCostsByUserIds(List.of(7L))).thenReturn(List.of());

        // Characterizes a defect candidate, not an approved rule for users without history.
        assertThatThrownBy(() -> service.findAll(0)).isInstanceOf(NullPointerException.class);
        assertNoWrites();
    }

    @Test
    void fullPageMapsEveryUserByIdAndPreservesNullFieldsAndPointCost() {
        List<User> pageUsers = List.of(new User(7L), new User(42L), new User(90L), new User(101L), new User(300L));
        Pageable pageable = PageRequest.of(0, 5, Sort.by("userId").ascending());
        when(users.findByVisible(eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(pageUsers, pageable, 12));
        when(points.readCostsByUserIds(List.of(7L, 42L, 90L, 101L, 300L))).thenReturn(List.of(
                PointHistory.builder().id(220L).user(pageUsers.get(4)).cost(-15L).build(),
                PointHistory.builder().id(215L).user(pageUsers.get(2)).cost(null).build(),
                PointHistory.builder().id(218L).user(pageUsers.get(0)).cost(3L).build(),
                PointHistory.builder().id(211L).user(pageUsers.get(3)).cost(0L).build(),
                PointHistory.builder().id(219L).user(pageUsers.get(1)).cost(999L).build()));

        Page<UserReadResponseDTO> result = service.findAll(0);

        assertThat(result.getContent()).extracting(UserReadResponseDTO::getUserId)
                .containsExactly(7L, 42L, 90L, 101L, 300L);
        assertThat(result.getContent()).extracting(UserReadResponseDTO::getPoint)
                .containsExactly(3L, 999L, null, 0L, -15L);
        assertThat(result.getContent()).allSatisfy(dto -> {
            assertThat(dto.getName()).isNull();
            assertThat(dto.getNickName()).isNull();
            assertThat(dto.getPhoneNumber()).isNull();
            assertThat(dto.getEmail()).isNull();
            assertThat(dto.getAccountBank()).isNull();
            assertThat(dto.getAccountNumber()).isNull();
            assertThat(dto.getAddress()).isNull();
            assertThat(dto.getRole()).isNull();
        });
        assertThat(result.getPageable()).isEqualTo(pageable);
        assertThat(result.getTotalElements()).isEqualTo(12);
        assertThat(result.getTotalPages()).isEqualTo(3);
        verify(points, never()).readCostByUser(any());
        assertNoWrites();
    }

    @Test
    void missingHistoryInMixedPageStillFailsInsteadOfDroppingUserOrDefaultingCost() {
        User first = new User(7L);
        User second = new User(42L);
        when(users.findByVisible(eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 5), 2));
        when(points.readCostsByUserIds(List.of(7L, 42L)))
                .thenReturn(List.of(PointHistory.builder().user(first).cost(15L).build()));

        assertThatThrownBy(() -> service.findAll(0)).isInstanceOf(NullPointerException.class);

        assertNoWrites();
    }

    @Test
    void unpagedListStillUsesExistingSingleUserLookup() {
        User user = new User(7L);
        when(users.findAll()).thenReturn(List.of(user));
        when(points.readCostByUser(user)).thenReturn(PointHistory.builder().cost(15L).build());

        assertThat(service.findAll()).singleElement().satisfies(dto -> {
            assertThat(dto.getUserId()).isEqualTo(7L);
            assertThat(dto.getPoint()).isEqualTo(15L);
        });

        verify(points).readCostByUser(user);
        verify(points, never()).readCostsByUserIds(any());
        assertNoWrites();
    }

    @Test
    void singleUserDetailsStillUsesExistingSingleUserLookup() {
        User user = new User(7L);
        when(users.findById(7L)).thenReturn(java.util.Optional.of(user));
        when(points.readCostByUser(user)).thenReturn(PointHistory.builder().cost(15L).build());

        UserReadResponseDTO result = service.readUserById(7L);

        assertThat(result.getUserId()).isEqualTo(7L);
        assertThat(result.getPoint()).isEqualTo(15L);
        verify(points).readCostByUser(user);
        verify(points, never()).readCostsByUserIds(any());
        assertNoWrites();
    }

    @Test
    void negativePageFailsBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.findAll(-1)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(users, points, transactions, products, passwordEncoder, auth);
    }

    private void assertNoWrites() {
        assertNoRepositoryWrites();
        verify(points, never()).createInitByUser(any());
        verify(points, never()).updateCost(any());
        verify(points, never()).payment(any());
        verifyNoInteractions(transactions, products, passwordEncoder, auth);
    }

    private void assertNoRepositoryWrites() {
        verify(users, never()).save(any(User.class));
        verify(users, never()).delete(any(User.class));
        verify(users, never()).deleteById(anyLong());
        verify(users, never()).deleteAll();
    }

    private static User user(Long id, String name, String nickname, String phone, String email,
                             String bank, String account, String address, Role role) {
        return User.builder().userId(id).name(name).nickname(nickname).phoneNumber(phone).email(email)
                .accountBank(bank).accountNumber(account).address(address).role(role).visible(true).build();
    }

    private static void assertDto(UserReadResponseDTO actual, Long id, String name, String nickname,
                                  String phone, String email, String bank, String account, String address,
                                  Long roleId, String roleType, Long point) {
        assertThat(actual.getUserId()).isEqualTo(id);
        assertThat(actual.getName()).isEqualTo(name);
        assertThat(actual.getNickName()).isEqualTo(nickname);
        assertThat(actual.getPhoneNumber()).isEqualTo(phone);
        assertThat(actual.getEmail()).isEqualTo(email);
        assertThat(actual.getAccountBank()).isEqualTo(bank);
        assertThat(actual.getAccountNumber()).isEqualTo(account);
        assertThat(actual.getAddress()).isEqualTo(address);
        assertThat(actual.getRole().getRoleId()).isEqualTo(roleId);
        assertThat(actual.getRole().getRoleType()).isEqualTo(roleType);
        assertThat(actual.getPoint()).isEqualTo(point);
    }
}
