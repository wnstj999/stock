package com.mock.stock.domain.stock.controller;

import com.mock.stock.domain.stock.entity.Stock;
import com.mock.stock.domain.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    // 인증 없이 누구나 현재 주식 시장 상황을 볼 수 있도록 할 수도 있고, 인증된 유저만 볼 수도 있음.
    // 일단 프론트에서 인증 토큰을 보내므로 인증 통과 후 조회됨.
    @GetMapping
    public ResponseEntity<List<Stock>> getStocks() {
        return ResponseEntity.ok(stockService.getAllStocks());
    }
}
