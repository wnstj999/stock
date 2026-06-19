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
        // 이미 DB의 주식 잔재가 소거되었으므로, 서버 재시작 시 데이터 소실을 막기 위해 
        // deleteAll 강제 소거 로직을 완전히 제거하고 코인 5종이 없을 때만 주입하도록 복구합니다.
        if (stockRepository.count() == 0) {
            stockRepository.save(Stock.builder().ticker("KRW-BTC").companyName("비트코인").currentPrice(0L).previousClose(0L).build());
            stockRepository.save(Stock.builder().ticker("KRW-ETH").companyName("이더리움").currentPrice(0L).previousClose(0L).build());
            stockRepository.save(Stock.builder().ticker("KRW-XRP").companyName("리플").currentPrice(0L).previousClose(0L).build());
            stockRepository.save(Stock.builder().ticker("KRW-SOL").companyName("솔라나").currentPrice(0L).previousClose(0L).build());
            stockRepository.save(Stock.builder().ticker("KRW-DOGE").companyName("도지코인").currentPrice(0L).previousClose(0L).build());
            log.info("코인 모의투자 마켓 기초 데이터 5종 세팅을 완료했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }
}
