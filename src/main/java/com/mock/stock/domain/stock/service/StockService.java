package com.mock.stock.domain.stock.service;

import com.mock.stock.domain.stock.entity.Stock;
import com.mock.stock.domain.stock.repository.StockRepository;
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

    @PostConstruct
    @Transactional
    public void initStocks() {
        if (stockRepository.count() == 0) {
            stockRepository.save(Stock.builder().ticker("KRW-BTC").companyName("비트코인").currentPrice(0L).previousClose(0L).build());
            stockRepository.save(Stock.builder().ticker("KRW-ETH").companyName("이더리움").currentPrice(0L).previousClose(0L).build());
            stockRepository.save(Stock.builder().ticker("KRW-XRP").companyName("리플").currentPrice(0L).previousClose(0L).build());
            stockRepository.save(Stock.builder().ticker("KRW-SOL").companyName("솔라나").currentPrice(0L).previousClose(0L).build());
            stockRepository.save(Stock.builder().ticker("KRW-DOGE").companyName("도지코인").currentPrice(0L).previousClose(0L).build());
            log.info("초기 암호화폐 마켓 기초 데이터가 세팅되었습니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }
}
