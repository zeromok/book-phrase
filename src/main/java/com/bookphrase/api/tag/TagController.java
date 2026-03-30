package com.bookphrase.api.tag;

import com.bookphrase.domain.tag.repository.TagRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Tag", description = "태그(기분) API")
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagRepository tagRepository;

    @Operation(summary = "전체 태그 목록 조회", description = "오늘 기분은? 화면에서 사용")
    @GetMapping
    public ResponseEntity<List<TagResponse>> getTags() {
        List<TagResponse> tags = tagRepository.findAllByOrderByNameAsc()
                .stream()
                .map(TagResponse::from)
                .toList();
        return ResponseEntity.ok(tags);
    }

    public record TagResponse(Long id, String name, String emoji) {
        public static TagResponse from(com.bookphrase.domain.tag.entity.Tag tag) {
            return new TagResponse(tag.getId(), tag.getName(), tag.getEmoji());
        }
    }
}
