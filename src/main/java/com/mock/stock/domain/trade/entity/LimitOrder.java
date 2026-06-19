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
@Table(name = "limit_order")
public class LimitOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PositionType positionType; // LONG or SHORT

    @Column(nullable = false)
    private Double price; // 지정가

    @Column(nullable = false)
    private Double quantity; // 주문 계약 수량

    @Column(nullable = false)
    private Integer leverage; // 주문 레버리지

    @Column(nullable = false)
    private Double margin; // 주문 증거금 락 = (price * quantity) / leverage

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OrderStatus status; // PENDING, FILLED, CANCELED

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }
}
