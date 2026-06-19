package com.mock.stock.domain.user.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class UserInfoResponse {
    private String email;
    private String nickname;
    private BigDecimal walletBalance;
    private BigDecimal lockedBalance; 
    private List<PositionDto> holdings;
    private List<com.mock.stock.domain.trade.dto.LimitOrderResponse> pendingOrders; 


    @Data
    @Builder
    public static class PositionDto {
        private Long id;
        private String ticker;
        private String companyName;
        private String positionType;
        private Double quantity;
        private Double entryPrice;
        private Double currentPrice;
        private Integer leverage;
        private Double margin;
        private Double unrealizedPnl;
        private Double pnlRate;
        private Double takeProfitPrice;
        private Double stopLossPrice;
    }
}

