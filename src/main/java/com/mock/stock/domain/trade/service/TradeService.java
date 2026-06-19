package com.mock.stock.domain.trade.service;

import com.mock.stock.domain.stock.entity.Stock;
import com.mock.stock.domain.stock.repository.StockRepository;
import com.mock.stock.domain.trade.dto.TradeRequest;
import com.mock.stock.domain.trade.dto.TradeHistoryResponse;
import com.mock.stock.domain.trade.dto.LimitOrderResponse;
import com.mock.stock.domain.trade.entity.*;
import com.mock.stock.domain.trade.repository.PositionRepository;
import com.mock.stock.domain.trade.repository.TradeHistoryRepository;
import com.mock.stock.domain.trade.repository.LimitOrderRepository;
import com.mock.stock.domain.user.entity.User;
import com.mock.stock.domain.user.repository.UserRepository;
import com.mock.stock.domain.wallet.entity.Wallet;
import com.mock.stock.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final StockRepository stockRepository;
    private final PositionRepository positionRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final LimitOrderRepository limitOrderRepository;

    @Transactional
    public void buyStock(String email, TradeRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Long userId = user.getId();
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
        Stock stock = stockRepository.findByTicker(request.getTicker())
                .orElseThrow(() -> new IllegalArgumentException("주식을 찾을 수 없습니다."));

        int leverage = (request.getLeverage() != null && request.getLeverage() >= 1) ? request.getLeverage() : 1;

        // 지정가(LIMIT) 분기 처리
        if ("LIMIT".equalsIgnoreCase(request.getOrderType())) {
            Double price = request.getPrice();
            if (price == null || price <= 0.0) {
                throw new IllegalArgumentException("지정가 주문에는 유효한 가격 입력이 필요합니다.");
            }
            double requiredMargin = (price * request.getQuantity()) / leverage;
            // 1. 예수금에서 증거금 선차감 (Margin Lock)
            wallet.deductBalance(new BigDecimal(requiredMargin));

            // 2. 미체결 대기 주문 생성
            limitOrderRepository.save(LimitOrder.builder()
                    .user(user)
                    .stock(stock)
                    .positionType(PositionType.LONG)
                    .price(price)
                    .quantity(request.getQuantity())
                    .leverage(leverage)
                    .margin(requiredMargin)
                    .status(OrderStatus.PENDING)
                    .build());
            return;
        }

        // 시장가(MARKET) 처리
        double currentPrice = stock.getCurrentPrice().doubleValue();
        if (currentPrice <= 0) {
            throw new IllegalArgumentException("현재 시세 정보가 유효하지 않습니다. 잠시 후 다시 시도해주세요.");
        }
        double requiredMargin = (currentPrice * request.getQuantity()) / leverage;
        wallet.deductBalance(new BigDecimal(requiredMargin));

        Position position = positionRepository.findByUserIdAndStockIdAndPositionType(userId, stock.getId(), PositionType.LONG)
                .orElse(Position.builder()
                        .user(user)
                        .stock(stock)
                        .positionType(PositionType.LONG)
                        .quantity(0.0)
                        .entryPrice(0.0)
                        .leverage(leverage)
                        .margin(0.0)
                        .build());
        
        position.addQuantity(request.getQuantity(), currentPrice);
        positionRepository.save(position);

        tradeHistoryRepository.save(TradeHistory.builder()
                .user(user)
                .stock(stock)
                .tradeType("OPEN_LONG")
                .price(currentPrice)
                .quantity(request.getQuantity())
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

        int leverage = (request.getLeverage() != null && request.getLeverage() >= 1) ? request.getLeverage() : 1;

        // 지정가(LIMIT) 분기 처리
        if ("LIMIT".equalsIgnoreCase(request.getOrderType())) {
            Double price = request.getPrice();
            if (price == null || price <= 0.0) {
                throw new IllegalArgumentException("지정가 주문에는 유효한 가격 입력이 필요합니다.");
            }
            double requiredMargin = (price * request.getQuantity()) / leverage;
            // 1. 예수금에서 증거금 선차감 (Margin Lock)
            wallet.deductBalance(new BigDecimal(requiredMargin));

            // 2. 미체결 대기 주문 생성
            limitOrderRepository.save(LimitOrder.builder()
                    .user(user)
                    .stock(stock)
                    .positionType(PositionType.SHORT)
                    .price(price)
                    .quantity(request.getQuantity())
                    .leverage(leverage)
                    .margin(requiredMargin)
                    .status(OrderStatus.PENDING)
                    .build());
            return;
        }

        // 시장가(MARKET) 처리
        double currentPrice = stock.getCurrentPrice().doubleValue();
        if (currentPrice <= 0) {
            throw new IllegalArgumentException("현재 시세 정보가 유효하지 않습니다. 잠시 후 다시 시도해주세요.");
        }
        double requiredMargin = (currentPrice * request.getQuantity()) / leverage;
        wallet.deductBalance(new BigDecimal(requiredMargin));

        Position position = positionRepository.findByUserIdAndStockIdAndPositionType(userId, stock.getId(), PositionType.SHORT)
                .orElse(Position.builder()
                        .user(user)
                        .stock(stock)
                        .positionType(PositionType.SHORT)
                        .quantity(0.0)
                        .entryPrice(0.0)
                        .leverage(leverage)
                        .margin(0.0)
                        .build());
        
        position.addQuantity(request.getQuantity(), currentPrice);
        positionRepository.save(position);

        tradeHistoryRepository.save(TradeHistory.builder()
                .user(user)
                .stock(stock)
                .tradeType("OPEN_SHORT")
                .price(currentPrice)
                .quantity(request.getQuantity())
                .build());
    }

    @Transactional
    public void closePosition(String email, Long positionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Position p = positionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포지션입니다."));

        if (!p.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없는 포지션 청산 요청입니다.");
        }

        double currentPrice = p.getStock().getCurrentPrice().doubleValue();
        
        double unrealizedPnl = 0.0;
        if (p.getPositionType() == PositionType.LONG) {
            unrealizedPnl = (currentPrice - p.getEntryPrice()) * p.getQuantity();
        } else {
            unrealizedPnl = (p.getEntryPrice() - currentPrice) * p.getQuantity();
        }

        double refundAmount = p.getMargin() + unrealizedPnl;
        if (refundAmount < 0) {
            refundAmount = 0.0;
        }

        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
        wallet.addBalance(new BigDecimal(refundAmount));

        String tradeType = p.getPositionType() == PositionType.LONG ? "CLOSE_LONG" : "CLOSE_SHORT";
        tradeHistoryRepository.save(TradeHistory.builder()
                .user(user)
                .stock(p.getStock())
                .tradeType(tradeType)
                .price(currentPrice)
                .quantity(p.getQuantity())
                .build());

        positionRepository.delete(p);
    }

    @Transactional
    public void cancelLimitOrder(String email, Long orderId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        LimitOrder order = limitOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 대기 주문입니다."));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없는 주문 취소 요청입니다.");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("대기(PENDING) 상태의 주문만 취소할 수 있습니다.");
        }

        // 1. 주문 취소 처리
        order.updateStatus(OrderStatus.CANCELED);
        limitOrderRepository.save(order);

        // 2. 증거금 락(Lock) 해제 -> 예수금 환불
        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
        wallet.addBalance(new BigDecimal(order.getMargin()));
    }

    @Transactional
    public void executeLimitOrder(LimitOrder order) {
        // 스케줄러가 가격 부합 여부 판단 시 호출하여 포지션 진입 처리
        order.updateStatus(OrderStatus.FILLED);
        limitOrderRepository.save(order);

        User user = order.getUser();
        Stock stock = order.getStock();

        Position position = positionRepository.findByUserIdAndStockIdAndPositionType(user.getId(), stock.getId(), order.getPositionType())
                .orElse(Position.builder()
                        .user(user)
                        .stock(stock)
                        .positionType(order.getPositionType())
                        .quantity(0.0)
                        .entryPrice(0.0)
                        .leverage(order.getLeverage())
                        .margin(0.0)
                        .build());

        // 지정가(order.getPrice()) 시세를 기준으로 포지션 가중 누적 정산
        position.addQuantity(order.getQuantity(), order.getPrice());
        positionRepository.save(position);

        String historyType = order.getPositionType() == PositionType.LONG ? "OPEN_LONG" : "OPEN_SHORT";
        tradeHistoryRepository.save(TradeHistory.builder()
                .user(user)
                .stock(stock)
                .tradeType(historyType)
                .price(order.getPrice())
                .quantity(order.getQuantity())
                .build());
    }

    @Transactional(readOnly = true)
    public List<LimitOrderResponse> getPendingLimitOrders(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return limitOrderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), OrderStatus.PENDING)
                .stream()
                .map(o -> LimitOrderResponse.builder()
                        .id(o.getId())
                        .ticker(o.getStock().getTicker())
                        .companyName(o.getStock().getCompanyName())
                        .positionType(o.getPositionType().name())
                        .price(o.getPrice())
                        .quantity(o.getQuantity())
                        .leverage(o.getLeverage())
                        .margin(o.getMargin())
                        .createdAt(o.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TradeHistoryResponse> getMyTradeHistory(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
                
        return tradeHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(th -> TradeHistoryResponse.builder()
                        .ticker(th.getStock().getTicker())
                        .companyName(th.getStock().getCompanyName())
                        .tradeType(th.getTradeType())
                        .price(th.getPrice())
                        .quantity(th.getQuantity())
                        .tradeDate(th.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public void updatePositionTpSl(String email, Long positionId, Double tpPrice, Double slPrice) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Position p = positionRepository.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포지션입니다."));

        if (!p.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("해당 포지션 설정을 변경할 권한이 없습니다.");
        }

        // 0 이하의 값은 설정 해제(null)로 처리합니다.
        if (tpPrice != null && tpPrice <= 0.0) tpPrice = null;
        if (slPrice != null && slPrice <= 0.0) slPrice = null;

        p.updateTpSl(tpPrice, slPrice);
        positionRepository.save(p);
    }
}

