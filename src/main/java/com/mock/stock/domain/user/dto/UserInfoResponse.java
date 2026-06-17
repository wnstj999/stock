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
    private List<UserStockDto> holdings;

    @Data
    @Builder
    public static class UserStockDto {
        private String ticker;
        private String companyName;
        private Long quantity;
        private Long averagePrice;
        private Long currentPrice;
    }
}
