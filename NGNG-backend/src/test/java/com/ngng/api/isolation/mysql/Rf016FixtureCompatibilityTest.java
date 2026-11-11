package com.ngng.api.isolation.mysql;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.user.point.dto.CreateAddPointRequestDTO;
import com.ngng.api.user.point.dto.PaymentPointRequestDto;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.payment.PaymentVerificationGate;
import com.ngng.api.user.point.repository.PaymentSettlementRepository;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static com.ngng.api.isolation.Rf016UnusedPaymentCollaborator.unused;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Bean-method wiring only: all persistence/observation arguments are mocks; no fixture constructor. */
class Rf016FixtureCompatibilityTest {
    final UserRepository users = mock(UserRepository.class);
    final PointHistoryRepository histories = mock(PointHistoryRepository.class);
    final AuthService auth = mock(AuthService.class);
    final EntityManagerFactory factory = mock(EntityManagerFactory.class);
    final Rf016MySqlFixture fixture = mock(Rf016MySqlFixture.class);
    final PointHistoryService service = new Rf016MySqlFixture.JpaConfiguration()
            .pointHistoryService(users, histories, auth, factory, fixture);
    final User user = User.builder().userId(16001L).email("rf016@example.invalid").name("RF016").build();

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void chargingRetainsRepositoryDelegationAndObservationWithoutPaymentWork() {
        when(auth.readAuthUser()).thenReturn(user);
        when(users.lockUserIdForPointUpdate(16001L)).thenReturn(Optional.of(16001L));
        when(histories.findLatestCostForUpdate(16001L)).thenReturn(Optional.of(1000L));
        when(histories.save(any())).thenAnswer(invocation -> {
            PointHistory saved = invocation.getArgument(0); saved.setId(16002L); return saved;
        });
        when(fixture.observe(any(), same(factory))).thenAnswer(invocation -> invocation.getArgument(0));

        PointHistory saved = service.updateCost(new CreateAddPointRequestDTO(200L, "충전"));

        assertEquals(16002L, saved.getId());
        assertEquals(1000L, saved.getBeforeCost());
        assertEquals(200L, saved.getAddCost());
        assertEquals(1200L, saved.getCost());
        assertSame(user, saved.getUser());
        verify(histories).save(same(saved));
        verify(fixture).observe(same(saved), same(factory));
        verifyNoMoreInteractions(fixture);
        verifyNoInteractions(factory);
    }

    @Test void accidentalPaymentFailsBeforeAnyPointRepositoryWork() {
        when(auth.readAuthUser()).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user), null, List.of()));

        AssertionError failure = assertThrows(AssertionError.class,
                () -> service.payment(new PaymentPointRequestDto(200L, 42L)));

        assertTrue(failure.getMessage().contains("ProductRepository.lockTransactionProduct"));
        verifyNoInteractions(users, histories, factory, fixture);
    }

    @Test void eachPaymentOnlyCollaboratorRejectsBusinessCalls() {
        assertThrows(AssertionError.class, () -> unused(ProductRepository.class).lockTransactionProduct(42L));
        assertThrows(AssertionError.class, () -> unused(TransactionDetailsRepository.class).lockPaymentTransactions(42L));
        assertThrows(AssertionError.class, () -> unused(PaymentVerificationGate.class)
                .requireVerified(1L, 42L, 7L, 8L, 200L, 1L));
        assertThrows(AssertionError.class, () -> unused(PaymentSettlementRepository.class)
                .markPaid(1L, 42L, 7L, 8L, 200L));
    }
}
