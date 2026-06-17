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
public class UserStock extends BaseTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private Long quantity; // 보유 수량

    @Column(nullable = false)
    private Long averagePrice; // 평단가

    public void addQuantity(Long amount, Long price) {
        long totalCost = (this.quantity * this.averagePrice) + (amount * price);
        this.quantity += amount;
        this.averagePrice = totalCost / this.quantity;
    }

    public void deductQuantity(Long amount) {
        if (this.quantity < amount) {
            throw new IllegalArgumentException("보유 수량이 부족합니다.");
        }
        this.quantity -= amount;
        if (this.quantity == 0) {
            this.averagePrice = 0L;
        }
    }
}
