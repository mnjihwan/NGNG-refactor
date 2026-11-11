package com.ngng.api.transaction.read;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.entity.TransactionStatus;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.transaction.repository.TransactionStatusRepository;
import com.ngng.api.transaction.service.TransactionDetailsService;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionReadOwnershipTest {
    TransactionDetailsRepository details;
    TransactionStatusRepository statuses;
    ProductRepository products;
    UserRepository users;
    TransactionDetailsService service;
    TransactionDetails transaction;
    @BeforeEach void setup() {
        details = mock(TransactionDetailsRepository.class);
        statuses = mock(TransactionStatusRepository.class);
        products = mock(ProductRepository.class);
        users = mock(UserRepository.class);
        service = new TransactionDetailsService(details, statuses, products, new AuthService(users));
        transaction = spy(ReadFixture.graph());
        transaction.setSeller(spy(transaction.getSeller()));
        transaction.setConsumer(spy(transaction.getConsumer()));
        clearInvocations(transaction);
        when(details.findById(88L)).thenReturn(Optional.of(transaction));
        authenticate(ReadFixture.id(1000));
    }
    @AfterEach void finish() {
        SecurityContextHolder.clearContext();
        verify(details, never()).save(any());
        verify(details, never()).delete(any());
        verify(details, atMostOnce()).findById(anyLong());
        verify(details, atMostOnce()).findALlByConsumerId(anyLong());
        verify(details, atMostOnce()).findAllBySellerId(anyLong());
        verifyNoMoreInteractions(details);
        verify(transaction, never()).setStatus(any());
        verifyNoInteractions(statuses, products);
    }
    void authenticate(Long id) {
        var principal = new CustomUserDetails(User.builder().email(ReadChainHarness.EMAIL).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        when(users.findUserByEmail(ReadChainHarness.EMAIL)).thenReturn(Optional.of(ReadFixture.user(id)));
    }
    void deniedWithoutMapping() {
        var error = assertThrows(ResponseStatusException.class, () -> service.readTransactionDetailsById(88L));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(transaction, never()).getAddress();
        verify(transaction, never()).getProduct();
        verify(transaction, never()).getPrice();
        if (transaction.getSeller() != null) {
            verify(transaction.getSeller(), never()).getName();
            verify(transaction.getSeller(), never()).getPhoneNumber();
        }
        if (transaction.getConsumer() != null) {
            verify(transaction.getConsumer(), never()).getName();
            verify(transaction.getConsumer(), never()).getPhoneNumber();
        }
    }
    @ParameterizedTest @CsvSource({"1,true", "2,false", "1000,true", "2000,false", "9223372036854775806,true"})
    void partyIdsUseNonNullValuesEvenForDistinctLongReferences(long id, boolean seller) {
        User party = seller ? transaction.getSeller() : transaction.getConsumer();
        party.setUserId(ReadFixture.id(id));
        Long caller = ReadFixture.id(id);
        assertEquals(party.getUserId(), caller);
        assertNotSame(party.getUserId(), caller);
        authenticate(caller);
        assertEquals(88L, service.readTransactionDetailsById(88L).getId());
    }
    @ParameterizedTest @ValueSource(longs = {3000, 4000})
    void currentProductOwnerAndUnrelatedCallerAreDenied(long id) {
        authenticate(ReadFixture.id(id));
        deniedWithoutMapping();
    }
    @Test void sameRecordedPartyOnBothSidesCanReadWithCompleteIds() {
        transaction.getConsumer().setUserId(ReadFixture.id(1000));
        assertEquals(88L, service.readTransactionDetailsById(88L).getId());
    }
    @ParameterizedTest @ValueSource(strings = {"seller", "consumer", "sellerId", "consumerId", "both", "bothIds"})
    void anyIncompletePartyDeniesEvenWhenTheOtherPartyMatches(String missing) {
        authenticate(ReadFixture.id(missing.startsWith("seller") ? 2000 : 1000));
        switch (missing) {
            case "seller" -> transaction.setSeller(null);
            case "consumer" -> transaction.setConsumer(null);
            case "sellerId" -> transaction.getSeller().setUserId(null);
            case "consumerId" -> transaction.getConsumer().setUserId(null);
            case "both" -> { transaction.setSeller(null); transaction.setConsumer(null); }
            case "bothIds" -> { transaction.getSeller().setUserId(null); transaction.getConsumer().setUserId(null); }
        }
        deniedWithoutMapping();
    }
    @ParameterizedTest @ValueSource(strings = {"product", "category", "thumbnail", "images", "tags", "productStatus"})
    void thirdPartyIsDeniedBeforeBrokenMapperAssociationsAreAccessed(String broken) {
        authenticate(ReadFixture.id(4000));
        switch (broken) {
            case "product" -> transaction.setProduct(null);
            case "category" -> transaction.getProduct().setCategory(null);
            case "thumbnail" -> transaction.getProduct().setThumbnail(null);
            case "images" -> transaction.getProduct().setProductImages(null);
            case "tags" -> transaction.getProduct().setTags(null);
            case "productStatus" -> transaction.getProduct().setStatus(null);
        }
        clearInvocations(transaction);
        deniedWithoutMapping();
    }
    @Test void authorizedBrokenMapperIsNotRepairedOrConvertedTo403() {
        transaction.setProduct(null);
        assertThrows(NullPointerException.class, () -> service.readTransactionDetailsById(88L));
    }
    @ParameterizedTest @ValueSource(longs = {1, 2, 3, 4, 5})
    void transactionStatusDoesNotAlterPartyAccess(long status) {
        transaction.setStatus(new TransactionStatus(status, "synthetic-status"));
        clearInvocations(transaction);
        assertEquals(status, service.readTransactionDetailsById(88L).getStatus().getId());
    }
    @Test void nullAddressAndNullTransactionStatusKeepExistingDtoValues() {
        transaction.setAddress(null);
        transaction.setStatus(null);
        clearInvocations(transaction);
        var dto = service.readTransactionDetailsById(88L);
        assertNull(dto.getAddress());
        assertNull(dto.getStatus());
        assertEquals(300L, dto.getPrice());
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "anonymous", "unauthenticated", "nullUser", "nullEmail", "blankEmail"})
    void invalidDirectInvocationAuthenticationIs401BeforeRepositories(String scenario) {
        SecurityContextHolder.clearContext();
        if (scenario.equals("anonymous")) SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));
        if (scenario.equals("unauthenticated")) SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(User.builder().email(ReadChainHarness.EMAIL).build()), null));
        if (scenario.equals("nullUser") || scenario.equals("nullEmail") || scenario.equals("blankEmail")) {
            User user = scenario.equals("nullUser") ? null : User.builder().email(scenario.equals("blankEmail") ? " " : null).build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null, List.of()));
        }
        var error = assertThrows(ResponseStatusException.class, () -> service.readTransactionDetailsById(88L));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(users, details);
    }
    @ParameterizedTest @ValueSource(strings = {"absent", "nullId"})
    void unresolvedDbUserIs401BeforeTransactionLookup(String scenario) {
        when(users.findUserByEmail(ReadChainHarness.EMAIL)).thenReturn(scenario.equals("absent") ? Optional.empty() : Optional.of(ReadFixture.user(null)));
        var error = assertThrows(ResponseStatusException.class, () -> service.readTransactionDetailsById(99L));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(details);
    }
    @Test void authenticatedMissingTransactionKeepsEmptyDto() {
        var dto = service.readTransactionDetailsById(99L);
        assertNull(dto.getId()); assertNull(dto.getAddress()); assertNull(dto.getPrice());
        verify(users).findUserByEmail(ReadChainHarness.EMAIL);
    }
    @ParameterizedTest @ValueSource(strings = {"consumer", "seller"})
    void existingIdBasedListDtoMappingRemainsOutsideSingleReadGuard(String list) {
        SecurityContextHolder.clearContext();
        when(details.findALlByConsumerId(2000L)).thenReturn(List.of(transaction));
        when(details.findAllBySellerId(1000L)).thenReturn(List.of(transaction));
        var result = list.equals("consumer") ? service.readAllByConsumerId(2000L) : service.readAllBySellerId(1000L);
        assertEquals(1, result.size());
        assertEquals("synthetic-private-address", result.get(0).getAddress());
        assertEquals(88L, result.get(0).getId());
        verifyNoInteractions(users);
    }
    @ParameterizedTest @ValueSource(strings = {"user", "transaction"})
    void repositoryFailurePropagatesWithoutBecomingAuthenticationOrOwnershipDenial(String repository) {
        var failure = new DataAccessResourceFailureException("synthetic-database-unavailable");
        if (repository.equals("user")) when(users.findUserByEmail(ReadChainHarness.EMAIL)).thenThrow(failure);
        else when(details.findById(88L)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> service.readTransactionDetailsById(88L)));
    }
}
