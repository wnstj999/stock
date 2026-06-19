package com.mock.stock.domain.trade.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.stock.domain.trade.dto.TradeRequest;
import com.mock.stock.domain.user.entity.User;
import com.mock.stock.domain.user.repository.UserRepository;
import com.mock.stock.domain.wallet.entity.Wallet;
import com.mock.stock.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mock.stock.domain.trade.dto.AiPredictionResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoTradingService {

    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api-key:YOUR_OPENAI_API_KEY_HERE}")
    private String openAiApiKey;

    @Transactional(readOnly = true)
    public AiPredictionResponse getAiPrediction(String email, String ticker) {
        if (openAiApiKey == null || openAiApiKey.isEmpty() || openAiApiKey.equals("YOUR_OPENAI_API_KEY_HERE")) {
            return AiPredictionResponse.builder()
                    .ticker(ticker)
                    .prediction("ERROR")
                    .confidence(0.0)
                    .reason("오류: OpenAI API Key가 설정되지 않았습니다. application.yml에 API 키를 설정해주세요.")
                    .build();
        }

        try {
            // 1. 머신러닝 예측 데이터 가져오기 (Python ML 서버)
            String mlUrl = "http://localhost:8001/predict?ticker=" + ticker;
            JsonNode mlResult = restTemplate.getForObject(mlUrl, JsonNode.class);
            if (mlResult == null || mlResult.has("error")) {
                return AiPredictionResponse.builder()
                        .ticker(ticker)
                        .prediction("ERROR")
                        .confidence(0.0)
                        .reason("머신러닝 서버(Python)와 통신할 수 없습니다.")
                        .build();
            }

            String prediction = mlResult.get("prediction").asText();
            double confidence = mlResult.get("confidence").asDouble();
            double currentPrice = mlResult.get("current_price").asDouble();

            // 2. OpenAI에 프롬프트 전송하여 분석 사유(Reason) 생성
            String prompt = String.format(
                "당신은 가상화폐 선물시장을 분석하는 최고의 마진/선물 수석 애널리스트입니다. 머신러닝 모델이 %s 종목에 대해 다음과 같은 예측을 내놓았습니다.\n" +
                "- ML 예측 방향: %s\n" +
                "- 예측 신뢰도(확률): %.2f%%\n" +
                "- 현재 가격: %.2f KRW\n\n" +
                "이 데이터와 당신의 최근 시장 지식을 결합하여, 유저에게 다음과 같은 구체적인 마진/선물 매매 지침을 내려주세요.\n" +
                "1. action: 'STRONG_LONG', 'LONG', 'HOLD', 'SHORT', 'STRONG_SHORT' 중 하나를 선택하세요.\n" +
                "   (포지션이 없더라도 숏 포지션 진입을 고려할 수 있습니다. 숏 진입 시에는 가격이 하락하면 수익을 얻습니다.)\n" +
                "2. target_price: 이 포지션(롱 또는 숏)에 진입했을 때 얼마에 익절(Take Profit)해야 할지 목표가(KRW)를 정수로 적어주세요.\n" +
                "   - 롱 포지션(LONG, STRONG_LONG)인 경우: 반드시 현재가(%.2f)보다 높은 가격이어야 합니다. (예: 현재가 대비 +2%% ~ +10%% 사이)\n" +
                "   - 숏 포지션(SHORT, STRONG_SHORT)인 경우: 반드시 현재가(%.2f)보다 낮은 가격이어야 합니다. (예: 현재가 대비 -2%% ~ -10%% 사이)\n" +
                "   - HOLD인 경우 0\n" +
                "3. stop_loss: 이 포지션에 진입했을 때 얼마에 손절(Stop Loss)해야 할지 손절가(KRW)를 정수로 적어주세요.\n" +
                "   - 롱 포지션(LONG, STRONG_LONG)인 경우: 반드시 현재가(%.2f)보다 낮은 가격이어야 합니다. (예: 현재가 대비 -1%% ~ -5%% 사이)\n" +
                "   - 숏 포지션(SHORT, STRONG_SHORT)인 경우: 반드시 현재가(%.2f)보다 높은 가격이어야 합니다. (예: 현재가 대비 +1%% ~ +5%% 사이)\n" +
                "   - HOLD인 경우 0\n" +
                "   [수학적 정합성 규칙]: 절대로 롱 예측 시 현재가보다 낮은 목표가를 주거나, 숏 예측 시 현재가보다 높은 목표가를 주지 마세요. 무조건 이 산술적 원칙을 지켜야 합니다.\n" +
                "4. recommended_leverage: 이 포지션 진입에 적합한 추천 레버리지 배수(정수 1, 5, 10, 25, 50 중 하나)를 선택해 주세요. 시장 변동성과 리스크에 맞춰 적절하게 결정해야 합니다.\n" +
                "5. reason: 왜 이런 레버리지 포지션 지침과 가격을 설정했는지 2~3문장 길이의 명쾌하고 전문적인 분석 코멘트(한국어)를 작성해주세요.\n\n" +
                "반드시 아래 JSON 형식으로만 응답하세요:\n" +
                "{\"action\": \"<action>\", \"target_price\": <number>, \"stop_loss\": <number>, \"recommended_leverage\": <number>, \"reason\": \"<분석 코멘트>\"}",
                ticker, prediction, confidence, currentPrice, currentPrice, currentPrice, currentPrice, currentPrice
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini"); 
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "당신은 가상화폐 선물 거래 수석 애널리스트입니다."),
                    Map.of("role", "user", "content", prompt)
            ));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String openAiUrl = "https://api.openai.com/v1/chat/completions";
            
            JsonNode gptResponse = restTemplate.postForObject(openAiUrl, request, JsonNode.class);
            String content = gptResponse.get("choices").get(0).get("message").get("content").asText();
            
            JsonNode decisionJson = objectMapper.readTree(content);
            String action = decisionJson.has("action") ? decisionJson.get("action").asText() : "HOLD";
            Long targetPrice = decisionJson.has("target_price") ? decisionJson.get("target_price").asLong() : 0L;
            Long stopLossPrice = decisionJson.has("stop_loss") ? decisionJson.get("stop_loss").asLong() : 0L;
            Integer recommendedLeverage = decisionJson.has("recommended_leverage") ? decisionJson.get("recommended_leverage").asInt() : 1;
            String reason = decisionJson.has("reason") ? decisionJson.get("reason").asText() : "";

            // 인공지능 예측값의 수학적 정합성을 강제 보증하는 백엔드 방어 필터 (Validation Guard)
            long currentPriceRound = Math.round(currentPrice);
            if (action.contains("LONG")) {
                // 롱 포지션인데 목표가가 현재가보다 같거나 낮으면 강제 5% 익절가로 보정
                if (targetPrice <= currentPriceRound) {
                    targetPrice = Math.round(currentPrice * 1.05);
                }
                // 롱 포지션인데 손절가가 현재가보다 같거나 높으면 강제 3% 손절가로 보정
                if (stopLossPrice >= currentPriceRound || stopLossPrice <= 0) {
                    stopLossPrice = Math.round(currentPrice * 0.97);
                }
            } else if (action.contains("SHORT")) {
                // 숏 포지션인데 목표가가 현재가보다 같거나 높으면 강제 5% 익절가로 보정
                if (targetPrice >= currentPriceRound || targetPrice <= 0) {
                    targetPrice = Math.round(currentPrice * 0.95);
                }
                // 숏 포지션인데 손절가가 현재가보다 같거나 낮으면 강제 3% 손절가로 보정
                if (stopLossPrice <= currentPriceRound) {
                    stopLossPrice = Math.round(currentPrice * 1.03);
                }
            } else {
                targetPrice = 0L;
                stopLossPrice = 0L;
            }

            return AiPredictionResponse.builder()
                    .ticker(ticker)
                    .prediction(prediction)
                    .confidence(confidence)
                    .action(action)
                    .targetPrice(targetPrice)
                    .stopLossPrice(stopLossPrice)
                    .recommendedLeverage(recommendedLeverage)
                    .reason(reason)
                    .build();


        } catch (Exception e) {
            log.error("AI Prediction Error", e);
            return AiPredictionResponse.builder()
                    .ticker(ticker)
                    .prediction("ERROR")
                    .confidence(0.0)
                    .reason("AI 분석 처리 중 서버 오류가 발생했습니다: " + e.getMessage())
                    .build();
        }
    }
}
