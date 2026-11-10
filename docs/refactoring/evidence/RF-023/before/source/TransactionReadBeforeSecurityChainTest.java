package com.ngng.api.transaction.read;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Before-only characterization: archived with the before source after reproduction. */
class TransactionReadBeforeSecurityChainTest extends ReadChainHarness {
    @ParameterizedTest @CsvSource({"USER,1000", "USER,2000", "USER,4000", "ADMIN,4000"})
    void validAuthenticationDisclosesTheSamePersonalFieldsToPartiesAndThirdParties(String role, long id) throws Exception {
        caller(ReadFixture.id(id));
        mvc.perform(get("/transaction/88").header("Authorization", "Bearer " + token("access", role, 3600)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(88))
                .andExpect(jsonPath("$.address").value("synthetic-private-address"))
                .andExpect(jsonPath("$.seller.name").value("synthetic-user-1000"))
                .andExpect(jsonPath("$.consumer.phoneNumber").value("000-0000-0000"))
                .andExpect(header().doesNotExist("Authorization"));
        verifyNoInteractions(users); // existing read never resolves the authenticated DB user
        verify(details).findById(88L);
    }
    @Test void missingTransactionRemainsEmptyDto200() throws Exception {
        mvc.perform(get("/transaction/99").header("Authorization", "Bearer " + token("access", "USER", 3600)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":null,\"address\":null,\"product\":null,\"consumer\":null,\"seller\":null,\"status\":null,\"price\":null}", true));
    }
    @Test void noCredentialsStopsBeforeRepository() throws Exception {
        mvc.perform(get("/transaction/88")).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        verifyNoInteractions(details, users);
    }
}
