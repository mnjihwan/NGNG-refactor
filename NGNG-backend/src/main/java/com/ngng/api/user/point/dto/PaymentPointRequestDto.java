package com.ngng.api.user.point.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentPointRequestDto {
    @NotNull
    @Positive
    private Long paymentCost;

    @NotNull
    @Positive
    private Long payProductId;

}
