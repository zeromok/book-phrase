package com.bookphrase.api.bookmark;

import com.bookphrase.api.bookmark.dto.BookmarkResponse;
import com.bookphrase.domain.bookmark.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Bookmark", description = "찜하기 API")
@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "찜 추가")
    @PostMapping("/{phraseId}")
    public ResponseEntity<Void> addBookmark(
            @PathVariable Long phraseId,
            @AuthenticationPrincipal UserDetails userDetails) {
        bookmarkService.addBookmark(Long.parseLong(userDetails.getUsername()), phraseId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "찜 해제")
    @DeleteMapping("/{phraseId}")
    public ResponseEntity<Void> removeBookmark(
            @PathVariable Long phraseId,
            @AuthenticationPrincipal UserDetails userDetails) {
        bookmarkService.removeBookmark(Long.parseLong(userDetails.getUsername()), phraseId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 찜 목록 조회")
    @GetMapping
    public ResponseEntity<List<BookmarkResponse>> getBookmarks(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                bookmarkService.getBookmarks(Long.parseLong(userDetails.getUsername()))
        );
    }
}
