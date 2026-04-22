package com.bookphrase.api.phrase;

import com.bookphrase.api.phrase.dto.PhraseFeedResponse;
import com.bookphrase.api.phrase.dto.PhraseRevealResponse;
import com.bookphrase.domain.phrase.service.PhraseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Phrase", description = "문구 카드 API")
@RestController
@RequestMapping("/api/v1/phrases")
@RequiredArgsConstructor
public class PhraseController {

    private final PhraseService phraseService;

    @Operation(summary = "카드 피드 조회", description = "seed 기반 랜덤 정렬 + 페이지네이션. seed가 같으면 같은 순서를 보장하여 중복 없는 무한 스크롤 지원.")
    @GetMapping("/feed")
    public ResponseEntity<PhraseFeedResponse> getFeed(
            @RequestParam(required = false) Long tagId,
            @RequestParam long seed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(phraseService.getFeed(tagId, seed, page, size));
    }

    @Operation(summary = "카드 탭 → 책 공개 (Reveal)", description = "카드 탭 시 책 정보를 서버에서 응답.")
    @GetMapping("/{id}/reveal")
    public ResponseEntity<PhraseRevealResponse> reveal(@PathVariable Long id) {
        return ResponseEntity.ok(phraseService.reveal(id));
    }
}
