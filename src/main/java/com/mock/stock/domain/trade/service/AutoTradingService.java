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
                "당신은 가상화폐 시장을 분석하는 최고의 수석 애널리스트입니다. 머신러닝 모델이 %s 종목에 대해 다음과 같은 예측을 내놓았습니다.\n" +
                "- ML 예측 방향: %s\n" +
                "- 예측 신뢰도(확률): %.2f%%\n" +
                "- 현재 가격: %.2f KRW\n\n" +
                "이 데이터와 당신의 최근 시장 지식을 결합하여, 유저에게 도움이 될 만한 2~3문장 길이의 명쾌하고 전문적인 분석 코멘트(한국어)를 작성해주세요.\n" +
                "반드시 JSON 형식으로 응답하세요: {\"reason\": \"<분석 코멘트>\"}",
                ticker, prediction, confidence, currentPrice
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini"); 
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "당신은 가상화폐 수석 애널리스트입니다."),
                    Map.of("role", "user", "content", prompt)
            ));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            String openAiUrl = "https://api.openai.com/v1/chat/completions";
            
            JsonNode gptResponse = restTemplate.postForObject(openAiUrl, request, JsonNode.class);
            String content = gptResponse.get("choices").get(0).get("message").get("content").asText();
            
            JsonNode decisionJson = objectMapper.readTree(content);
            String reason = decisionJson.get("reason").asText();

            return AiPredictionResponse.builder()
                    .ticker(ticker)
                    .prediction(prediction)
                    .confidence(confidence)
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
