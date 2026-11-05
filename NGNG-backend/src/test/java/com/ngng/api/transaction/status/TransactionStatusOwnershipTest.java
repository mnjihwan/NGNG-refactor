package com.ngng.api.transaction.status;

import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.controller.TransactionController;
import com.ngng.api.transaction.dto.UpdateTransactionDetailsRequestDTO;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.entity.TransactionStatus;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.transaction.repository.TransactionStatusRepository;
import com.ngng.api.transaction.service.TransactionDetailsService;
import com.ngng.api.transaction.service.TransactionRequestService;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.NoSuchElementException;
import java.util.Optional;
import static com.ngng.api.transaction.status.StatusFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Direct service and standalone MVC: no Spring Boot, persistence, or security filters. */
class TransactionStatusOwnershipTest {
    final TransactionDetailsRepository details = mock(TransactionDetailsRepository.class);
    final TransactionStatusRepository statuses = mock(TransactionStatusRepository.class);
    final AuthService auth = mock(AuthService.class);
    final TransactionDetailsService service = new TransactionDetailsService(details, statuses,
            mock(ProductRepository.class), auth);
    final TransactionStatus next = new TransactionStatus(4L, "completed");
    final UpdateTransactionDetailsRequestDTO request = new UpdateTransactionDetailsRequestDTO(4L);
    TransactionDetails transaction;

    void fixture(Long owner, Long buyer, Long caller) {
        transaction = graph(owner, buyer);
        when(details.findById(88L)).thenReturn(Optional.of(transaction));
        when(auth.readAuthUser()).thenReturn(user(caller));
        when(statuses.findById(4L)).thenReturn(Optional.of(next));
    }
    void allowed() {
        var dto = service.updateTransactionStatus(88L, request);
        assertSame(next, transaction.getStatus());
        assertSame(next, dto.getStatus());
        assertEquals(88L, dto.getId());
        verify(statuses).findById(4L);
        verify(details, never()).save(any());
        var order = inOrder(details, auth, statuses);
        order.verify(details).findById(88L);
        order.verify(auth).readAuthUser();
        order.verify(statuses).findById(4L);
    }
    void denied() {
        TransactionStatus initial = transaction.getStatus();
        var error = assertThrows(ResponseStatusException.class, () -> service.updateTransactionStatus(88L, request));
        assertEquals(401, error.getStatusCode().value());
        assertTrue(error.getReason().contains("소유권이 없는 유저 입니다."));
        assertSame(initial, transaction.getStatus());
        verifyNoInteractions(statuses);
        verify(details, never()).save(any());
    }

    @ParameterizedTest @CsvSource({"true,7", "false,7", "true,1000", "false,1000"})
    void sameReferenceOwnerAndBuyerAreAllowed(boolean owner, long id) {
        Long shared = distinctId(id);
        fixture(owner ? shared : distinctId(2000), owner ? distinctId(2000) : shared, shared);
        allowed();
    }

    @ParameterizedTest @CsvSource({"true,7", "false,7", "true,1000", "false,1000"})
    void separateEqualOwnerAndBuyerAreAllowed(boolean owner, long id) {
        Long stored = distinctId(id), caller = distinctId(id);
        separateEqual(stored, caller);
        fixture(owner ? stored : distinctId(2000), owner ? distinctId(2000) : stored, caller);
        allowed();
    }

    @Test void cachedSmallIdStillWorks() {
        Long stored = Long.valueOf(7), caller = Long.valueOf(7);
        assertSame(stored, caller);
        fixture(stored, distinctId(2000), caller);
        allowed();
    }
    @Test void unrelatedUserIsDenied() { fixture(distinctId(1000), distinctId(2000), distinctId(4000)); denied(); }
    @Test void recordedSellerAloneIsDenied() { fixture(distinctId(1000), distinctId(2000), distinctId(3000)); denied(); }
    @Test void ownerBranchShortCircuitsConsumerIdRead() {
        Long id = distinctId(1000);
        Long caller = distinctId(1000);
        separateEqual(id, caller);
        fixture(id, distinctId(2000), caller);
        var buyer = spy(transaction.getConsumer());
        transaction.setConsumer(buyer);
        allowed();
        verify(buyer, never()).getUserId(); // DTO only reads name/phone
    }
    @Test void buyerIdIsReadOnlyAfterOwnerMismatch() {
        Long id = distinctId(2000);
        fixture(distinctId(1000), id, id);
        var owner = spy(transaction.getProduct().getUser());
        var buyer = spy(transaction.getConsumer());
        transaction.getProduct().setUser(owner); transaction.setConsumer(buyer);
        allowed();
        var order = inOrder(owner, buyer);
        order.verify(owner).getUserId(); order.verify(buyer).getUserId();
    }
    @ParameterizedTest @CsvSource({"true", "false"})
    void nullCallerAndNullOwnerOrBuyerRetainExistingAllowance(boolean ownerNull) {
        fixture(ownerNull ? null : distinctId(1000), ownerNull ? distinctId(2000) : null, null);
        allowed();
    }
    @Test void nullCallerAgainstNonNullOwnersIsDenied() { fixture(distinctId(1000), distinctId(2000), null); denied(); }
    @Test void nonNullCallerAgainstNullIdsIsDenied() { fixture(null, null, distinctId(1000)); denied(); }
    @Test void missingTransactionRetainsNoSuchElementBoundary() {
        assertThrows(NoSuchElementException.class, () -> service.updateTransactionStatus(404L, request));
        verifyNoInteractions(auth, statuses);
    }
    @Test void missingStatusDoesNotMutate() {
        Long id = distinctId(1000); fixture(id, distinctId(2000), id);
        when(statuses.findById(4L)).thenReturn(Optional.empty());
        TransactionStatus initial = transaction.getStatus();
        assertThrows(NoSuchElementException.class, () -> service.updateTransactionStatus(88L, request));
        assertSame(initial, transaction.getStatus());
    }
    @Test void standaloneMvcPreservesSuccessJson() throws Exception {
        Long id = distinctId(1000); fixture(id, distinctId(2000), id);
        mvc().perform(put("/transaction/88").contentType("application/json").content("{\"statusId\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(88))
                .andExpect(jsonPath("$.address").value("fixture-address"))
                .andExpect(jsonPath("$.price").value(300)).andExpect(jsonPath("$.status.id").value(4))
                .andExpect(jsonPath("$.status.status").value("completed"))
                .andExpect(jsonPath("$.product.id").value(7)).andExpect(jsonPath("$.product.title").value("fixture"))
                .andExpect(jsonPath("$.seller.name").value("user-3000"))
                .andExpect(jsonPath("$.consumer.phoneNumber").value("000"));
    }
    @Test void standaloneMvcPreservesOwnership401() throws Exception {
        fixture(distinctId(1000), distinctId(2000), distinctId(4000));
        mvc().perform(put("/transaction/88").contentType("application/json").content("{\"statusId\":4}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(statuses);
        assertEquals(1L, transaction.getStatus().getId());
    }
    @ParameterizedTest @ValueSource(strings = {"{", "{\"statusId\":\"invalid\"}"})
    void malformedBodyRetains400BeforeService(String body) throws Exception {
        mvc().perform(put("/transaction/88").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(details, auth, statuses);
    }
    @Test void malformedPathRetains400BeforeService() throws Exception {
        mvc().perform(put("/transaction/invalid").contentType("application/json").content("{\"statusId\":4}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(details, auth, statuses);
    }
    org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new TransactionController(service, mock(TransactionRequestService.class))).build();
    }
}
