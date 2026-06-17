package com.mock.stock.domain.stock.service;

import com.mock.stock.domain.stock.entity.Stock;
import com.mock.stock.domain.stock.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockRepository stockRepository;
    private final Random random = new Random();

    // 서버 시작 시 초기 주식 데이터 세팅
    @PostConstruct
    @Transactional
    public void initStocks() {
        if (stockRepository.count() == 0) {
            stockRepository.save(Stock.builder().ticker("005930").companyName("삼성전자").currentPrice(80000L).previousClose(79500L).build());
            stockRepository.save(Stock.builder().ticker("035720").companyName("카카오").currentPrice(45000L).previousClose(46000L).build());
            stockRepository.save(Stock.builder().ticker("035420").companyName("NAVER").currentPrice(190000L).previousClose(188000L).build());
            stockRepository.save(Stock.builder().ticker("005380").companyName("현대차").currentPrice(250000L).previousClose(255000L).build());
            stockRepository.save(Stock.builder().ticker("373220").companyName("LG에너지솔루션").currentPrice(380000L).previousClose(380000L).build());
            log.info("초기 가상 주식 데이터가 세팅되었습니다.");
        }
    }

    // 5초마다 주식 가격 랜덤 변동 (가상 장 운영)
    @Scheduled(fixedRate = 5000)
    @Transactional
    public void fluctuatePrices() {
        List<Stock> stocks = stockRepository.findAll();
        for (Stock stock : stocks) {
            // -2% ~ +2% 사이의 랜덤 변동률
            double changePercent = (random.nextDouble() * 4) - 2;
            long changeAmount = (long) (stock.getCurrentPrice() * (changePercent / 100.0));
            
            long newPrice = stock.getCurrentPrice() + changeAmount;
            // 100원 단위로 끊기
            newPrice = (newPrice / 100) * 100;
            if (newPrice < 100) newPrice = 100; // 최소가 방어

            stock.updatePrice(newPrice);
        }
    }

    @Transactional(readOnly = true)
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }
}
