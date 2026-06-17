package com.mock.stock.domain.trade.service;

import com.mock.stock.domain.stock.entity.Stock;
import com.mock.stock.domain.stock.repository.StockRepository;
import com.mock.stock.domain.trade.dto.TradeRequest;
import com.mock.stock.domain.trade.entity.TradeHistory;
import com.mock.stock.domain.trade.entity.UserStock;
import com.mock.stock.domain.trade.repository.TradeHistoryRepository;
import com.mock.stock.domain.trade.repository.UserStockRepository;
import com.mock.stock.domain.user.entity.User;
import com.mock.stock.domain.user.repository.UserRepository;
import com.mock.stock.domain.wallet.entity.Wallet;
import com.mock.stock.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final StockRepository stockRepository;
    private final UserStockRepository userStockRepository;
    private final TradeHistoryRepository tradeHistoryRepository;

    @Transactional
    public void buyStock(String email, TradeRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Long userId = user.getId();
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
        Stock stock = stockRepository.findByTicker(request.getTicker())
                .orElseThrow(() -> new IllegalArgumentException("주식을 찾을 수 없습니다."));

        long totalCost = stock.getCurrentPrice() * request.getQuantity();

        // 1. 예수금 차감
        wallet.deductBalance(new BigDecimal(totalCost));

        // 2. 보유 주식 증가
        UserStock userStock = userStockRepository.findByUserIdAndStockId(userId, stock.getId())
                .orElse(UserStock.builder().user(user).stock(stock).quantity(0L).averagePrice(0L).build());
        userStock.addQuantity(request.getQuantity(), stock.getCurrentPrice());
        userStockRepository.save(userStock);

        // 3. 거래 내역 기록
        tradeHistoryRepository.save(TradeHistory.builder()
                .user(user).stock(stock).tradeType("BUY")
                .price(stock.getCurrentPrice()).quantity(request.getQuantity())
                .build());
    }

    @Transactional
    public void sellStock(String email, TradeRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Long userId = user.getId();
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
        Stock stock = stockRepository.findByTicker(request.getTicker())
                .orElseThrow(() -> new IllegalArgumentException("주식을 찾을 수 없습니다."));

        // 1. 보유 주식 차감
        UserStock userStock = userStockRepository.findByUserIdAndStockId(userId, stock.getId())
                .orElseThrow(() -> new IllegalArgumentException("보유한 주식이 없습니다."));
        userStock.deductQuantity(request.getQuantity());
        
        if (userStock.getQuantity() == 0) {
            userStockRepository.delete(userStock);
        }

        // 2. 예수금 증가
        long totalRevenue = stock.getCurrentPrice() * request.getQuantity();
        wallet.addBalance(new BigDecimal(totalRevenue));

        // 3. 거래 내역 기록
        tradeHistoryRepository.save(TradeHistory.builder()
                .user(user).stock(stock).tradeType("SELL")
                .price(stock.getCurrentPrice()).quantity(request.getQuantity())
                .build());
    }
}
