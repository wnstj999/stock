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
    private String reason;
}
