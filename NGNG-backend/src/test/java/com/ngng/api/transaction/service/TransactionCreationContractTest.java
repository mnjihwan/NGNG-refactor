package com.ngng.api.transaction.service;

import com.ngng.api.product.product.entity.Category;
import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.entity.Status;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.product.thumbnail.entity.Thumbnail;
import com.ngng.api.transaction.controller.TransactionController;
import com.ngng.api.transaction.dto.CreateTransactionDetailsRequestDTO;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.transaction.repository.TransactionStatusRepository;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** No Boot configuration, DB, security filters, or external services are loaded. */
class TransactionCreationContractTest {
    TransactionDetailsRepository details = mock(TransactionDetailsRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    TransactionDetailsService service = new TransactionDetailsService(details,
            mock(TransactionStatusRepository.class), products, mock(AuthService.class));
    TransactionRequestService requests = mock(TransactionRequestService.class);
    CreateTransactionDetailsRequestDTO input = new CreateTransactionDetailsRequestDTO(7L, 11L, 300L);
    Product product;

    @BeforeEach void setup() {
        product = Product.builder().productId(7L).title("product").price(900L).forSale(true)
                .user(User.builder().userId(12L).name("seller").phoneNumber("123").build())
                .category(Category.builder().categoryId(1L).categoryName("category").build())
                .status(Status.builder().statusId(1L).statusName("used").build())
                .thumbnail(Thumbnail.builder().thumbnailId(1L).thumbnailUrl("thumb").build())
                .productImages(List.of()).tags(List.of()).build();
        when(products.lockTransactionProduct(7L)).thenReturn(Optional.of(mock(ProductRepository.TransactionProduct.class)));
        when(products.findById(7L)).thenReturn(Optional.of(product));
        when(details.save(any())).thenAnswer(call -> {
            TransactionDetails saved = call.getArgument(0);
            saved.setId(88L);
            return saved;
        });
    }

    @Test void firstCreationPreservesSellerBuyerPriceStatusAndDto() {
        var response = service.create(input);
        assertEquals(88L, response.getId());
        assertEquals(300L, response.getPrice());
        assertEquals(1L, response.getStatus().getId());
        verify(details).save(argThat(saved -> saved.getProduct() == product
                && saved.getSeller() == product.getUser() && saved.getConsumer().getUserId().equals(11L)
                && saved.getPrice().equals(300L) && saved.getStatus().getId().equals(1L)));
        assertTrue(product.getForSale(), "direct creation retains its existing product/request state behavior");
        var order = inOrder(products, details);
        order.verify(products).lockTransactionProduct(7L);
        order.verify(details).flush();
        order.verify(details).findVisibleIdsByProductId(7L);
        order.verify(details).findCommittedIdsByProductId(7L);
        order.verify(products).findById(7L);
        order.verify(details).save(any());
    }

    @Test void currentDetailsMergeOwnAndCommittedIdsThenLockExistingPrimaryKeysOnce() {
        var own = mock(TransactionDetailsRepository.AcceptanceDetails.class);
        var committed = mock(TransactionDetailsRepository.AcceptanceDetails.class);
        when(details.findVisibleIdsByProductId(7L)).thenReturn(List.of(88L));
        when(details.findCommittedIdsByProductId(7L)).thenReturn(List.of(88L, 99L));
        when(details.lockAcceptanceDetailsById(88L)).thenReturn(Optional.of(own));
        when(details.lockAcceptanceDetailsById(99L)).thenReturn(Optional.of(committed));
        assertEquals(List.of(own, committed), service.findCurrentDetails(7L));
        var order = inOrder(details);
        order.verify(details).flush();
        order.verify(details).findVisibleIdsByProductId(7L);
        order.verify(details).findCommittedIdsByProductId(7L);
        order.verify(details).lockAcceptanceDetailsById(88L);
        order.verify(details).lockAcceptanceDetailsById(99L);
        order.verifyNoMoreInteractions();
    }

    @Test void directCreateIndependentReaderFailurePropagatesWithoutSaving() {
        RuntimeException failure = new RuntimeException("reader unavailable");
        when(details.findCommittedIdsByProductId(7L)).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.create(input)));
        verify(details, never()).save(any());
    }

    @Test void existingTransactionIs409WithoutSavingOrLoadingProductGraph() {
        when(details.findCommittedIdsByProductId(7L)).thenReturn(List.of(88L));
        when(details.lockAcceptanceDetailsById(88L)).thenReturn(Optional.of(mock(TransactionDetailsRepository.AcceptanceDetails.class)));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.create(input)).getStatusCode().value());
        verify(details, never()).save(any());
        verify(products, never()).findById(anyLong());
    }

    @Test void missingProductRetainsExistingAssertionBoundaryWithTestAssertionsEnabled() {
        when(products.lockTransactionProduct(7L)).thenReturn(Optional.empty());
        when(products.findById(7L)).thenReturn(Optional.empty());
        assertThrows(AssertionError.class, () -> service.create(input));
        verify(details, never()).save(any());
    }

    @Test void saveFailurePropagates() {
        RuntimeException failure = new RuntimeException("save failed");
        doThrow(failure).when(details).save(any());
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.create(input)));
    }

    @Test void mappingFailurePropagatesAfterSaving() {
        product.setThumbnail(null);
        assertThrows(NullPointerException.class, () -> service.create(input));
        verify(details).save(any());
    }

    @Test void directCreateProxyHasTransactionBeforeLockAndCommits() {
        RecordingTransactions manager = new RecordingTransactions();
        when(products.lockTransactionProduct(7L)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(mock(ProductRepository.TransactionProduct.class));
        });
        assertEquals(88L, proxy(manager).create(input).getId());
        assertEquals(1, manager.commits);
        assertEquals(0, manager.rollbacks);
    }

    @Test void directCreateSaveFailureTriggersRollbackAdvice() {
        RecordingTransactions manager = new RecordingTransactions();
        doThrow(new IllegalStateException("write failed")).when(details).save(any());
        assertThrows(IllegalStateException.class, () -> proxy(manager).create(input));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
    }

    @Test void directCreateMappingFailureTriggersRollbackAdvice() {
        RecordingTransactions manager = new RecordingTransactions();
        product.setThumbnail(null);
        assertThrows(NullPointerException.class, () -> proxy(manager).create(input));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
    }

    @Test void directCreateCommitFailureEscapesProxy() {
        RecordingTransactions manager = new RecordingTransactions();
        manager.failCommit = true;
        assertThrows(UnexpectedRollbackException.class, () -> proxy(manager).create(input));
    }

    @Test void firstPostRetains201LocationAndDtoFields() throws Exception {
        mvc(service).perform(post("/transaction").contentType("application/json")
                        .content("{\"productId\":7,\"buyerId\":11,\"price\":300}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/transaction/88"))
                .andExpect(jsonPath("$.id").value(88)).andExpect(jsonPath("$.price").value(300))
                .andExpect(jsonPath("$.status.id").value(1)).andExpect(jsonPath("$.product.id").value(7))
                .andExpect(jsonPath("$.seller.name").value("seller"));
    }

    @Test void requestUpdateRetains200NumericIdAndMissing404() throws Exception {
        when(requests.update(1L, true)).thenReturn(1L);
        when(requests.update(404L, true)).thenReturn(-1L);
        MockMvc mvc = mvc(service);
        mvc.perform(put("/transaction/request").contentType("application/json")
                        .content("{\"transactionRequestId\":1,\"isAccepted\":true}"))
                .andExpect(status().isOk()).andExpect(content().string("1"));
        mvc.perform(put("/transaction/request").contentType("application/json")
                        .content("{\"transactionRequestId\":404,\"isAccepted\":true}"))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
    }

    @Test void requestConflictAndDirectConflictAre409() throws Exception {
        when(requests.update(1L, true)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));
        when(details.findCommittedIdsByProductId(7L)).thenReturn(List.of(88L));
        when(details.lockAcceptanceDetailsById(88L)).thenReturn(Optional.of(mock(TransactionDetailsRepository.AcceptanceDetails.class)));
        MockMvc mvc = mvc(service);
        mvc.perform(put("/transaction/request").contentType("application/json")
                        .content("{\"transactionRequestId\":1,\"isAccepted\":true}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/transaction").contentType("application/json")
                        .content("{\"productId\":7,\"buyerId\":11,\"price\":300}"))
                .andExpect(status().isConflict());
    }

    @Test void malformedRequestBodyRemains400() throws Exception {
        mvc(service).perform(put("/transaction/request").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(requests);
    }

    private MockMvc mvc(TransactionDetailsService target) {
        return MockMvcBuilders.standaloneSetup(new TransactionController(target, requests)).build();
    }

    private TransactionDetailsService proxy(RecordingTransactions manager) {
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        TransactionInterceptor advice = new TransactionInterceptor();
        advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(advice);
        return (TransactionDetailsService) factory.getProxy();
    }

    private static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;
        boolean failCommit;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new UnexpectedRollbackException("test commit failed");
            commits++;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
