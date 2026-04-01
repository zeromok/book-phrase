package com.bookphrase.api.admin.dto;

import com.bookphrase.domain.book.entity.Book;

public record AdminBookResponse(
        Long id,
        String title,
        String author,
        String isbn,
        String coverImageUrl,
        String aladdinUrl,
        String yes24Url
) {
    public static AdminBookResponse from(Book book) {
        return new AdminBookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getCoverImageUrl(),
                book.getAladdinUrl(),
                book.getYes24Url()
        );
    }
}
