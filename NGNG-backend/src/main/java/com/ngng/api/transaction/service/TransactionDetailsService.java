package com.ngng.api.transaction.service;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.dto.CreateTransactionDetailsRequestDTO;
import com.ngng.api.transaction.dto.ReadTransactionDetailsDTO;
import com.ngng.api.transaction.dto.UpdateTransactionDetailsRequestDTO;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.entity.TransactionStatus;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.transaction.repository.TransactionStatusRepository;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "transaction-log")
public class TransactionDetailsService extends Exception {

    private final TransactionDetailsRepository transactionDetailsRepository;
    private final TransactionStatusRepository transactionStatusRepository;
//    private final ProductService productService;
    private final ProductRepository productRepository;
    private final AuthService authService;

//    @Transactional(readOnly = true)
    public ReadTransactionDetailsDTO readTransactionDetailsById(Long TransactionId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails principal)
                || principal.getUser() == null || principal.getUser().getEmail() == null
                || principal.getUser().getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }
        User requester = authService.readAuthUser();
        if (requester == null || requester.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }
        TransactionDetails transactionDetails = transactionDetailsRepository.findById(TransactionId).orElse(null);
        if (transactionDetails != null) {
            User seller = transactionDetails.getSeller();
            User consumer = transactionDetails.getConsumer();
            if (seller == null || seller.getUserId() == null || consumer == null || consumer.getUserId() == null
                    || (!requester.getUserId().equals(seller.getUserId())
                    && !requester.getUserId().equals(consumer.getUserId()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "거래를 조회할 권한이 없습니다.");
            }
        }
        return new ReadTransactionDetailsDTO().from(transactionDetails);
    }

    public List<ReadTransactionDetailsDTO> readAllByConsumerId(Long id){
        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findALlByConsumerId(id);

        return  transactionDetails.stream()
                .map(item -> new ReadTransactionDetailsDTO().from(item))
                .collect(Collectors.toList());
    }

    public List<ReadTransactionDetailsDTO> readAllByConsumer(){
        User user = authService.readAuthUser();
        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findALlByConsumerId(user.getUserId());

        return  transactionDetails.stream()
                .map(item -> new ReadTransactionDetailsDTO().from(item))
                .collect(Collectors.toList());
    }

    public List<ReadTransactionDetailsDTO> readAllBySellerId(Long id){
        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findAllBySellerId(id);

        return  transactionDetails.stream()
                .map(item -> new ReadTransactionDetailsDTO().from(item))
                .collect(Collectors.toList());
    }
    public List<ReadTransactionDetailsDTO> readAllBySeller(){
        User user = authService.readAuthUser();
        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findAllBySellerId(user.getUserId());

        return  transactionDetails.stream()
                .map(item -> new ReadTransactionDetailsDTO().from(item))
                .collect(Collectors.toList());
    }


    public List<ReadTransactionDetailsDTO> readAllBySellerIdAndStatusId(Long status){
        User user = authService.readAuthUser();
        List<TransactionDetails> transactionDetails = transactionDetailsRepository.findAllBySellerIdFilterStatus(user.getUserId(),status);

        return  transactionDetails.stream()
                .map(item -> new ReadTransactionDetailsDTO().from(item))
                .collect(Collectors.toList());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReadTransactionDetailsDTO create(CreateTransactionDetailsRequestDTO request){
        productRepository.lockTransactionProduct(request.getProductId());
        if (!findCurrentDetails(request.getProductId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 거래가 생성된 상품입니다.");
        }
        Product product = productRepository.findById(request.getProductId()).orElse(null);


        assert product != null;
        TransactionDetails transactionDetails = TransactionDetails.builder()
                .product(product)
                .consumer(User.builder().userId(request.getBuyerId()).build())
                .seller(product.getUser())
                .status(new TransactionStatus(1L))
                .price(request.getPrice())
                .build();

        TransactionDetails ds = transactionDetailsRepository.save(transactionDetails);
        log.info("Success Create TransactionDetails id: {} Seller : {}  Consumer : {} ",ds.getId(),ds.getSeller().getUserId(),ds.getConsumer().getUserId());

        return new ReadTransactionDetailsDTO().from(ds);
    }

    /** Caller must hold this product's lock in its write transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<TransactionDetails> findCurrentDetails(Long productId) {
        return transactionDetailsRepository.findAllByProductProductId(productId);
    }

    @Transactional()
    public ReadTransactionDetailsDTO updateTransactionStatus(Long transId, UpdateTransactionDetailsRequestDTO request){

        TransactionDetails transactionDetails;
        try{
//            System.out.println(transId+"-"+request.getStatus().getId());
//            int  ds = transactionDetailsRepository.updateTransactionStatus(transId,request.getStatus().getId());

//            System.out.println(ds);


            transactionDetails = transactionDetailsRepository.findById(transId).orElseThrow();

            User user = authService.readAuthUser();

            if(
                !Objects.equals(transactionDetails.getProduct().getUser().getUserId(), user.getUserId()) &&
                !Objects.equals(transactionDetails.getConsumer().getUserId(), user.getUserId())
            ) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"소유권이 없는 유저 입니다.");

//            TransactionStatus status = transactionStatusRepository.findById(request.getStatus().getId()).orElseThrow();
            TransactionStatus status = transactionStatusRepository.findById(request.getStatusId()).orElseThrow();


            transactionDetails.setStatus(status);

            log.info("Success Update TransactionDetails id: {} Status :{} ",transactionDetails.getId(),status.getId());

        } catch (ResponseStatusException e) {
            System.out.println(e);

            log.error("Faild Create TransactionDetails id: {} ",transId);
            throw new ResponseStatusException(e.getStatusCode(),e.getMessage());
//            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"잘못된 쿼리 요청 입니다.");
        }


//        transactionDetailsRepository.save(transactionDetails);

        return new ReadTransactionDetailsDTO().from(transactionDetails);
    }
}
