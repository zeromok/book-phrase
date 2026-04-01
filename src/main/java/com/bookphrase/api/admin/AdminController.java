package com.bookphrase.api.admin;

import com.bookphrase.api.admin.dto.AdminBookRequest;
import com.bookphrase.api.admin.dto.AdminBookResponse;
import com.bookphrase.api.admin.dto.AdminPhraseRequest;
import com.bookphrase.domain.book.entity.Book;
import com.bookphrase.domain.book.repository.BookRepository;
import com.bookphrase.domain.phrase.entity.Phrase;
import com.bookphrase.domain.phrase.repository.PhraseRepository;
import com.bookphrase.domain.tag.entity.Tag;
import com.bookphrase.domain.tag.repository.TagRepository;
import com.bookphrase.infrastructure.aladin.AladinApiService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Admin", description = "관리자 API - 책/문구 등록")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AladinApiService aladinApiService;
    private final BookRepository bookRepository;
    private final TagRepository tagRepository;
    private final PhraseRepository phraseRepository;

    @Operation(summary = "ISBN으로 책 등록", description = "알라딘 API를 통해 책 정보를 자동으로 가져와 등록합니다. yes24Url은 선택 입력.")
    @PostMapping("/books")
    public ResponseEntity<AdminBookResponse> addBook(@RequestBody @Valid AdminBookRequest request) {
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new IllegalArgumentException("이미 등록된 ISBN입니다: " + request.isbn());
        }

        AladinApiService.AladinBookInfo info = aladinApiService.fetchByIsbn(request.isbn());

        Book book = bookRepository.save(Book.builder()
                .title(info.title())
                .author(info.author())
                .isbn(request.isbn())
                .coverImageUrl(info.cover())
                .aladdinUrl(info.link())
                .yes24Url(request.yes24Url())
                .build());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminBookResponse.from(book));
    }

    @Operation(summary = "등록된 책 목록 조회")
    @GetMapping("/books")
    public ResponseEntity<List<AdminBookResponse>> getBooks() {
        return ResponseEntity.ok(bookRepository.findAll().stream()
                .map(AdminBookResponse::from)
                .toList());
    }

    @Operation(summary = "문구 등록", description = "책 ID와 태그 ID 목록을 지정하여 문구를 등록합니다. GET /admin/tags 로 태그 ID 확인 후 사용.")
    @PostMapping("/phrases")
    public ResponseEntity<Void> addPhrase(@RequestBody @Valid AdminPhraseRequest request) {
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다: " + request.bookId()));

        List<Tag> tags = tagRepository.findAllById(request.tagIds());

        Phrase phrase = Phrase.builder()
                .text(request.text())
                .book(book)
                .build();

        tags.forEach(phrase::addTag);

        phraseRepository.save(phrase);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "태그 목록 조회", description = "문구 등록 시 사용할 태그 ID 확인용")
    @GetMapping("/tags")
    public ResponseEntity<List<TagResponse>> getTags() {
        return ResponseEntity.ok(tagRepository.findAllByOrderByNameAsc().stream()
                .map(t -> new TagResponse(t.getId(), t.getName(), t.getEmoji()))
                .toList());
    }

    public record TagResponse(Long id, String name, String emoji) {}
}
