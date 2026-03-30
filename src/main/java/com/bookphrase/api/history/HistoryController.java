package com.bookphrase.api.history;

import com.bookphrase.domain.phrase.entity.UserHistory;
import com.bookphrase.domain.phrase.repository.UserHistoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "History", description = "본 카드 히스토리 API")
@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
public class HistoryController {

    private final UserHistoryRepository userHistoryRepository;

    @Operation(summary = "내가 본 카드 목록", description = "최근 본 순서로 반환")
    @GetMapping
    public ResponseEntity<List<HistoryResponse>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        List<HistoryResponse> history = userHistoryRepository
                .findByUserIdOrderByViewedAtDesc(userId)
                .stream()
                .map(HistoryResponse::from)
                .toList();
        return ResponseEntity.ok(history);
    }

    public record HistoryResponse(
            Long phraseId,
            String phraseText,
            String bookTitle,
            String bookAuthor,
            LocalDateTime viewedAt
    ) {
        public static HistoryResponse from(UserHistory h) {
            var phrase = h.getPhrase();
            var book = phrase.getBook();
            return new HistoryResponse(
                    phrase.getId(),
                    phrase.getText(),
                    book.getTitle(),
                    book.getAuthor(),
                    h.getViewedAt()
            );
        }
    }
}
