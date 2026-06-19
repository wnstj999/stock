package com.mock.stock.domain.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class LimitOrderResponse {
    private Long id;
    private String ticker;
    private String companyName;
    private String positionType; // LONG or SHORT
    private Double price;
    private Double quantity;
    private Integer leverage;
    private Double margin;
    private LocalDateTime createdAt;
}
