package com.ngng.api.transaction.read;

import com.ngng.api.user.user.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionReadSecurityChainTest extends ReadChainHarness {
    @ParameterizedTest @CsvSource({
            "USER,1000", "USER,2000", "USER,3000", "USER,4000",
            "ADMIN,1000", "ADMIN,2000", "ADMIN,3000", "ADMIN,4000",
            "UNCONFIRMED_USER,1000", "UNCONFIRMED_USER,2000", "UNCONFIRMED_USER,3000", "UNCONFIRMED_USER,4000"})
    void roleAndRecordedPartyMatrixHasNoAdminOrCurrentProductOwnerOverride(String role, long id) throws Exception {
        caller(ReadFixture.id(id));
        var result = mvc.perform(get("/transaction/88").header("Authorization", "Bearer " + token("access", role, 3600)));
        if (id == 1000 || id == 2000) {
            result.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(88))
                    .andExpect(jsonPath("$.address").value("synthetic-private-address"))
                    .andExpect(jsonPath("$.seller.name").value("synthetic-user-1000"))
                    .andExpect(jsonPath("$.consumer.phoneNumber").value("000-0000-0000"));
        } else {
            result.andExpect(status().isForbidden()).andExpect(content().string(""));
            verify(transaction, never()).getAddress();
            verify(transaction, never()).getProduct();
        }
        result.andExpect(header().doesNotExist("Authorization"));
        verify(users).findUserByEmail(EMAIL);
        verify(details).findById(88L);
        verifyNoMoreInteractions(details);
    }
    @Test void exactSuccessDtoAndNullableAddressArePreserved() throws Exception {
        caller(ReadFixture.id(1000));
        transaction.setAddress(null);
        mvc.perform(get("/transaction/88").header("Authorization", "Bearer " + token("access", "USER", 3600)))
                .andExpect(status().isOk()).andExpect(content().json("""
                    {"id":88,"address":null,"product":{"id":7,"title":"synthetic-product","price":900,
                    "isEscrow":false,"purchaseAt":"synthetic-place","visible":true,"freeShipping":false,
                    "status":{"id":1,"name":"used"},"category":"category","tags":[],"images":[],
                    "thumbnail":{"id":1,"thumbnailURL":"synthetic-thumb"}},
                    "consumer":{"name":"synthetic-user-2000","phoneNumber":"000-0000-0000"},
                    "seller":{"name":"synthetic-user-1000","phoneNumber":"000-0000-0000"},
                    "status":{"id":1,"status":"requested"},"price":300}
                    """, true));
    }
    @ParameterizedTest @ValueSource(strings = {"seller", "consumer", "sellerId", "consumerId"})
    void incompletePartyAlwaysReturns403WithoutMapping(String missing) throws Exception {
        caller(ReadFixture.id(missing.startsWith("seller") ? 2000 : 1000));
        switch (missing) {
            case "seller" -> transaction.setSeller(null);
            case "consumer" -> transaction.setConsumer(null);
            case "sellerId" -> transaction.getSeller().setUserId(null);
            case "consumerId" -> transaction.getConsumer().setUserId(null);
        }
        mvc.perform(get("/transaction/88").header("Authorization", "Bearer " + token("access", "USER", 3600)))
                .andExpect(status().isForbidden()).andExpect(content().string(""));
        verify(transaction, never()).getAddress();
        verify(transaction, never()).getProduct();
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "malformed", "tampered", "expired"})
    void invalidCredentialsNeverReachUserOrTransactionRepositoriesOrIssueToken(String credentials) throws Exception {
        var request = get("/transaction/88");
        switch (credentials) {
            case "malformed" -> request.header("Authorization", "Bearer broken");
            case "tampered" -> {
                String value = token("access", "USER", 3600);
                int signature = value.lastIndexOf('.') + 1;
                value = value.substring(0, signature) + (value.charAt(signature) == 'a' ? 'b' : 'a') + value.substring(signature + 1);
                request.header("Authorization", "Bearer " + value);
            }
            case "expired" -> request.header("Authorization", "Bearer " + token("access", "USER", -3600));
        }
        mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization"));
        verifyNoInteractions(details, users);
    }
    @ParameterizedTest @ValueSource(strings = {"malformed", "wrongType", "tampered"})
    void invalidAccessWithValidRefreshDoesNotFallbackOrIssueToken(String credentials) throws Exception {
        String access = credentials.equals("malformed") ? "broken" : token("refresh", "USER", 3600);
        if (credentials.equals("tampered")) {
            String value = token("access", "USER", 3600);
            int signature = value.lastIndexOf('.') + 1;
            access = value.substring(0, signature) + (value.charAt(signature) == 'a' ? 'b' : 'a') + value.substring(signature + 1);
        }
        mvc.perform(get("/transaction/88").header("Authorization", "Bearer " + access)
                        .cookie(new Cookie("refreshToken", token("refresh", "USER", 3600))))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization"));
        verifyNoInteractions(details, users);
    }
    @ParameterizedTest @CsvSource({"missing,1000", "expired,2000", "missing,4000", "expired,4000"})
    void legitimateRefreshFallbackPreservesAuthorizationAndOnlyIssuesExistingAccessHeader(String access, long id) throws Exception {
        caller(ReadFixture.id(id));
        var request = get("/transaction/88").cookie(new Cookie("refreshToken", token("refresh", "USER", 3600)));
        if (access.equals("expired")) request.header("Authorization", "Bearer " + token("access", "USER", -3600));
        var result = mvc.perform(request).andExpect(status().is(id == 4000 ? 403 : 200))
                .andExpect(header().string("Authorization", org.hamcrest.Matchers.startsWith("Bearer "))).andReturn();
        String issued = result.getResponse().getHeader("Authorization");
        var verifier = context.getBean(com.ngng.api.global.security.jwt.util.JwtTokenVerifier.class);
        assertEquals(EMAIL, verifier.getEmail(issued));
        assertEquals("access", verifier.getType(issued));
        if (id == 4000) assertEquals("", result.getResponse().getContentAsString());
        verify(users).findUserByEmail(EMAIL);
    }
    @ParameterizedTest @ValueSource(strings = {"absent", "nullId"})
    void validJwtWithUnresolvedDbUserIs401AndDoesNotReadTransaction(String condition) throws Exception {
        when(users.findUserByEmail(EMAIL)).thenReturn(condition.equals("absent") ? Optional.empty() : Optional.of(User.builder().build()));
        mvc.perform(get("/transaction/88").header("Authorization", "Bearer " + token("access", "USER", 3600)))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Authorization"));
        verifyNoInteractions(details);
    }
    @Test void authenticatedMissingTransactionKeepsExactNullDto200() throws Exception {
        caller(ReadFixture.id(4000));
        mvc.perform(get("/transaction/99").header("Authorization", "Bearer " + token("access", "USER", 3600)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":null,\"address\":null,\"product\":null,\"consumer\":null,\"seller\":null,\"status\":null,\"price\":null}", true));
        verify(users).findUserByEmail(EMAIL);
        verify(details).findById(99L);
    }
    @Test void invalidPathKeepsBinding400WithoutServiceRepositoryAccess() throws Exception {
        mvc.perform(get("/transaction/not-a-number").header("Authorization", "Bearer " + token("access", "USER", 3600)))
                .andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Authorization"));
        verifyNoInteractions(users, details);
    }
    @Test void dbFailurePropagatesThroughTokenFilterInsteadOfBecoming401Or403() {
        var failure = new DataAccessResourceFailureException("synthetic-database-unavailable");
        when(users.findUserByEmail(EMAIL)).thenThrow(failure);
        Exception thrown = assertThrows(Exception.class, () -> mvc.perform(get("/transaction/88")
                .header("Authorization", "Bearer " + token("access", "USER", 3600))));
        assertSame(failure, thrown.getCause());
        verifyNoInteractions(details);
    }
}
