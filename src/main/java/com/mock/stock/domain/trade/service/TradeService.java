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

        long currentPrice = stock.getCurrentPrice();
        if (currentPrice <= 0) {
            throw new IllegalArgumentException("현재 시세 정보가 유효하지 않습니다. 잠시 후 다시 시도해주세요.");
        }
        long totalCost = currentPrice * request.getQuantity();

        // 1. 예수금 차감
        wallet.deductBalance(new BigDecimal(totalCost));

        // 2. 보유 주식 증가
        UserStock userStock = userStockRepository.findByUserIdAndStockId(userId, stock.getId())
                .orElse(UserStock.builder().user(user).stock(stock).quantity(0L).averagePrice(0L).build());
        userStock.addQuantity(request.getQuantity(), currentPrice);
        userStockRepository.save(userStock);

        // 3. 거래 내역 기록
        tradeHistoryRepository.save(TradeHistory.builder()
                .user(user).stock(stock).tradeType("BUY")
                .price(currentPrice).quantity(request.getQuantity())
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

        long currentPrice = stock.getCurrentPrice();
        if (currentPrice <= 0) {
            throw new IllegalArgumentException("현재 시세 정보가 유효하지 않습니다. 잠시 후 다시 시도해주세요.");
        }

        // 1. 보유 주식 차감
        UserStock userStock = userStockRepository.findByUserIdAndStockId(userId, stock.getId())
                .orElseThrow(() -> new IllegalArgumentException("보유한 주식이 없습니다."));
        userStock.deductQuantity(request.getQuantity());
        
        if (userStock.getQuantity() == 0) {
            userStockRepository.delete(userStock);
        }

        // 2. 예수금 증가
        long totalRevenue = currentPrice * request.getQuantity();
        wallet.addBalance(new BigDecimal(totalRevenue));

        // 3. 거래 내역 기록
        tradeHistoryRepository.save(TradeHistory.builder()
                .user(user).stock(stock).tradeType("SELL")
                .price(currentPrice).quantity(request.getQuantity())
                .build());
    }

    @Transactional(readOnly = true)
    public java.util.List<com.mock.stock.domain.trade.dto.TradeHistoryResponse> getMyTradeHistory(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
                
        return tradeHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(th -> com.mock.stock.domain.trade.dto.TradeHistoryResponse.builder()
                        .ticker(th.getStock().getTicker())
                        .companyName(th.getStock().getCompanyName())
                        .tradeType(th.getTradeType())
                        .price(th.getPrice())
                        .quantity(th.getQuantity())
                        .tradeDate(th.getCreatedAt())
                        .build())
                .toList();
    }
}
