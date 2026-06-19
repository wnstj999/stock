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
    private Long quantity;
}
