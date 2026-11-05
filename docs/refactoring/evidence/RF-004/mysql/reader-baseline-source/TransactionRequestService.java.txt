package com.ngng.api.transaction.service;

import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.dto.CreateTransactionDetailsRequestDTO;
import com.ngng.api.transaction.dto.CreateTransactionRequestDTO;
import com.ngng.api.transaction.dto.TransactionRequestDTO;
import com.ngng.api.transaction.entity.TransactionRequest;
import com.ngng.api.transaction.repository.TransactionRequestRepository;
import com.ngng.api.user.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class TransactionRequestService {
    private final TransactionRequestRepository transactionRequestRepository;
    private final ProductRepository productRepository;
    private final TransactionDetailsService transactionDetailsService;

    public Long create(CreateTransactionRequestDTO request){

        TransactionRequest target = transactionRequestRepository.findByProductProductIdAndBuyerUserId(request.getProductId(), request.getBuyerId()).orElse(null);
        if(target == null){
            return transactionRequestRepository.save(
                    TransactionRequest.builder()
                            .product(Product.builder().productId(request.getProductId()).build())
                            .buyer(User.builder().userId(request.getBuyerId()).build())
                            .seller(User.builder().userId(request.getSellerId()).build())
                            .price(request.getPrice())
                            .build()
            ).getTransactionRequestId();
        }else{
            System.out.println("이미 요청 했었음");
            return -1L;
        }

    }

    @Transactional
    public Long update( Long requestId, Boolean isAccepted){
        Assert.notNull(requestId, "The given id must not be null");
        Long productId = transactionRequestRepository.findProductIdForUpdate(requestId).orElse(null);
        if (productId == null) {
            return -1L;
        }
        // All approved writers take the product lock first. These scalar locking
        // reads do not reuse an earlier RR snapshot or managed entity state.
        ProductRepository.TransactionProduct product = productRepository.lockTransactionProduct(productId).orElse(null);
        if (product == null) return -1L;
        transactionRequestRepository.flush();
        TransactionRequestRepository.AcceptanceState target = transactionRequestRepository.lockAcceptanceState(requestId).orElse(null);
        if (target == null) return -1L;
        if (!Objects.equals(productId, target.getProductId())) throw conflict();

        if (isAccepted) {
            var existing = transactionDetailsService.findCurrentDetails(productId);
            if (Boolean.TRUE.equals(target.getAccepted())) {
                if (existing.size() == 1
                        && Objects.equals(existing.get(0).getBuyerId(), target.getBuyerId())
                        && Objects.equals(existing.get(0).getSellerId(), product.getSellerId())
                        && Objects.equals(existing.get(0).getPrice(), target.getPrice())) {
                    return requestId;
                }
                throw conflict();
            }
            if (!existing.isEmpty()) throw conflict();
            productRepository.markTransactionSold(productId, updateTimestamp());
            transactionDetailsService.create(new CreateTransactionDetailsRequestDTO(productId, target.getBuyerId(), target.getPrice()));
            transactionRequestRepository.updateAcceptance(requestId, true, updateTimestamp());
            var otherIds = new TreeSet<>(transactionRequestRepository.findVisibleIdsByProductId(productId));
            otherIds.addAll(transactionRequestRepository.findCommittedIdsByProductId(productId));
            for (Long otherId : otherIds) {
                if (!Objects.equals(otherId, requestId)) {
                    transactionRequestRepository.updateAcceptance(otherId, false, updateTimestamp());
                }
            }
        } else {
            if (Boolean.TRUE.equals(target.getAccepted())) throw conflict();
            if (!Boolean.FALSE.equals(target.getAccepted())) {
                transactionRequestRepository.updateAcceptance(requestId, false, updateTimestamp());
            }
        }
        return requestId;
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "이미 확정된 거래 요청과 충돌합니다.");
    }

    private Timestamp updateTimestamp() {
        // Match @UpdateTimestamp's VM clock and MySQL timestamp precision.
        return Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    public List<TransactionRequestDTO> readAll(Long productId){
        return transactionRequestRepository.findAllByProductProductId(productId).stream()
                .map(TransactionRequestDTO::new).toList();
    }
}
