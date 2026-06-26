package com.mock.stock.domain.trade.scheduler;

import com.mock.stock.domain.stock.entity.Stock;
import com.mock.stock.domain.stock.repository.StockRepository;
import com.mock.stock.domain.trade.dto.AiPredictionResponse;
import com.mock.stock.domain.trade.dto.TradeRequest;
import com.mock.stock.domain.trade.entity.Position;
import com.mock.stock.domain.trade.entity.PositionType;
import com.mock.stock.domain.trade.repository.PositionRepository;
import com.mock.stock.domain.trade.service.AutoTradingService;
import com.mock.stock.domain.trade.service.TradeService;
import com.mock.stock.domain.user.entity.User;
import com.mock.stock.domain.user.repository.UserRepository;
import com.mock.stock.domain.wallet.entity.Wallet;
import com.mock.stock.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAgentTradingScheduler {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final StockRepository stockRepository;
    private final PositionRepository positionRepository;
    private final AutoTradingService autoTradingService;
    private final TradeService tradeService;

    // 30초마다 AI 자율 트레이딩 감시 및 실행
    @Scheduled(fixedDelay = 30000)
    public void executeAiAgentTrading() {
        List<User> activeUsers = userRepository.findByAiAgentActive(true);
        if (activeUsers.isEmpty()) {
            return;
        }

        List<Stock> stocks = stockRepository.findAll();
        if (stocks.isEmpty()) {
            return;
        }

        log.info("AI 자율 트레이딩 에이전트 스케줄러 작동 시작 - 대상 사용자 수: {}명", activeUsers.size());

        for (User user : activeUsers) {
            try {
                // 1. 이미 열린 포지션이 있는 유저라면 리스크 제어를 위해 건너뜀 (한 번에 하나의 포지션만 유지)
                List<Position> holdings = positionRepository.findByUserId(user.getId());
                if (!holdings.isEmpty()) {
                    continue;
                }

                // 2. 가용 자산 체크 (최소 100만 원 이상이어야 매매 집행)
                Wallet wallet = walletRepository.findByUserId(user.getId()).orElse(null);
                if (wallet == null || wallet.getBalance().compareTo(new BigDecimal("1000000")) < 0) {
                    log.warn("사용자 {} 의 예수금 잔고가 부족하여 자율 매매를 보류합니다.", user.getEmail());
                    continue;
                }

                // 3. 5종 코인 중 랜덤으로 1종의 예측 대상 선정
                int randIndex = (int) (Math.random() * stocks.size());
                Stock targetStock = stocks.get(randIndex);
                String ticker = targetStock.getTicker();

                // 4. AI 분석 지침 호출
                AiPredictionResponse prediction = autoTradingService.getAiPrediction(user.getEmail(), ticker);
                if (prediction == null || "ERROR".equals(prediction.getPrediction())) {
                    continue;
                }

                String action = prediction.getAction();
                double confidence = prediction.getConfidence();

                // 5. 신뢰도가 90% 이상이고 매수/매도 포지션 진입일 때만 실행
                if (confidence >= 90.0 && (action.contains("LONG") || action.contains("SHORT"))) {
                    log.info("AI 에이전트 매매 신호 포착! 종목: {}, 포지션: {}, 신뢰도: {}%, 분석 사유: {}",
                            ticker, action, confidence, prediction.getReason());

                    // 진입 투자 증거금 설정 (200만 원 고정 또는 자산의 20% 중 작은 값)
                    BigDecimal investMargin = new BigDecimal("2000000");
                    if (wallet.getBalance().compareTo(investMargin) < 0) {
                        investMargin = wallet.getBalance().multiply(new BigDecimal("0.9"));
                    }

                    int leverage = (prediction.getRecommendedLeverage() != null) ? prediction.getRecommendedLeverage() : 10;
                    double currentPrice = targetStock.getCurrentPrice().doubleValue();
                    if (currentPrice <= 0) {
                        continue;
                    }

                    // 수량 계산 = (증거금 * 레버리지) / 현재가
                    double quantity = (investMargin.doubleValue() * leverage) / currentPrice;
                    // 소수점 4자리 밑으로 버림 처리
                    quantity = Math.floor(quantity * 10000) / 10000.0;

                    if (quantity <= 0.0) {
                        continue;
                    }

                    TradeRequest tradeRequest = new TradeRequest(ticker, quantity, leverage, null, "MARKET");

                    // 6. 포지션 강제 자동 진입
                    if (action.contains("LONG")) {
                        tradeService.buyStock(user.getEmail(), tradeRequest);
                    } else if (action.contains("SHORT")) {
                        tradeService.sellStock(user.getEmail(), tradeRequest);
                    }

                    // 7. 진입 완료된 포지션에 AI 추천 익절가 및 손절가 주입
                    PositionType pType = action.contains("LONG") ? PositionType.LONG : PositionType.SHORT;
                    Position position = positionRepository.findByUserIdAndStockIdAndPositionType(user.getId(), targetStock.getId(), pType)
                            .orElse(null);

                    if (position != null) {
                        // AI 예측 응답에서 반환된 원화 가격
                        Double tpPrice = prediction.getTargetPrice() != null ? prediction.getTargetPrice().doubleValue() : null;
                        Double slPrice = prediction.getStopLossPrice() != null ? prediction.getStopLossPrice().doubleValue() : null;
                        
                        position.updateTpSl(tpPrice, slPrice);
                        positionRepository.save(position);

                        log.info("AI 에이전트 자율 매매 집행 완료! 포지션ID: {}, 진입평단가: {}, 익절가: {}, 손절가: {}",
                                position.getId(), position.getEntryPrice(), tpPrice, slPrice);
                    }
                }
            } catch (Exception e) {
                log.error("사용자 {} 의 AI 자율 트레이딩 에이전트 수행 에러: {}", user.getEmail(), e.getMessage(), e);
            }
        }
    }
}
