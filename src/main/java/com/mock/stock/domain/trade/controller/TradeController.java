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

    @PostMapping("/close/{positionId}")
    public ResponseEntity<String> closePosition(Authentication authentication, @PathVariable Long positionId) {
        String email = authentication.getName();
        tradeService.closePosition(email, positionId);
        return ResponseEntity.ok("포지션이 성공적으로 청산되었습니다.");
    }

    @PostMapping("/cancel/limit/{orderId}")
    public ResponseEntity<String> cancelLimitOrder(Authentication authentication, @PathVariable Long orderId) {
        String email = authentication.getName();
        tradeService.cancelLimitOrder(email, orderId);
        return ResponseEntity.ok("지정가 주문이 성공적으로 취소되었습니다.");
    }

    @GetMapping("/pending")
    public ResponseEntity<java.util.List<com.mock.stock.domain.trade.dto.LimitOrderResponse>> getPendingLimitOrders(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(tradeService.getPendingLimitOrders(email));
    }

    @GetMapping("/history")
    public ResponseEntity<java.util.List<com.mock.stock.domain.trade.dto.TradeHistoryResponse>> getMyTradeHistory(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(tradeService.getMyTradeHistory(email));
    }

    @PostMapping("/position/{positionId}/tpsl")
    public ResponseEntity<String> updatePositionTpSl(Authentication authentication, @PathVariable Long positionId, @RequestBody TpSlRequest request) {
        String email = authentication.getName();
        tradeService.updatePositionTpSl(email, positionId, request.getTpPrice(), request.getSlPrice());
        return ResponseEntity.ok("익절/손절 설정이 정상 업데이트되었습니다.");
    }

    @lombok.Data
    public static class TpSlRequest {
        private Double tpPrice;
        private Double slPrice;
    }
}

