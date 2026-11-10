package com.ngng.api.transaction.service;

import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.product.product.service.ProductCacheInvalidator;
import com.ngng.api.transaction.dto.CreateTransactionDetailsRequestDTO;
import com.ngng.api.transaction.dto.CreateTransactionRequestDTO;
import com.ngng.api.transaction.dto.TransactionRequestDTO;
import com.ngng.api.transaction.entity.TransactionRequest;
import com.ngng.api.transaction.repository.TransactionRequestRepository;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.service.AuthService;
import com.ngng.api.global.security.custom.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.Assert;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TransactionRequestService {
    private final TransactionRequestRepository transactionRequestRepository;
    private final ProductRepository productRepository;
    private final TransactionDetailsService transactionDetailsService;
    private final ProductCacheInvalidator productCacheInvalidator;
    private final AuthService authService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long create(CreateTransactionRequestDTO request){
        User buyer = authenticatedUser();
        requireSameUser(buyer.getUserId(), request.getBuyerId());
        var locked = productRepository.lockTransactionProduct(request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireSameUser(locked.getSellerId(), request.getSellerId());
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requireSameUser(product.getUser() == null ? null : product.getUser().getUserId(), request.getSellerId());
        // Do not expose the duplicate result until both buyer and actual seller are authorized.
        TransactionRequest target = transactionRequestRepository.findByProductProductIdAndBuyerUserId(request.getProductId(), request.getBuyerId()).orElse(null);
        if(target == null){
            return transactionRequestRepository.save(
                    TransactionRequest.builder()
                            .product(product)
                            .buyer(buyer)
                            .seller(product.getUser())
                            .price(request.getPrice())
                            .build()
            ).getTransactionRequestId();
        }else{
            System.out.println("이미 요청 했었음");
            return -1L;
        }

    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long update( Long requestId, Boolean isAccepted){
        Assert.notNull(requestId, "The given id must not be null");
        User caller = authenticatedUser();
        Long productId = transactionRequestRepository.findProductIdForUpdate(requestId).orElse(null);
        if (productId == null) {
            return -1L;
        }
        // Lock only the product row before loading its eager associations. The
        // REST entry point starts this transaction without preloaded entities.
        var locked = productRepository.lockTransactionProduct(productId).orElse(null);
        if (locked == null) return -1L;
        requireSameUser(locked.getSellerId(), caller.getUserId());
        TransactionRequest target = transactionRequestRepository.findById(requestId).orElse(null);
        if (target == null) return -1L;
        Product product = target.getProduct();
        requireSameUser(product.getUser() == null ? null : product.getUser().getUserId(), caller.getUserId());
        if (!Objects.equals(productId, product.getProductId())) throw conflict();

        if (isAccepted) {
            var existing = transactionDetailsService.findCurrentDetails(productId);
            if (Boolean.TRUE.equals(target.getIsAccepted())) {
                if (existing.size() == 1
                        && existing.get(0).getConsumer() != null
                        && existing.get(0).getSeller() != null
                        && Objects.equals(existing.get(0).getConsumer().getUserId(), target.getBuyer().getUserId())
                        && Objects.equals(existing.get(0).getSeller().getUserId(), product.getUser().getUserId())
                        && Objects.equals(existing.get(0).getPrice(), target.getPrice())) {
                    return requestId;
                }
                throw conflict();
            }
            if (!existing.isEmpty()) throw conflict();
            product.setForSale(false);
            productRepository.save(product);
            transactionDetailsService.create(new CreateTransactionDetailsRequestDTO(productId, target.getBuyer().getUserId(), target.getPrice()));
            target.setIsAccepted(true);
            transactionRequestRepository.save(target);
            for (TransactionRequest other : transactionRequestRepository.findAllByProductProductId(productId)) {
                if (!Objects.equals(other.getTransactionRequestId(), requestId)
                        && !Boolean.FALSE.equals(other.getIsAccepted())) {
                    other.setIsAccepted(false);
                    transactionRequestRepository.save(other);
                }
            }
            productCacheInvalidator.invalidate(productId);
        } else {
            if (Boolean.TRUE.equals(target.getIsAccepted())) throw conflict();
            if (!Boolean.FALSE.equals(target.getIsAccepted())) {
                target.setIsAccepted(false);
                transactionRequestRepository.save(target);
            }
        }
        return requestId;
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "이미 확정된 거래 요청과 충돌합니다.");
    }

    private User authenticatedUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof CustomUserDetails details)
                || details.getUser() == null || details.getUser().getEmail() == null
                || details.getUser().getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User user = authService.readAuthUser();
        if (user == null || user.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    private void requireSameUser(Long expected, Long actual) {
        if (expected == null || actual == null || !Objects.equals(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "거래 요청에 대한 권한이 없습니다.");
        }
    }

    public List<TransactionRequestDTO> readAll(Long productId){
        return transactionRequestRepository.findAllByProductProductId(productId).stream()
                .map(TransactionRequestDTO::new).toList();
    }
}
