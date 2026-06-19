package com.mock.stock.domain.trade.controller;

import com.mock.stock.domain.trade.service.AutoTradingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auto-trade")
@RequiredArgsConstructor
public class AutoTradingController {

    private final AutoTradingService autoTradingService;

    @GetMapping("/predict")
    public ResponseEntity<com.mock.stock.domain.trade.dto.AiPredictionResponse> predict(Authentication authentication, @RequestParam String ticker) {
        String email = authentication.getName();
        com.mock.stock.domain.trade.dto.AiPredictionResponse result = autoTradingService.getAiPrediction(email, ticker);
        return ResponseEntity.ok(result);
    }
}
