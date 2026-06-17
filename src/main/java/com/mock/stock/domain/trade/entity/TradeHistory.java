package com.mock.stock.domain.trade.entity;

import com.mock.stock.domain.stock.entity.Stock;
import com.mock.stock.domain.user.entity.User;
import com.mock.stock.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false, length = 10)
    private String tradeType; // BUY or SELL

    @Column(nullable = false)
    private Long price; // 체결가

    @Column(nullable = false)
    private Long quantity; // 체결 수량
}
