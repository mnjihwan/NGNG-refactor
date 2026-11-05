package com.ngng.api.user.point.service;

import com.ngng.api.user.point.dto.CreateAddPointRequestDTO;
import com.ngng.api.user.point.dto.PaymentPointRequestDto;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j(topic ="point-log")
public class PointHistoryService {

    private final PointHistoryRepository pointHistoryRepository;
    private final AuthService authService;
    private final UserRepository userRepository;


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

    @Transactional
    public PointHistory payment(PaymentPointRequestDto request){
        requirePositive(request.getPaymentCost());
        User user = this.authService.readAuthUser();
        Long beforeCost = lockAndReadCost(user);
        if (beforeCost < request.getPaymentCost()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient point balance");
        }

        PointHistory pointHistory = PointHistory.builder()
                .beforeCost(beforeCost)
                .addCost(request.getPaymentCost())
                .cost(beforeCost - request.getPaymentCost())
                .type("결제")
                .typeDetail(user.getName() + " 유저가 "+request.getPaymentCost() + " 금액 만큼 " + request.getPayProductId() + "상품을 구매 하셨습니다.")
                .user(user)
                .build();

        PointHistory response = pointHistoryRepository.save(pointHistory);

        log.info("Success Payment Point UserId: {} ProductId : {} cost : {} ",response.getUser().getUserId(),request.getPayProductId(),request.getPaymentCost());

        return response;

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
