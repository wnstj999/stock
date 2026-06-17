package com.mock.stock.domain.wallet.entity;

import com.mock.stock.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal balance; // 예수금 (소수점 연산 오차 방지를 위해 BigDecimal 사용)

    @Builder
    public Wallet(User user, BigDecimal balance) {
        this.user = user;
        this.balance = balance;
    }
}
