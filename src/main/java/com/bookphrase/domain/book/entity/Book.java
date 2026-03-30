package com.bookphrase.domain.book.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "books")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "title", "author"})
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(unique = true, length = 20)
    private String isbn;

    @Column(columnDefinition = "TEXT")
    private String coverImageUrl;

    @Column(columnDefinition = "TEXT")
    private String aladdinUrl;

    @Column(columnDefinition = "TEXT")
    private String yes24Url;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public Book(String title, String author, String isbn,
                String coverImageUrl, String aladdinUrl, String yes24Url) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.coverImageUrl = coverImageUrl;
        this.aladdinUrl = aladdinUrl;
        this.yes24Url = yes24Url;
    }
}
