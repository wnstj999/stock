package com.mock.stock.domain.trade.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TradeRequest {
    private String ticker;
    private Long quantity;
}
