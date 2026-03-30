package com.bookphrase.api.admin;

import com.bookphrase.api.admin.dto.BookCreateRequest;
import com.bookphrase.api.admin.dto.PhraseCreateRequest;
import com.bookphrase.domain.book.entity.Book;
import com.bookphrase.domain.book.repository.BookRepository;
import com.bookphrase.domain.phrase.entity.Phrase;
import com.bookphrase.domain.phrase.repository.PhraseRepository;
import com.bookphrase.domain.tag.entity.Tag;
import com.bookphrase.domain.tag.repository.TagRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Admin", description = "관리자 API - 콘텐츠 등록/수정/삭제")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final BookRepository bookRepository;
    private final PhraseRepository phraseRepository;
    private final TagRepository tagRepository;

    @Operation(summary = "책 등록")
    @PostMapping("/books")
    public ResponseEntity<Long> createBook(@Valid @RequestBody BookCreateRequest request) {
        Book book = Book.builder()
                .title(request.title())
                .author(request.author())
                .isbn(request.isbn())
                .coverImageUrl(request.coverImageUrl())
                .aladdinUrl(request.aladdinUrl())
                .yes24Url(request.yes24Url())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookRepository.save(book).getId());
    }

    @Operation(summary = "문구 등록")
    @PostMapping("/phrases")
    public ResponseEntity<Long> createPhrase(@Valid @RequestBody PhraseCreateRequest request) {
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new IllegalArgumentException("책을 찾을 수 없습니다."));

        Phrase phrase = Phrase.builder()
                .text(request.text())
                .book(book)
                .build();

        if (request.tagIds() != null) {
            request.tagIds().forEach(tagId -> {
                Tag tag = tagRepository.findById(tagId)
                        .orElseThrow(() -> new IllegalArgumentException("태그를 찾을 수 없습니다: " + tagId));
                phrase.addTag(tag);
            });
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(phraseRepository.save(phrase).getId());
    }

    @Operation(summary = "태그 등록")
    @PostMapping("/tags")
    public ResponseEntity<Long> createTag(
            @RequestParam String name,
            @RequestParam(required = false) String emoji) {
        Tag tag = Tag.builder().name(name).emoji(emoji).build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tagRepository.save(tag).getId());
    }

    @Operation(summary = "문구 삭제")
    @DeleteMapping("/phrases/{id}")
    public ResponseEntity<Void> deletePhrase(@PathVariable Long id) {
        phraseRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
