package com.bookphrase.domain.phrase.entity;

import com.bookphrase.domain.book.entity.Book;
import com.bookphrase.domain.tag.entity.Tag;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "phrases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "text"})
public class Phrase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    // phrase_tags 연결 테이블
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "phrase_tags",
            joinColumns = @JoinColumn(name = "phrase_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public Phrase(String text, Book book) {
        this.text = text;
        this.book = book;
    }

    public void addTag(Tag tag) {
        this.tags.add(tag);
    }
}
