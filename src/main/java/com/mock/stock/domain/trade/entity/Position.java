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
@Table(name = "user_position") // 기존 테이블과의 간섭 최소화를 위해 user_position으로 명명
public class Position extends BaseTimeEntity {

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
    private Double quantity; // 포지션 수량 (소수점 지원을 위해 Double)

    @Column(nullable = false)
    private Double entryPrice; // 진입 평단가

    @Column(nullable = false)
    private Integer leverage; // 레버리지 배수

    @Column(nullable = false)
    private Double margin; // 증거금 = (entryPrice * quantity) / leverage

    @Column(nullable = true)
    private Double takeProfitPrice; // 익절 가격 (Take Profit)

    @Column(nullable = true)
    private Double stopLossPrice; // 손절 가격 (Stop Loss)

    public void updateTpSl(Double takeProfitPrice, Double stopLossPrice) {
        this.takeProfitPrice = takeProfitPrice;
        this.stopLossPrice = stopLossPrice;
    }

    public void addQuantity(Double amount, Double price) {
        double totalCost = (this.quantity * this.entryPrice) + (amount * price);
        this.quantity += amount;
        this.entryPrice = totalCost / this.quantity;
        // 추가 매수한 만큼 증거금 누적 가산
        this.margin += (amount * price) / this.leverage;
    }

    public void deductQuantity(Double amount) {
        if (this.quantity < amount) {
            throw new IllegalArgumentException("보유한 포지션 수량이 부족합니다.");
        }
        double ratio = (this.quantity - amount) / this.quantity;
        this.quantity -= amount;
        this.margin = this.margin * ratio; // 수량 감소 비율에 맞춰 증거금 차감
        if (this.quantity <= 0.0) {
            this.entryPrice = 0.0;
            this.margin = 0.0;
        }
    }
}
