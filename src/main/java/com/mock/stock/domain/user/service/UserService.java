package com.mock.stock.domain.user.service;

import com.mock.stock.domain.trade.entity.Position;
import com.mock.stock.domain.trade.entity.OrderStatus;
import com.mock.stock.domain.trade.entity.LimitOrder;
import com.mock.stock.domain.trade.repository.PositionRepository;
import com.mock.stock.domain.trade.repository.LimitOrderRepository;
import com.mock.stock.domain.trade.dto.LimitOrderResponse;
import com.mock.stock.domain.user.dto.UserInfoResponse;
import com.mock.stock.domain.user.entity.User;
import com.mock.stock.domain.user.repository.UserRepository;
import com.mock.stock.domain.wallet.entity.Wallet;
import com.mock.stock.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PositionRepository positionRepository;
    private final LimitOrderRepository limitOrderRepository;

    @Transactional(readOnly = true)
    public UserInfoResponse getUserInfo(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Long userId = user.getId();
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
        
        List<Position> positions = positionRepository.findByUserId(userId);

        List<UserInfoResponse.PositionDto> holdingDtos = positions.stream()
                .map(p -> {
                    double currentPrice = p.getStock().getCurrentPrice().doubleValue();
                    double unrealizedPnl = 0.0;
                    if (p.getPositionType() == com.mock.stock.domain.trade.entity.PositionType.LONG) {
                        unrealizedPnl = (currentPrice - p.getEntryPrice()) * p.getQuantity();
                    } else {
                        unrealizedPnl = (p.getEntryPrice() - currentPrice) * p.getQuantity();
                    }
                    double pnlRate = p.getMargin() > 0 ? (unrealizedPnl / p.getMargin()) * 100 : 0.0;

                    return UserInfoResponse.PositionDto.builder()
                            .id(p.getId())
                            .ticker(p.getStock().getTicker())
                            .companyName(p.getStock().getCompanyName())
                            .positionType(p.getPositionType().name())
                            .quantity(p.getQuantity())
                            .entryPrice(p.getEntryPrice())
                            .currentPrice(currentPrice)
                            .leverage(p.getLeverage())
                            .margin(p.getMargin())
                            .unrealizedPnl(unrealizedPnl)
                            .pnlRate(pnlRate)
                            .takeProfitPrice(p.getTakeProfitPrice())
                            .stopLossPrice(p.getStopLossPrice())
                            .build();
                })
                .collect(Collectors.toList());

        // 대기 지정가 주문 조회
        List<LimitOrder> pendingOrders = limitOrderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, OrderStatus.PENDING);
        
        // 락 증거금 총합
        double totalLockedMargin = pendingOrders.stream()
                .mapToDouble(LimitOrder::getMargin)
                .sum();

        List<LimitOrderResponse> pendingOrderDtos = pendingOrders.stream()
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
                .collect(Collectors.toList());

        return UserInfoResponse.builder()
                .email(user.getEmail())
                .nickname(user.getNickname())
                .walletBalance(wallet.getBalance())
                .lockedBalance(new BigDecimal(totalLockedMargin))
                .holdings(holdingDtos)
                .pendingOrders(pendingOrderDtos)
                .build();
    }
}


