package com.ngng.api.product.authorization;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.controller.ProductController;
import com.ngng.api.product.product.dto.request.UpdateProductRequestDTO;
import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.product.product.service.*;
import com.ngng.api.product.thumbnail.service.ThumbnailService;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.entity.TransactionStatus;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static com.ngng.api.product.authorization.ProductAuthorizationFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductAuthorizationServiceTest {
    ProductRepository products;
    TagService tags;
    AuthService auth;
    ProductService service;
    Product target;
    @BeforeEach void setup() {
        products = mock(ProductRepository.class); tags = mock(TagService.class); auth = mock(AuthService.class);
        service = new ProductService(products, tags, auth); target = spy(product(distinctId(1000)));
        when(products.findById(77L)).thenReturn(Optional.of(target));
        when(products.save(any())).thenAnswer(inv -> inv.getArgument(0));
        authenticate("USER", new CustomUserDetails(user(null)), true);
        when(auth.readAuthUser()).thenReturn(user(distinctId(1000)));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void authenticate(String role, Object principal, boolean authenticated) {
        var token = new UsernamePasswordAuthenticationToken(principal, "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        if (!authenticated) token.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(token);
    }
    void operation(String method, UpdateProductRequestDTO request) {
        if (method.equals("PUT")) service.update(77L, request); else service.delete(77L);
    }
    void deniedWithoutWrites(String method, boolean noAuthLookup) {
        var request = mock(UpdateProductRequestDTO.class);
        clearInvocations(target);
        var exception = assertThrows(ResponseStatusException.class, () -> operation(method, request));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(request, tags);
        if (noAuthLookup) verifyNoInteractions(auth);
        verify(target, never()).getTags(); verify(target, never()).getReports(); verify(target, never()).getTransactionDetails();
        verify(target, never()).from(any()); verify(target, never()).setVisible(any()); verify(target, never()).setForSale(any());
        verify(products, never()).save(any());
        assertEquals("original", target.getTitle()); assertTrue(target.getVisible()); assertTrue(target.getForSale());
        assertEquals("old", target.getTags().get(0).getTagName());
    }
    @ParameterizedTest @CsvSource({"PUT,other", "DELETE,other", "PUT,no-caller", "DELETE,no-caller",
            "PUT,no-caller-id", "DELETE,no-caller-id", "PUT,no-owner", "DELETE,no-owner",
            "PUT,no-owner-id", "DELETE,no-owner-id", "PUT,both-null-id", "DELETE,both-null-id",
            "PUT,wrong-role", "DELETE,wrong-role"})
    void ownershipFailurePrecedesAllRequestTagAndBusinessAccess(String method, String kind) {
        switch (kind) {
            case "other" -> when(auth.readAuthUser()).thenReturn(user(2000L));
            case "no-caller" -> when(auth.readAuthUser()).thenReturn(null);
            case "no-caller-id" -> when(auth.readAuthUser()).thenReturn(user(null));
            case "no-owner" -> target.setUser(null);
            case "no-owner-id" -> target.setUser(user(null));
            case "both-null-id" -> { target.setUser(user(null)); when(auth.readAuthUser()).thenReturn(user(null)); }
            case "wrong-role" -> authenticate("UNCONFIRMED_USER", new CustomUserDetails(user(null)), true);
        }
        deniedWithoutWrites(method, kind.equals("wrong-role"));
    }
    @ParameterizedTest @CsvSource({"PUT,null", "DELETE,null", "PUT,unauth-user", "DELETE,unauth-user",
            "PUT,unauth-admin", "DELETE,unauth-admin", "PUT,string-user", "DELETE,string-user",
            "PUT,string-admin", "DELETE,string-admin", "PUT,anonymous", "DELETE,anonymous",
            "PUT,null-internal-user", "DELETE,null-internal-user", "PUT,null-email", "DELETE,null-email",
            "PUT,blank-email", "DELETE,blank-email", "PUT,null-principal", "DELETE,null-principal",
            "PUT,anonymous-custom-principal", "DELETE,anonymous-custom-principal"})
    void malformedAuthenticationIsControlledDenialEvenWithAdminAuthority(String method, String kind) {
        switch (kind) {
            case "null" -> SecurityContextHolder.clearContext();
            case "unauth-user" -> authenticate("USER", new CustomUserDetails(user(null)), false);
            case "unauth-admin" -> authenticate("ADMIN", new CustomUserDetails(user(null)), false);
            case "string-user" -> authenticate("USER", "anonymousUser", true);
            case "string-admin" -> authenticate("ADMIN", "anonymousUser", true);
            case "anonymous" -> SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            case "anonymous-custom-principal" -> SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", new CustomUserDetails(user(null)), List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            case "null-internal-user" -> authenticate("ADMIN", new CustomUserDetails(null), true);
            case "null-email" -> authenticate("ADMIN", new CustomUserDetails(User.builder().build()), true);
            case "blank-email" -> authenticate("ADMIN", new CustomUserDetails(User.builder().email(" ").build()), true);
            case "null-principal" -> authenticate("ADMIN", null, true);
        }
        deniedWithoutWrites(method, true);
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "ADMIN"})
    void allowedUpdatePreservesFieldsOwnerAndTagReplacement(String role) {
        authenticate(role, new CustomUserDetails(user(null)), true);
        User owner = target.getUser();
        if (role.equals("USER")) { User caller = auth.readAuthUser(); assertEquals(owner.getUserId(), caller.getUserId()); assertNotSame(owner.getUserId(), caller.getUserId()); clearInvocations(auth); }
        else when(auth.readAuthUser()).thenReturn(user(2000L));
        var response = service.update(77L, ProductAuthorizationFixture.request());
        assertEquals(77L, response.getId()); assertEquals("changed", response.getTitle()); assertEquals("new content", response.getContent());
        assertEquals(400L, response.getPrice()); assertTrue(response.getIsEscrow()); assertTrue(response.getDiscountable());
        assertTrue(response.getFreeShipping()); assertTrue(response.getVisible()); assertTrue(response.getForSale());
        assertEquals("2026-11", response.getPurchaseAt()); assertEquals(2L, response.getStatus().getId()); assertEquals(3L, response.getCategory().getId());
        assertNull(response.getStatus().getName()); assertNull(response.getCategory().getName());
        assertEquals("new", response.getTags().get(0).getTagName()); assertSame(owner, target.getUser());
        verify(tags).deleteAllByProductIdAndTagName(77L, List.of("old")); verify(products, never()).save(any());
        if (role.equals("ADMIN")) verifyNoInteractions(auth);
    }
    @ParameterizedTest @CsvSource({"USER,null-reports,true", "USER,null-transaction,true", "USER,status6,true", "USER,status1,false",
            "ADMIN,null-reports,true", "ADMIN,null-transaction,true", "ADMIN,status6,true", "ADMIN,status1,false"})
    void deleteRetainsExistingOrBusinessBranches(String role, String branch, boolean allowed) {
        authenticate(role, new CustomUserDetails(user(null)), true);
        when(auth.readAuthUser()).thenReturn(user(role.equals("ADMIN") ? 2000L : distinctId(1000)));
        target.setTransactionDetails(TransactionDetails.builder().status(new TransactionStatus(branch.equals("status6") ? 6L : 1L, "fixture")).build());
        if (branch.equals("null-reports")) target.setReports(null);
        if (branch.equals("null-transaction")) target.setTransactionDetails(null);
        assertEquals(allowed ? 77L : -1L, service.delete(77L));
        assertEquals(!allowed, target.getVisible()); assertEquals(!allowed, target.getForSale());
        if (allowed) verify(products).save(target); else verify(products, never()).save(any());
        verifyNoInteractions(tags);
        if (role.equals("ADMIN")) verifyNoInteractions(auth);
    }
    @ParameterizedTest @ValueSource(strings = {"PUT", "DELETE"})
    void missingProductRetainsExistingExceptionBeforeAuthLookup(String method) {
        when(products.findById(77L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> operation(method, mock(UpdateProductRequestDTO.class)));
        verifyNoInteractions(tags, auth); verify(products, never()).save(any());
    }
    @ParameterizedTest @ValueSource(strings = {"PUT", "DELETE"})
    void noFilterMvcStillMapsObjectDenialTo403(String method) throws Exception {
        when(auth.readAuthUser()).thenReturn(user(2000L));
        var controller = new ProductController(service, mock(CompressService.class), mock(AwsS3Service.class), mock(UploadService.class),
                mock(ProductImageService.class), mock(ThumbnailService.class), auth);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var request = method.equals("PUT") ? put("/products/77").contentType("application/json").content(BODY) : delete("/products/77");
        mvc.perform(request).andExpect(status().isForbidden()).andExpect(content().string(""));
        assertTrue(target.getVisible()); assertEquals("original", target.getTitle()); verifyNoInteractions(tags); verify(products, never()).save(any());
    }
}
