package com.mock.stock.domain.stock.service;

import com.mock.stock.domain.stock.entity.Stock;
import com.mock.stock.domain.stock.repository.StockRepository;
import com.mock.stock.domain.trade.repository.PositionRepository;
import com.mock.stock.domain.trade.repository.LimitOrderRepository;
import com.mock.stock.domain.trade.repository.TradeHistoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.redis.core.RedisTemplate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockRepository stockRepository;
    private final PositionRepository positionRepository;
    private final LimitOrderRepository limitOrderRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @PostConstruct
    @Transactional
    public void initStocks() {
        // 이미 DB의 주식 잔재가 소거되었으므로, 서버 재시작 시 데이터 소실을 막기 위해 
        // deleteAll 강제 소거 로직을 완전히 제거하고 코인 5종이 없을 때만 주입하도록 복구합니다.
        String[] tickers = {"KRW-BTC", "KRW-ETH", "KRW-XRP", "KRW-SOL", "KRW-DOGE"};
        String[] names = {"비트코인", "이더리움", "리플", "솔라나", "도지코인"};
        for (int i = 0; i < tickers.length; i++) {
            if (stockRepository.findByTicker(tickers[i]) == null) {
                stockRepository.save(Stock.builder()
                        .ticker(tickers[i])
                        .companyName(names[i])
                        .currentPrice(0L)
                        .previousClose(0L)
                        .build());
                log.info("코인 모의투자 마켓 기초 데이터 주입 완료: {}", tickers[i]);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Stock> getAllStocks() {
        try {
            // 1. 레디스 캐시에서 전체 시세 조회
            Object cachedStocks = redisTemplate.opsForValue().get("stocks:all");
            if (cachedStocks instanceof List) {
                @SuppressWarnings("unchecked")
                List<Stock> stockList = (List<Stock>) cachedStocks;
                if (!stockList.isEmpty()) {
                    return stockList;
                }
            }
        } catch (Exception e) {
            // 레디스 서버 장애 발생 시 안전하게 로그만 남기고 데이터베이스 폴백 수행
            log.error("레디스 캐시 조회 실패, 마이에스큐엘 데이터베이스로 폴백 진행: {}", e.getMessage());
        }

        // 2. 레디스 캐시 미적용 또는 에러 시 마이에스큐엘에서 직접 조회
        return stockRepository.findAll();
    }
}
