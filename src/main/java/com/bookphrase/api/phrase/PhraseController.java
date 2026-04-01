package com.bookphrase.api.phrase;

import com.bookphrase.api.phrase.dto.PhraseFeedResponse;
import com.bookphrase.api.phrase.dto.PhraseRevealResponse;
import com.bookphrase.domain.phrase.service.PhraseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Phrase", description = "문구 카드 API")
@RestController
@RequestMapping("/api/v1/phrases")
@RequiredArgsConstructor
public class PhraseController {

    private final PhraseService phraseService;

    @Operation(summary = "카드 피드 조회", description = "선택한 태그 기반으로 문구 카드 10장 반환. 로그인 시 이미 본 카드 제외.")
    @GetMapping("/feed")
    public ResponseEntity<List<PhraseFeedResponse>> getFeed(
            @RequestParam(required = false) Long tagId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = userDetails != null ? Long.parseLong(userDetails.getUsername()) : null;
        return ResponseEntity.ok(phraseService.getFeed(tagId, userId));
    }

    @Operation(summary = "카드 탭 → 책 공개 (Reveal)", description = "카드 탭 시 책 정보를 서버에서 응답.")
    @GetMapping("/{id}/reveal")
    public ResponseEntity<PhraseRevealResponse> reveal(@PathVariable Long id) {
        return ResponseEntity.ok(phraseService.reveal(id));
    }

    @Operation(summary = "카드 조회 기록", description = "카드 조회 시 호출. UserHistory에 기록되어 이후 피드에서 제외됨.")
    @PostMapping("/{id}/view")
    public ResponseEntity<Void> recordView(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        phraseService.recordView(id, userId);
        return ResponseEntity.ok().build();
    }
}
