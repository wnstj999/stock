package com.mock.stock.domain.trade.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AllArgsConstructor;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TradeRequest {
    private String ticker;
    private Double quantity;
    private Integer leverage;
    private Double price; // 지정가
    private String orderType; // "MARKET" or "LIMIT"
}


