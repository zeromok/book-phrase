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
import com.bookphrase.infrastructure.pipeline.ContentPipelineService;
import com.bookphrase.infrastructure.pipeline.PhraseRetagService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Admin", description = "관리자 API - 책/문구 등록")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AladinApiService aladinApiService;
    private final BookRepository bookRepository;
    private final TagRepository tagRepository;
    private final PhraseRepository phraseRepository;
    private final ContentPipelineService contentPipelineService;
    private final PhraseRetagService phraseRetagService;

    // ── 책 등록 ──────────────────────────────────────────────────────────

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

    // ── 문구 등록 ─────────────────────────────────────────────────────────

    @Operation(summary = "문구 등록", description = "책 ID와 태그 ID 목록을 지정하여 문구를 등록합니다.")
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

    // ── 태그 조회 ─────────────────────────────────────────────────────────

    @Operation(summary = "태그 목록 조회")
    @GetMapping("/tags")
    public ResponseEntity<List<TagResponse>> getTags() {
        return ResponseEntity.ok(tagRepository.findAllByOrderByNameAsc().stream()
                .map(t -> new TagResponse(t.getId(), t.getName(), t.getEmoji()))
                .toList());
    }

    // ── AI 파이프라인 ─────────────────────────────────────────────────────

    @Operation(
            summary = "[자동화] 베스트셀러 파이프라인 수동 실행",
            description = """
                    전체 베스트셀러에서 신규 책을 가져와 AI가 문구+태그를 자동 생성합니다.
                    스케줄러(매일 KST 06:00)와 동일한 로직입니다.
                    maxBooks: 1~50 (기본 10)
                    """)
    @PostMapping("/pipeline")
    public ResponseEntity<Map<String, Object>> runPipeline(
            @RequestParam(defaultValue = "10") int maxBooks) {

        if (maxBooks < 1 || maxBooks > 50) {
            return ResponseEntity.badRequest().body(Map.of("error", "maxBooks는 1~50 사이여야 합니다."));
        }

        ContentPipelineService.PipelineResult result = contentPipelineService.runPipeline(maxBooks);
        return ResponseEntity.ok(toResponseMap(result, null, null, maxBooks));
    }

    @Operation(
            summary = "[초기 수집] 카테고리별 파이프라인 수동 실행",
            description = """
                    초기 데이터 수집용. 특정 카테고리 + 소스 조합으로 책을 대량 수집합니다.
                    
                    queryType 옵션:
                    - BESTSELLER   : 베스트셀러 (기본값)
                    - NEW_SPECIAL  : 화제의 신간
                    - BLOG_BEST    : 블로거 베스트 (감성 도서 비중 높음)
                    
                    주요 categoryId:
                    - null  : 전체 카테고리
                    - 1     : 소설/시/희곡
                    - 55889 : 에세이
                    - 336   : 자기계발
                    - 656   : 인문학
                    - 51    : 철학/종교
                    - 74    : 역사
                    - 798   : 사회과학
                    
                    초기 데이터 수집 권장 순서:
                    1. BESTSELLER + null (전체 베스트셀러 30권)
                    2. BLOG_BEST + 55889 (에세이 블로거 추천 20권)
                    3. BESTSELLER + 1 (소설 베스트셀러 20권)
                    4. BESTSELLER + 336 (자기계발 베스트셀러 20권)
                    5. BESTSELLER + 656 (인문학 베스트셀러 20권)
                    """)
    @PostMapping("/pipeline/category")
    public ResponseEntity<Map<String, Object>> runPipelineByCategory(
            @RequestParam(defaultValue = "BESTSELLER") AladinApiService.QueryType queryType,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "20") int maxBooks) {

        if (maxBooks < 1 || maxBooks > 50) {
            return ResponseEntity.badRequest().body(Map.of("error", "maxBooks는 1~50 사이여야 합니다."));
        }

        ContentPipelineService.PipelineResult result =
                contentPipelineService.runByCategory(queryType, categoryId, maxBooks);
        return ResponseEntity.ok(toResponseMap(result, queryType.name(), categoryId, maxBooks));
    }

    private Map<String, Object> toResponseMap(
            ContentPipelineService.PipelineResult result,
            String queryType, Integer categoryId, int maxBooks) {
        return Map.of(
                "saved",          result.saved(),
                "duplicate",      result.duplicate(),
                "keywordFiltered",result.keywordFiltered(),
                "claudeFiltered", result.claudeFiltered(),
                "error",          result.error(),
                "total",          result.total(),
                "queryType",      queryType != null ? queryType : "BESTSELLER",
                "categoryId",     categoryId != null ? categoryId : "전체",
                "maxBooks",       maxBooks
        );
    }

    public record TagResponse(Long id, String name, String emoji) {}

    // ── 재태깅 (일회성 운영 작업) ─────────────────────────────────────────────

    @Operation(
            summary = "[일회성] 모든 phrase 재태깅",
            description = """
                    모든 phrase의 태그를 새 프롬프트로 다시 부여합니다.
                    phrase 텍스트와 책 정보는 변경되지 않습니다.
                    Aladin에서 카테고리를 가져와 재태깅 정확도를 높입니다.

                    소요 시간: phrase 1건당 약 2초 (Aladin + Claude + 1초 sleep)
                    예상 비용: phrase 100건당 약 $0.05 (Claude Haiku)
                    """)
    @PostMapping("/retag-all")
    public ResponseEntity<Map<String, Object>> retagAll() {
        PhraseRetagService.RetagResult result = phraseRetagService.retagAll();
        return ResponseEntity.ok(result.toMap());
    }
}
