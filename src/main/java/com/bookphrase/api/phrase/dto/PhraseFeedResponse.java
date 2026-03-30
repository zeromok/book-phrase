package com.bookphrase.api.phrase.dto;

import com.bookphrase.domain.phrase.entity.Phrase;

public record PhraseFeedResponse(
        Long id,
        String text,
        boolean isBookmarked
) {
    // 찜 여부는 Service 레이어에서 판단해서 전달
    public static PhraseFeedResponse of(Phrase phrase, boolean isBookmarked) {
        return new PhraseFeedResponse(phrase.getId(), phrase.getText(), isBookmarked);
    }
}
