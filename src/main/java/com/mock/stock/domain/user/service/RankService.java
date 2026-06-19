package com.mock.stock.domain.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mock.stock.domain.stock.entity.Stock;
import com.mock.stock.domain.stock.repository.StockRepository;
import com.mock.stock.domain.user.dto.RankResponse;
import com.mock.stock.domain.user.entity.User;
import com.mock.stock.domain.trade.entity.UserStock;
import com.mock.stock.domain.user.repository.UserRepository;
import com.mock.stock.domain.trade.repository.UserStockRepository;
import com.mock.stock.domain.wallet.entity.Wallet;
import com.mock.stock.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final UserStockRepository userStockRepository;
    private final StockRepository stockRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional(readOnly = true)
    public List<RankResponse> getTop10Ranking() {
        // 1. Get current prices for all stocks
        List<Stock> allStocks = stockRepository.findAll();
        Map<Long, Long> currentPrices = new HashMap<>();
        
        if (!allStocks.isEmpty()) {
            String markets = allStocks.stream()
                    .map(Stock::getTicker)
                    .collect(Collectors.joining(","));
            
            try {
                String upbitUrl = "https://api.upbit.com/v1/ticker?markets=" + markets;
                JsonNode[] response = restTemplate.getForObject(upbitUrl, JsonNode[].class);
                if (response != null) {
                    for (JsonNode node : response) {
                        String ticker = node.get("market").asText();
                        long price = (long) node.get("trade_price").asDouble();
                        allStocks.stream().filter(s -> s.getTicker().equals(ticker))
                                .findFirst().ifPresent(s -> currentPrices.put(s.getId(), price));
                    }
                }
            } catch (Exception e) {
                // Fallback to average price if API fails
            }
        }

        // 2. Calculate total asset for all users
        List<User> users = userRepository.findAll();
        List<RankResponse> rankings = new ArrayList<>();

        for (User user : users) {
            Wallet wallet = walletRepository.findByUserId(user.getId()).orElse(null);
            long balance = wallet != null ? wallet.getBalance().longValue() : 0L;
            
            long stocksValue = 0L;
            List<UserStock> userStocks = userStockRepository.findByUserId(user.getId());
            for (UserStock us : userStocks) {
                Long currentPrice = currentPrices.get(us.getStock().getId());
                if (currentPrice == null) {
                    currentPrice = us.getAveragePrice();
                }
                stocksValue += us.getQuantity() * currentPrice;
            }
            
            rankings.add(RankResponse.builder()
                    .nickname(user.getNickname())
                    .totalAsset(balance + stocksValue)
                    .build());
        }

        // 3. Sort and assign rank
        rankings.sort((r1, r2) -> r2.getTotalAsset().compareTo(r1.getTotalAsset()));
        
        List<RankResponse> top10 = new ArrayList<>();
        long rank = 1;
        for (int i = 0; i < Math.min(10, rankings.size()); i++) {
            RankResponse r = rankings.get(i);
            top10.add(RankResponse.builder()
                    .nickname(r.getNickname())
                    .totalAsset(r.getTotalAsset())
                    .rank(rank++)
                    .build());
        }
        return top10;
    }
}
