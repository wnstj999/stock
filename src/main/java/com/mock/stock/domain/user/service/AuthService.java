package com.mock.stock.domain.user.service;

import com.mock.stock.domain.user.dto.LoginRequest;
import com.mock.stock.domain.user.dto.SignupRequest;
import com.mock.stock.domain.user.dto.TokenResponse;
import com.mock.stock.domain.user.entity.User;
import com.mock.stock.domain.user.repository.UserRepository;
import com.mock.stock.domain.wallet.entity.Wallet;
import com.mock.stock.domain.wallet.repository.WalletRepository;
import com.mock.stock.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    // 초기 지급 가상 예수금 1,000만원
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("10000000.00");

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        // 1. 유저 생성 및 저장 (비밀번호 암호화)
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .role("ROLE_USER")
                .build();
        userRepository.save(user);

        // 2. 가상 지갑(초기 예수금 포함) 생성 및 저장
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(INITIAL_BALANCE)
                .build();
        walletRepository.save(wallet);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String token = jwtProvider.createToken(user.getEmail(), user.getRole());
        return new TokenResponse(token);
    }
}
