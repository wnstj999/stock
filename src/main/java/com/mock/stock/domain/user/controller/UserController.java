package com.mock.stock.domain.user.controller;

import com.mock.stock.domain.user.dto.UserInfoResponse;
import com.mock.stock.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getMyInfo(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(userService.getUserInfo(email));
    }

    @PostMapping("/ai-agent/toggle")
    public ResponseEntity<String> toggleAiAgent(Authentication authentication, @RequestBody ToggleRequest request) {
        String email = authentication.getName();
        userService.toggleAiAgent(email, request.isActive());
        return ResponseEntity.ok("AI 에이전트 상태가 업데이트되었습니다.");
    }

    @lombok.Data
    public static class ToggleRequest {
        private boolean active;
    }
}
