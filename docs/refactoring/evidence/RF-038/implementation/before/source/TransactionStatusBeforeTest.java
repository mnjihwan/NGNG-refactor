package com.ngng.api.transaction.status;

import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.dto.UpdateTransactionDetailsRequestDTO;
import com.ngng.api.transaction.entity.*;
import com.ngng.api.transaction.repository.*;
import com.ngng.api.transaction.service.TransactionDetailsService;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Before-only characterization. Repositories are doubles; no physical database. */
class TransactionStatusBeforeTest {
    final TransactionDetailsRepository details = mock(TransactionDetailsRepository.class);
    final TransactionStatusRepository statuses = mock(TransactionStatusRepository.class);
    final ProductRepository products = mock(ProductRepository.class);
    final AuthService auth = mock(AuthService.class);
    final TransactionDetailsService service = new TransactionDetailsService(details, statuses, products, auth);
    @ParameterizedTest @CsvSource({"1,2", "1,5", "5,1", "6,5", "7,1", "8,6", "8,7", "2,2"})
    void buyerCanSetAnyExistingStatusIncludingUnpaidAndBackward(long before, long after) {
        var transaction = spy(Rf038Fixture.graph(before));
        when(details.findById(88L)).thenReturn(Optional.of(transaction));
        when(auth.readAuthUser()).thenReturn(Rf038Fixture.user(2000L));
        when(statuses.findById(after)).thenReturn(Optional.of(new TransactionStatus(after)));
        assertEquals(after, service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(after)).getStatus().getId());
        verify(transaction).setStatus(any());
        verifyNoInteractions(products);
    }
    @Test void recordedSellerWhoIsNotCurrentOwnerIsRejected() {
        var transaction = spy(Rf038Fixture.graph(2L));
        when(details.findById(88L)).thenReturn(Optional.of(transaction));
        when(auth.readAuthUser()).thenReturn(Rf038Fixture.user(1000L));
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(3L))).getStatusCode().value());
        verify(transaction, never()).setStatus(any());
    }
    @Test void currentOwnerCanMarkPaidWithoutPayment() {
        var transaction = spy(Rf038Fixture.graph(1L));
        when(details.findById(88L)).thenReturn(Optional.of(transaction));
        when(auth.readAuthUser()).thenReturn(Rf038Fixture.user(3000L));
        when(statuses.findById(2L)).thenReturn(Optional.of(new TransactionStatus(2L)));
        assertEquals(2L, service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(2L)).getStatus().getId());
        verify(transaction).setStatus(any());
    }
    @Test void nullCurrentStatusDoesNotPreventCancellation() {
        var transaction = spy(Rf038Fixture.graph(null));
        when(details.findById(88L)).thenReturn(Optional.of(transaction));
        when(auth.readAuthUser()).thenReturn(Rf038Fixture.user(2000L));
        when(statuses.findById(6L)).thenReturn(Optional.of(new TransactionStatus(6L)));
        assertEquals(6L, service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(6L)).getStatus().getId());
    }
    @Test void missingTransactionThrowsWithoutStatusLookup() {
        assertThrows(java.util.NoSuchElementException.class,
                () -> service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(6L)));
        verifyNoInteractions(statuses, auth, products);
    }
    @Test void missingOrNullTargetHasNoSuchElementContractAtServiceBoundary() {
        when(details.findById(88L)).thenReturn(Optional.of(Rf038Fixture.graph(1L)));
        when(auth.readAuthUser()).thenReturn(Rf038Fixture.user(2000L));
        assertThrows(java.util.NoSuchElementException.class,
                () -> service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(99L)));
        assertThrows(java.util.NoSuchElementException.class,
                () -> service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(null)));
    }
    @Test void nullDatabaseUserPreviouslyCausesNullPointer() {
        when(details.findById(88L)).thenReturn(Optional.of(Rf038Fixture.graph(1L)));
        assertThrows(NullPointerException.class,
                () -> service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(6L)));
    }
}
