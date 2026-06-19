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

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockRepository stockRepository;
    private final PositionRepository positionRepository;
    private final LimitOrderRepository limitOrderRepository;
    private final TradeHistoryRepository tradeHistoryRepository;

    @PostConstruct
    @Transactional
    public void initStocks() {
        // 외래 키 제약 조건(Foreign Key Constraint) 위반을 방지하기 위해 
        // 자식 테이블(보유 포지션, 대기 주문, 거래 내역) 데이터를 먼저 제거한 후 마켓 테이블을 초기화합니다.
        try {
            positionRepository.deleteAll();
            limitOrderRepository.deleteAll();
            tradeHistoryRepository.deleteAll();
            stockRepository.deleteAll();
            log.info("기존 주식 모의투자 잔재 데이터를 성공적으로 비웠습니다.");
        } catch (Exception e) {
            log.warn("DB 초기 세정 도중 예외가 발생했습니다 (정상 흐름 가능): {}", e.getMessage());
        }
        
        stockRepository.save(Stock.builder().ticker("KRW-BTC").companyName("비트코인").currentPrice(0L).previousClose(0L).build());
        stockRepository.save(Stock.builder().ticker("KRW-ETH").companyName("이더리움").currentPrice(0L).previousClose(0L).build());
        stockRepository.save(Stock.builder().ticker("KRW-XRP").companyName("리플").currentPrice(0L).previousClose(0L).build());
        stockRepository.save(Stock.builder().ticker("KRW-SOL").companyName("솔라나").currentPrice(0L).previousClose(0L).build());
        stockRepository.save(Stock.builder().ticker("KRW-DOGE").companyName("도지코인").currentPrice(0L).previousClose(0L).build());
        log.info("코인 모의투자 마켓 기초 데이터 5종 세팅을 완료했습니다.");
    }

    @Transactional(readOnly = true)
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }
}
