package com.mock.stock.domain.user.service;

import com.mock.stock.domain.trade.entity.UserStock;
import com.mock.stock.domain.trade.repository.UserStockRepository;
import com.mock.stock.domain.user.dto.UserInfoResponse;
import com.mock.stock.domain.user.entity.User;
import com.mock.stock.domain.user.repository.UserRepository;
import com.mock.stock.domain.wallet.entity.Wallet;
import com.mock.stock.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final UserStockRepository userStockRepository;

    @Transactional(readOnly = true)
    public UserInfoResponse getUserInfo(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Long userId = user.getId();
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("지갑을 찾을 수 없습니다."));
        List<UserStock> holdings = userStockRepository.findByUserId(userId);

        List<UserInfoResponse.UserStockDto> holdingDtos = holdings.stream()
                .map(us -> UserInfoResponse.UserStockDto.builder()
                        .ticker(us.getStock().getTicker())
                        .companyName(us.getStock().getCompanyName())
                        .quantity(us.getQuantity())
                        .averagePrice(us.getAveragePrice())
                        .currentPrice(0L) // 실시간 가격은 프론트엔드가 알아서 렌더링
                        .build())
                .collect(Collectors.toList());

        return UserInfoResponse.builder()
                .email(user.getEmail())
                .nickname(user.getNickname())
                .walletBalance(wallet.getBalance())
                .holdings(holdingDtos)
                .build();
    }
}
