package com.ngng.api.transaction.status;

import com.ngng.api.transaction.entity.TransactionStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual filter/AuthService/controller/service; repository doubles and synthetic JWT. */
class TransactionStatusBeforeChainTest extends StatusChainHarness {
    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN", "UNCONFIRMED_USER"})
    void buyerCanBypassPaymentAndSkipToConfirmation(String role) throws Exception {
        caller(2000L);
        when(statuses.findById(5L)).thenReturn(Optional.of(new TransactionStatus(5L)));
        mvc.perform(put("/transaction/88").header("Authorization", "Bearer " + token("access", role, 60))
                .contentType("application/json").content("{\"statusId\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status.id").value(5));
        verify(transaction).setStatus(any());
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN", "UNCONFIRMED_USER"})
    void recordedSellerIsDeniedIfCurrentOwnerDiffers(String role) throws Exception {
        caller(1000L);
        mvc.perform(put("/transaction/88").header("Authorization", "Bearer " + token("access", role, 60))
                .contentType("application/json").content("{\"statusId\":3}"))
                .andExpect(status().isUnauthorized());
        verify(transaction, never()).setStatus(any());
    }
}
