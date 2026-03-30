package com.bookphrase.api.bookmark.dto;

import com.bookphrase.domain.bookmark.entity.UserBookmark;

import java.time.LocalDateTime;

public record BookmarkResponse(
        Long phraseId,
        String phraseText,
        String bookTitle,
        String bookAuthor,
        LocalDateTime createdAt
) {
    public static BookmarkResponse from(UserBookmark bookmark) {
        var phrase = bookmark.getPhrase();
        var book = phrase.getBook();
        return new BookmarkResponse(
                phrase.getId(),
                phrase.getText(),
                book.getTitle(),
                book.getAuthor(),
                bookmark.getCreatedAt()
        );
    }
}
