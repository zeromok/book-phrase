package com.bookphrase.api.phrase.dto;

import java.util.List;

public record PhraseFeedResponse(
        List<PhraseItem> phrases,
        boolean hasNext
) {
    public record PhraseItem(
            Long id,
            String text
    ) {}
}
