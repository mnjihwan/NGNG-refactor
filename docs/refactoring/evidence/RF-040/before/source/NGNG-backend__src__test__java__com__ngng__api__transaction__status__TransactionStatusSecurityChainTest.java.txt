package com.ngng.api.transaction.status;

import com.ngng.api.transaction.entity.TransactionStatus;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.product.product.repository.ProductRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real SecurityConfig/filter/JWT/AuthService/controller/service, mocked repositories. */
class TransactionStatusSecurityChainTest extends StatusChainHarness {
    TransactionDetailsRepository.StatusTransaction current;
    @BeforeEach void currentState() { state(2L); }
    void state(Long from) {
        transaction = spy(Rf038Fixture.graph(from));
        when(details.findById(88L)).thenReturn(Optional.of(transaction));
        current = mock(TransactionDetailsRepository.StatusTransaction.class);
        when(current.getTransactionId()).thenReturn(88L); when(current.getProductId()).thenReturn(7L);
        when(current.getConsumerId()).thenReturn(2000L); when(current.getSellerId()).thenReturn(1000L);
        when(current.getStatusId()).thenReturn(from); when(current.getPrice()).thenReturn(300L);
        when(current.getAddress()).thenReturn("synthetic-address");
        when(details.findStatusProductId(88L)).thenReturn(Optional.of(7L));
        when(details.lockStatusTransaction(88L)).thenReturn(Optional.of(current));
        var product = mock(ProductRepository.TransactionProduct.class); when(product.getProductId()).thenReturn(7L);
        when(products.lockTransactionProduct(7L)).thenReturn(Optional.of(product));
        when(statuses.findById(any())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            return id == null || id == 99L ? Optional.empty() : Optional.of(new TransactionStatus(id, "synthetic-status-" + id));
        });
    }
    MockHttpServletRequestBuilder update(Long target) {
        return put("/transaction/88").contentType("application/json").content("{\"statusId\":" + target + "}");
    }
    static Stream<Arguments> rolesAndTransitions() {
        long[][] cases = {{1,2}, {2,3}, {3,4}, {4,5}, {5,1}, {6,1}, {7,5}, {8,6}, {8,7}, {6,7}, {7,6}, {2,2}};
        return Stream.of("USER","ADMIN","UNCONFIRMED_USER").flatMap(role -> Arrays.stream(cases)
                .flatMap(pair -> Stream.of(1000L,2000L,3000L,4000L).map(actor -> Arguments.of(role,pair[0],pair[1],actor))));
    }
    @ParameterizedTest @MethodSource("rolesAndTransitions")
    void actualChainEnforcesRecordedPartiesAndNormalExceptionalMatrix(String role, Long from, Long to, Long actor) throws Exception {
        state(from); caller(actor);
        boolean party = actor == 1000 || actor == 2000;
        boolean same = from.equals(to);
        boolean exceptional = to == 6 || to == 7;
        boolean sellerAction = (from == 2 && to == 3) || (from == 3 && to == 4);
        boolean buyerAction = from == 4 && to == 5;
        int expected = !party ? 401 : same || exceptional ? 200 : sellerAction ? (actor == 1000 ? 200 : 403)
                : buyerAction ? (actor == 2000 ? 200 : 403) : 409;
        var result = mvc.perform(update(to).header("Authorization", "Bearer " + token("access",role,60)))
                .andExpect(status().is(expected));
        if (expected == 200) {
            result.andExpect(jsonPath("$.id").value(88)).andExpect(jsonPath("$.status.id").value(to))
                    .andExpect(jsonPath("$.address").value("synthetic-address"))
                    .andExpect(jsonPath("$.price").value(300)).andExpect(jsonPath("$.product.id").value(7))
                    .andExpect(jsonPath("$.seller.name").value("synthetic-1000"))
                    .andExpect(jsonPath("$.status.status").value("synthetic-status-" + to))
                    .andExpect(jsonPath("$.product.title").value("synthetic-product"))
                    .andExpect(jsonPath("$.consumer.phoneNumber").value("000-0000-0000"));
        }
        verify(transaction,times(expected == 200 && !same ? 1 : 0)).setStatus(any());
        verify(details, never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "malformed", "wrongType", "expired"})
    void invalidTokenNeverReachesServiceRepositories(String scenario) throws Exception {
        var request = update(3L);
        switch(scenario) {
            case "malformed" -> request.header("Authorization","Bearer broken");
            case "wrongType" -> request.header("Authorization","Bearer " + token("refresh","USER",60));
            case "expired" -> request.header("Authorization","Bearer " + token("access","USER",-60));
        }
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        verifyNoInteractions(details, products, statuses, users);
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "expired"})
    void refreshFallbackAndHeaderRetainedForAuthorizedSeller(String scenario) throws Exception {
        caller(1000L);
        var request = update(3L).cookie(new jakarta.servlet.http.Cookie("refreshToken",token("refresh","USER",60)));
        if (scenario.equals("expired")) request.header("Authorization","Bearer " + token("access","USER",-60));
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.status.id").value(3))
                .andExpect(header().string("Authorization",org.hamcrest.Matchers.startsWith("Bearer ")));
    }
    @ParameterizedTest @ValueSource(strings = {"{", "{\"statusId\":\"invalid\"}"})
    void malformedBodyRetains400BeforeService(String body) throws Exception {
        mvc.perform(put("/transaction/88").header("Authorization","Bearer " + token("access","USER",60))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(details, products, statuses, users);
    }
    @Test void malformedPathRetains400BeforeService() throws Exception {
        mvc.perform(put("/transaction/invalid").header("Authorization","Bearer " + token("access","USER",60))
                .contentType("application/json").content("{\"statusId\":3}")).andExpect(status().isBadRequest());
        verifyNoInteractions(details, products, statuses, users);
    }
    @ParameterizedTest @ValueSource(strings = {"seller", "consumer", "status"})
    void incompletePersistedPartiesOrStatusAre409WithoutWrites(String field) throws Exception {
        caller(2000L);
        switch(field) {
            case "seller" -> when(current.getSellerId()).thenReturn(null);
            case "consumer" -> when(current.getConsumerId()).thenReturn(null);
            case "status" -> when(current.getStatusId()).thenReturn(null);
        }
        mvc.perform(update(6L).header("Authorization","Bearer " + token("access","USER",60))).andExpect(status().isConflict());
        verify(transaction,never()).setStatus(any()); verify(details,never()).findById(any());
    }
    @Test void staleManagedStateIs409AndNeverLeaksDto() throws Exception {
        caller(1000L); transaction.setStatus(new TransactionStatus(1L)); clearInvocations(transaction);
        mvc.perform(update(3L).header("Authorization","Bearer " + token("access","USER",60)))
                .andExpect(status().isConflict()).andExpect(content().string(""));
        verify(transaction,never()).setStatus(any());
    }
    @Test void legacySamePaidPutIsReadOnlyAndNotPaymentProof() throws Exception {
        caller(1000L);
        var before = transaction.getStatus(); var updatedAt = transaction.getUpdatedAt();
        mvc.perform(update(2L).header("Authorization","Bearer " + token("access","USER",60)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status.id").value(2));
        assertSame(before,transaction.getStatus()); assertEquals(updatedAt,transaction.getUpdatedAt());
        verify(transaction,never()).setStatus(any()); verify(details,never()).save(any());
    }
    @Test void nullDatabaseUserNeverFallsBackToTokenPrincipal() throws Exception {
        when(users.findUserByEmail(EMAIL)).thenReturn(Optional.empty());
        mvc.perform(update(3L).header("Authorization","Bearer " + token("access","ADMIN",60)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(details, products, statuses);
    }
    @Test void repositoryFailureIsPropagatedRatherThanConvertedToToken401() {
        caller(1000L); var failure = new IllegalStateException("synthetic locking failure");
        when(details.lockStatusTransaction(88L)).thenThrow(failure);
        var error = assertThrows(jakarta.servlet.ServletException.class,
                () -> mvc.perform(update(3L).header("Authorization","Bearer " + token("access","USER",60))));
        assertSame(failure,error.getCause()); verify(transaction,never()).setStatus(any());
    }
}
