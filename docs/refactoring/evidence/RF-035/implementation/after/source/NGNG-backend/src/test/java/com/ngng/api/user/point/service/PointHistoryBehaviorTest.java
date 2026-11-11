package com.ngng.api.user.point.service;

import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.user.point.payment.PaymentFixture;


import com.ngng.api.user.point.dto.CreateAddPointRequestDTO;
import com.ngng.api.user.point.dto.PaymentPointRequestDto;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Offline behavior tests; no Spring application or database is started. */
class PointHistoryBehaviorTest {
    @org.junit.jupiter.api.AfterEach void clearAuthentication() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    private final PointHistoryRepository histories = mock(PointHistoryRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final TransactionDetailsRepository details = mock(TransactionDetailsRepository.class);
    private final User user = User.builder().userId(7L).name("Alice").build();
    private PointHistoryService service;

    @BeforeEach
    void setUp() {
        service = new PointHistoryService(histories, auth, users, products, details);
        when(histories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void balance(long cost) {
        PaymentFixture.authenticate();
        PaymentFixture.allowPayment(products, details, 7L, 200L);
        when(auth.readAuthUser()).thenReturn(user);
        when(users.lockUserIdForPointUpdate(7L)).thenReturn(Optional.of(7L));
        when(histories.findLatestCostForUpdate(7L)).thenReturn(Optional.of(cost));
    }

    @Test
    void chargePreservesHistoryFields() {
        balance(1000L);
        PointHistory result = service.updateCost(new CreateAddPointRequestDTO(200L, "충전"));
        assertThat(result.getBeforeCost()).isEqualTo(1000L);
        assertThat(result.getAddCost()).isEqualTo(200L);
        assertThat(result.getCost()).isEqualTo(1200L);
        assertThat(result.getType()).isEqualTo("충전");
        assertThat(result.getTypeDetail()).isEqualTo("Alice 유저가 200 금액 만큼 충전 하셨습니다.");
        assertThat(result.getUser()).isSameAs(user);
        verify(histories).save(result);
    }

    @Test
    void paymentPreservesPositiveAddCostAndDescription() {
        balance(1000L);
        PointHistory result = service.payment(new PaymentPointRequestDto(200L, 42L));
        assertThat(result.getBeforeCost()).isEqualTo(1000L);
        assertThat(result.getAddCost()).isEqualTo(200L);
        assertThat(result.getCost()).isEqualTo(800L);
        assertThat(result.getType()).isEqualTo("결제");
        assertThat(result.getTypeDetail()).isEqualTo("Alice 유저가 200 금액 만큼 42상품을 구매 하셨습니다.");
        assertThat(result.getUser()).isSameAs(user);
        verify(histories).save(result);
    }

    @Test
    void signupStillCreatesZeroBalanceHistory() {
        PointHistory result = service.createInitByUser(user);
        assertThat(result.getBeforeCost()).isZero();
        assertThat(result.getAddCost()).isZero();
        assertThat(result.getCost()).isZero();
        assertThat(result.getType()).isEqualTo("기본 생성");
        assertThat(result.getTypeDetail()).isEqualTo("Alice 유저가 생성 되면서 기본으로 생성된 포인트");
        assertThat(result.getUser()).isSameAs(user);
        verifyNoInteractions(auth, users);
    }

    @Test
    void checkDoesNotAppendHistory() {
        when(auth.readAuthUser()).thenReturn(user);
        when(histories.findLastByUserId(7L)).thenReturn(Optional.of(
                PointHistory.builder().user(user).cost(1000L).build()));
        assertThat(service.isPaymnet(200L)).isTrue();
        verify(histories, never()).save(any());
        verifyNoInteractions(users);
    }
}
