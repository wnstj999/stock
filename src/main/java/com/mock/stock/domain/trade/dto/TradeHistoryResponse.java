package com.mock.stock.domain.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class TradeHistoryResponse {
    private String ticker;
    private String companyName;
    private String tradeType;
    private Long price;
    private Long quantity;
    private LocalDateTime tradeDate;
}
