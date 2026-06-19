package com.mock.stock.domain.stock.entity;

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
public class Stock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String ticker; // 종목코드 (예: 005930)

    @Column(nullable = false, length = 50)
    private String companyName; // 기업명

    @Column(nullable = false)
    private Long currentPrice; // 현재가

    @Column(nullable = false)
    private Long previousClose; // 전일 종가 (등락률 계산용)

    // 가격 및 전일 종가 변동을 위한 도메인 로직
    public void updatePrice(Long newPrice, Long newPrevClose) {
        this.currentPrice = newPrice;
        this.previousClose = newPrevClose;
    }
}
