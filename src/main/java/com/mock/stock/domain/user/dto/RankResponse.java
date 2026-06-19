package com.mock.stock.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RankResponse {
    private String nickname;
    private Long totalAsset;
    private Long rank;
}
