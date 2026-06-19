package com.mock.stock.domain.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AiPredictionResponse {
    private String ticker;
    private String prediction;
    private double confidence;
    private String action; // STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL
    private Long targetPrice; // 목표가
    private Long stopLossPrice; // 손절가
    private Integer recommendedLeverage; // 추천 레버리지
    private String reason;
}

