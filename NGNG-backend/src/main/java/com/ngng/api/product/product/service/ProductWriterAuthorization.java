package com.ngng.api.product.product.service;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** Object checks for the additional product writers. HTTP role matchers remain in SecurityConfig. */
public final class ProductWriterAuthorization {
    private ProductWriterAuthorization() {}

    public static User requireCaller(AuthService authService, boolean creation) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof CustomUserDetails details)
                || details.getUser() == null || details.getUser().getEmail() == null || details.getUser().getEmail().isBlank()
                || authentication.getAuthorities().stream().noneMatch(a -> "ROLE_USER".equals(a.getAuthority())
                || "ROLE_ADMIN".equals(a.getAuthority()) || (!creation && "ROLE_UNCONFIRMED_USER".equals(a.getAuthority())))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        User caller = authService.readAuthUser();
        if (caller == null || caller.getUserId() == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return caller;
    }

    public static void requireManager(Product product, AuthService authService) {
        User caller = requireCaller(authService, false);
        if (product == null || product.getProductId() == null || product.getUser() == null || product.getUser().getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) return;
        if (!caller.getUserId().equals(product.getUser().getUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public static Product requireProduct(Long productId, ProductRepository products, AuthService authService) {
        if (productId == null || productId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Product product = products.findById(productId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireManager(product, authService);
        return product;
    }

    public static Long parseProductId(String productId) {
        try {
            Long parsed = Long.valueOf(productId);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
}
