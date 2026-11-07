package com.storex.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotNull Long userId,
        @NotBlank String productName,
        @Min(1) int quantity,
        @NotNull @DecimalMin("0.0") BigDecimal amount
) {
}
