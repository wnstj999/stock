package com.mock.stock.domain.trade.controller;

import com.mock.stock.domain.trade.dto.TradeRequest;
import com.mock.stock.domain.trade.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    @PostMapping("/buy")
    public ResponseEntity<String> buyStock(Authentication authentication, @RequestBody TradeRequest request) {
        String email = authentication.getName();
        tradeService.buyStock(email, request);
        return ResponseEntity.ok("매수가 완료되었습니다.");
    }

    @PostMapping("/sell")
    public ResponseEntity<String> sellStock(Authentication authentication, @RequestBody TradeRequest request) {
        String email = authentication.getName();
        tradeService.sellStock(email, request);
        return ResponseEntity.ok("매도 성공");
    }

    @GetMapping("/history")
    public ResponseEntity<java.util.List<com.mock.stock.domain.trade.dto.TradeHistoryResponse>> getMyTradeHistory(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(tradeService.getMyTradeHistory(email));
    }
}
