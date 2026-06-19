package com.mock.stock.domain.stock.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mock.stock.domain.stock.entity.Stock;
import com.mock.stock.domain.stock.repository.StockRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockPriceScheduler {

    private final StockRepository stockRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Data
    public static class UpbitTickerResponse {
        private String market;
        @JsonProperty("trade_price")
        private Double tradePrice;
        @JsonProperty("prev_closing_price")
        private Double prevClosingPrice;
    }

    @Scheduled(fixedDelay = 2000) // 2초마다 실행
    @Transactional
    public void updateStockPrices() {
        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) {
            return;
        }

        // 종목들의 티커를 쉼표로 연결 (예: "KRW-BTC,KRW-ETH...")
        String codes = stocks.stream()
                .map(Stock::getTicker)
                .collect(Collectors.joining(","));

        String url = "https://api.upbit.com/v1/ticker?markets=" + codes;

        try {
            UpbitTickerResponse[] response = restTemplate.getForObject(url, UpbitTickerResponse[].class);
            if (response != null) {
                Map<String, UpbitTickerResponse> tickerMap = List.of(response).stream()
                        .collect(Collectors.toMap(UpbitTickerResponse::getMarket, r -> r));

                for (Stock stock : stocks) {
                    UpbitTickerResponse tickerData = tickerMap.get(stock.getTicker());
                    if (tickerData != null) {
                        Long newPrice = tickerData.getTradePrice().longValue();
                        Long newPrevClose = tickerData.getPrevClosingPrice().longValue();
                        stock.updatePrice(newPrice, newPrevClose);
                    }
                }
            }
        } catch (Exception e) {
            log.error("업비트 시세 동기화 실패: {}", e.getMessage());
        }
    }
}
