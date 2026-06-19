package com.mock.stock.domain.trade.scheduler;

import com.mock.stock.domain.stock.entity.Stock;
import com.mock.stock.domain.stock.repository.StockRepository;
import com.mock.stock.domain.trade.entity.LimitOrder;
import com.mock.stock.domain.trade.entity.OrderStatus;
import com.mock.stock.domain.trade.entity.Position;
import com.mock.stock.domain.trade.entity.PositionType;
import com.mock.stock.domain.trade.repository.LimitOrderRepository;
import com.mock.stock.domain.trade.repository.PositionRepository;
import com.mock.stock.domain.trade.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LimitOrderScheduler {

    private final LimitOrderRepository limitOrderRepository;
    private final PositionRepository positionRepository;
    private final StockRepository stockRepository;
    private final TradeService tradeService;

    // 3초 간격으로 미체결 지정가 주문 및 보유 포지션 익절(TP)/손절(SL) 조건 실시간 감시
    @Scheduled(fixedDelay = 3000)
    public void monitorMarketAndOrders() {
        checkAndExecuteLimitOrders();
        checkAndExecuteTpSl();
    }

    private void checkAndExecuteLimitOrders() {
        List<LimitOrder> pendingOrders = limitOrderRepository.findByStatus(OrderStatus.PENDING);
        if (pendingOrders.isEmpty()) {
            return;
        }

        for (LimitOrder order : pendingOrders) {
            try {
                Stock stock = stockRepository.findByTicker(order.getStock().getTicker())
                        .orElse(null);
                if (stock == null) {
                    continue;
                }

                double currentPrice = stock.getCurrentPrice().doubleValue();
                double targetPrice = order.getPrice();

                boolean isTriggered = false;

                if (order.getPositionType() == PositionType.LONG) {
                    // 롱 지정가: 시장가 <= 지정가 일 때 체결 (지정한 가격 이하로 저렴하게 매수)
                    if (currentPrice <= targetPrice) {
                        isTriggered = true;
                    }
                } else if (order.getPositionType() == PositionType.SHORT) {
                    // 숏 지정가: 시장가 >= 지정가 일 때 체결 (지정한 가격 이상으로 비싸게 매도)
                    if (currentPrice >= targetPrice) {
                        isTriggered = true;
                    }
                }

                if (isTriggered) {
                    log.info("지정가 주문 체결 조건 충족 - OrderID: {}, Ticker: {}, PositionType: {}, TargetPrice: {}, CurrentPrice: {}",
                            order.getId(), stock.getTicker(), order.getPositionType(), targetPrice, currentPrice);
                    tradeService.executeLimitOrder(order);
                }
            } catch (Exception e) {
                log.error("지정가 주문 스케줄러 처리 오류 - OrderID: " + order.getId(), e);
            }
        }
    }

    private void checkAndExecuteTpSl() {
        List<Position> activePositions = positionRepository.findAll();
        if (activePositions.isEmpty()) {
            return;
        }

        for (Position p : activePositions) {
            try {
                // 둘 다 입력되지 않은 포지션은 패스
                if (p.getTakeProfitPrice() == null && p.getStopLossPrice() == null) {
                    continue;
                }

                Stock stock = stockRepository.findByTicker(p.getStock().getTicker())
                        .orElse(null);
                if (stock == null) {
                    continue;
                }

                double currentPrice = stock.getCurrentPrice().doubleValue();
                if (currentPrice <= 0) {
                    continue;
                }

                boolean isTriggered = false;
                String triggerReason = "";

                // 롱 포지션 익절/손절 판별
                if (p.getPositionType() == PositionType.LONG) {
                    if (p.getTakeProfitPrice() != null && currentPrice >= p.getTakeProfitPrice()) {
                        isTriggered = true;
                        triggerReason = "익절가 도달 (TP: " + p.getTakeProfitPrice() + ")";
                    } else if (p.getStopLossPrice() != null && currentPrice <= p.getStopLossPrice()) {
                        isTriggered = true;
                        triggerReason = "손절가 도달 (SL: " + p.getStopLossPrice() + ")";
                    }
                } 
                // 숏 포지션 익절/손절 판별
                else if (p.getPositionType() == PositionType.SHORT) {
                    if (p.getTakeProfitPrice() != null && currentPrice <= p.getTakeProfitPrice()) {
                        isTriggered = true;
                        triggerReason = "익절가 도달 (TP: " + p.getTakeProfitPrice() + ")";
                    } else if (p.getStopLossPrice() != null && currentPrice >= p.getStopLossPrice()) {
                        isTriggered = true;
                        triggerReason = "손절가 도달 (SL: " + p.getStopLossPrice() + ")";
                    }
                }

                if (isTriggered) {
                    log.info("익절/손절 스케줄러 포지션 청산 실행 - PositionID: {}, Ticker: {}, 이유: {}, 현재가: {}",
                            p.getId(), stock.getTicker(), triggerReason, currentPrice);
                    
                    // TradeService.closePosition을 호출하여 가용 잔고 환불 및 거래 이력 생성
                    tradeService.closePosition(p.getUser().getEmail(), p.getId());
                }
            } catch (Exception e) {
                log.error("익절/손절 스케줄러 처리 오류 - PositionID: " + p.getId(), e);
            }
        }
    }
}
