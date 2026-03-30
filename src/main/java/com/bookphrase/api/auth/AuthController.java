package com.bookphrase.api.auth;

import com.bookphrase.api.auth.dto.LoginRequest;
import com.bookphrase.api.auth.dto.LoginResponse;
import com.bookphrase.api.auth.dto.SignupRequest;
import com.bookphrase.domain.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인 후 JWT 토큰 반환")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }
}
