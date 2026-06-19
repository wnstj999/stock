package com.mock.stock.domain.user.controller;

import com.mock.stock.domain.user.dto.RankResponse;
import com.mock.stock.domain.user.service.RankService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
public class RankController {
    private final RankService rankService;

    @GetMapping
    public ResponseEntity<List<RankResponse>> getRanking() {
        return ResponseEntity.ok(rankService.getTop10Ranking());
    }
}
