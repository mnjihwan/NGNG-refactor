package com.ngng.api.user.point.service;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.user.point.dto.CreateAddPointRequestDTO;
import com.ngng.api.user.point.dto.PaymentPointRequestDto;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.payment.PaymentVerificationGate;
import com.ngng.api.user.point.repository.PaymentSettlementRepository;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j(topic ="point-log")
public class PointHistoryService {

    private final PointHistoryRepository pointHistoryRepository;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final TransactionDetailsRepository transactionDetailsRepository;
    private final PaymentVerificationGate paymentVerificationGate;
    private final PaymentSettlementRepository paymentSettlementRepository;


    public PointHistory createInitByUser(User user){
        PointHistory pointHistory = PointHistory.builder()
                .beforeCost(0L)
                .addCost(0L)
                .cost(0L)
                .type("기본 생성")
                .typeDetail(user.getName()+" 유저가 생성 되면서 기본으로 생성된 포인트")
                .user(user)
                .build();

        PointHistory response = pointHistoryRepository.save(pointHistory);

        log.info("Success Create PointHistory id: {} User : {} ",response.getId(),response.getUser().getUserId());
        return response;
//        return pointHistory;
    }


    public PointHistory readCostByUser(User user){
        return pointHistoryRepository.findLastByUserId(user.getUserId()).orElse(null);

    }

    public List<PointHistory> readCostsByUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return pointHistoryRepository.findLatestByUserIds(userIds);
    }

    public PointHistory readCost(){
        User user = this.authService.readAuthUser();
        return pointHistoryRepository.findLastByUserId(user.getUserId()).orElse(null);
    }

    public List<PointHistory> readPointHistories() {
        User user = this.authService.readAuthUser();
        return pointHistoryRepository.findAllByUserId(user.getUserId());
    }

    @Transactional
    public PointHistory updateCost(CreateAddPointRequestDTO request){
        requirePositive(request.getAddCost());
        User user = this.authService.readAuthUser();
        Long beforeCost = lockAndReadCost(user);
        long cost;
        try {
            cost = Math.addExact(beforeCost, request.getAddCost());
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Point balance exceeds the supported range");
        }

        PointHistory pointHistory = PointHistory.builder()
                .beforeCost(beforeCost)
                .addCost(request.getAddCost())
                .cost(cost)
                .type(request.getType())
                .typeDetail(user.getName() + " 유저가 "+request.getAddCost() + " 금액 만큼 " + request.getType() + " 하셨습니다.")
                .user(user)
                .build();

        PointHistory response = pointHistoryRepository.save(pointHistory);

        log.info("Success Charge Point UserId: {} cost : {} ",response.getUser().getUserId(),request.getAddCost());

        return response;

    }

    public boolean isPaymnet(Long pay) {
        if (pay == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount is required");
        }
        if (pay <= 0) return false;
        User user = this.authService.readAuthUser();
        PointHistory lastHistory = readCostByUser(user);

        return lastHistory.getCost() >= pay;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointHistory payment(PaymentPointRequestDto request){
        requirePositive(request.getPaymentCost());
        if (request.getPayProductId() == null || request.getPayProductId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment product is required");
        }
        User user = readPaymentUser();
        TransactionDetailsRepository.PaymentTransaction transaction = lockAndValidatePayment(request, user);
        Long paymentCost = transaction.getPrice();
        requireVerified(transaction);
        Long beforeCost = lockAndReadCost(user);
        if (beforeCost < paymentCost) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient point balance");
        }

        // Approval may expire while the buyer/balance locks are awaited.
        requireVerified(transaction);
        PointHistory pointHistory = PointHistory.builder()
                .beforeCost(beforeCost)
                .addCost(paymentCost)
                .cost(beforeCost - paymentCost)
                .type("결제")
                .typeDetail(user.getName() + " 유저가 "+paymentCost + " 금액 만큼 " + request.getPayProductId() + "상품을 구매 하셨습니다.")
                .user(user)
                .build();

        PointHistory response = pointHistoryRepository.save(pointHistory);

        // Both writes remain in this transaction. Do not clear caller-managed state.
        pointHistoryRepository.flush();
        int changed = paymentSettlementRepository.markPaid(transaction.getTransactionId(),
                transaction.getProductId(), transaction.getConsumerId(), transaction.getSellerId(), paymentCost);
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment transaction changed");
        }

        log.info("Success Payment Point UserId: {} ProductId : {} cost : {} ",response.getUser().getUserId(),request.getPayProductId(),paymentCost);

        return response;

    }

    private User readPaymentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails principal)
                || principal.getUser() == null || principal.getUser().getEmail() == null
                || principal.getUser().getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        User user = authService.readAuthUser();
        if (user == null || user.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return user;
    }

    private TransactionDetailsRepository.PaymentTransaction lockAndValidatePayment(PaymentPointRequestDto request, User user) {
        productRepository.lockTransactionProduct(request.getPayProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment product was not found"));
        List<TransactionDetailsRepository.PaymentTransaction> transactions =
                transactionDetailsRepository.lockPaymentTransactions(request.getPayProductId());
        if (transactions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction was not found");
        }
        if (transactions.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment transaction is incomplete or ambiguous");
        }
        TransactionDetailsRepository.PaymentTransaction transaction = transactions.get(0);
        if (transaction.getTransactionId() == null || transaction.getTransactionId() <= 0
                || transaction.getConsumerId() == null || transaction.getConsumerId() <= 0
                || transaction.getSellerId() == null || transaction.getSellerId() <= 0
                || !request.getPayProductId().equals(transaction.getProductId())
                || transaction.getStatusId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment transaction is incomplete or ambiguous");
        }
        if (!user.getUserId().equals(transaction.getConsumerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment is restricted to the recorded buyer");
        }
        Long price = transaction.getPrice();
        if (price == null || price <= 0 || !price.equals(request.getPaymentCost())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment amount must match the transaction price");
        }
        if (!Long.valueOf(1).equals(transaction.getStatusId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment transaction is not awaiting payment");
        }
        return transaction;
    }

    private void requireVerified(TransactionDetailsRepository.PaymentTransaction transaction) {
        paymentVerificationGate.requireVerified(transaction.getTransactionId(), transaction.getProductId(),
                transaction.getConsumerId(), transaction.getSellerId(), transaction.getPrice(), transaction.getStatusId());
    }

    private Long lockAndReadCost(User user) {
        userRepository.lockUserIdForPointUpdate(user.getUserId()).orElseThrow();
        return pointHistoryRepository.findLatestCostForUpdate(user.getUserId()).orElseThrow();
    }

    private void requirePositive(Long amount) {
        if (amount == null || amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Point amount must be positive");
        }
    }
}
