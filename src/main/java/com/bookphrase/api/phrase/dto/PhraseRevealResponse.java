package com.bookphrase.api.phrase.dto;

import com.bookphrase.domain.phrase.entity.Phrase;

public record PhraseRevealResponse(
        Long phraseId,
        String text,
        BookInfo book
) {
    public record BookInfo(
            Long id,
            String title,
            String author,
            String coverImageUrl,
            PurchaseLinks purchaseLinks
    ) {}

    public record PurchaseLinks(
            String aladin,
            String yes24
    ) {}

    public static PhraseRevealResponse from(Phrase phrase) {
        var book = phrase.getBook();
        return new PhraseRevealResponse(
                phrase.getId(),
                phrase.getText(),
                new BookInfo(
                        book.getId(),
                        book.getTitle(),
                        book.getAuthor(),
                        book.getCoverImageUrl(),
                        new PurchaseLinks(book.getAladdinUrl(), book.getYes24Url())
                )
        );
    }
}
