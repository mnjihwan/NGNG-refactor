package com.ngng.api.user.point.controller;

import com.ngng.api.user.point.dto.CheckPointRequestDTO;
import com.ngng.api.user.point.dto.CreateAddPointRequestDTO;
import com.ngng.api.user.point.dto.PaymentPointRequestDto;
import com.ngng.api.user.point.dto.PointHistoryResponseDTO;
import com.ngng.api.user.point.service.PointHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/points")
public class PointController {
    private final PointHistoryService pointHistoryService;


    @GetMapping("")
    public ResponseEntity<PointHistoryResponseDTO> readPoint(){
        return ResponseEntity.ok().body(PointHistoryResponseDTO.from(pointHistoryService.readCost()));
    }

    @GetMapping("/all")
    public ResponseEntity<List<PointHistoryResponseDTO>> readAllPointHistory(){
        return ResponseEntity.ok().body(pointHistoryService.readPointHistories().stream()
                .map(PointHistoryResponseDTO::from).toList());
    }


    @PostMapping("")
    public ResponseEntity<PointHistoryResponseDTO> addPoint(@RequestBody @Valid final CreateAddPointRequestDTO dto){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PointHistoryResponseDTO.from(pointHistoryService.updateCost(dto)));
    }


    @PostMapping("/payment")
    public ResponseEntity<PointHistoryResponseDTO> payProduct(@RequestBody @Valid PaymentPointRequestDto paymentPointRequestDto){

        return ResponseEntity.ok().body(PointHistoryResponseDTO.from(pointHistoryService.payment(paymentPointRequestDto)));
    }


    @PostMapping("/check")
    public ResponseEntity<Boolean> checkPoint(@RequestBody @Valid CheckPointRequestDTO checkPointRequestDTO) {
        return ResponseEntity.ok().body(pointHistoryService.isPaymnet(checkPointRequestDTO.getPaymentCost()));
    }


}
