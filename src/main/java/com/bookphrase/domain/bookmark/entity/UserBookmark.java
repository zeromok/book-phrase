package com.bookphrase.domain.bookmark.entity;

import com.bookphrase.domain.phrase.entity.Phrase;
import com.bookphrase.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_bookmarks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "phrase_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phrase_id", nullable = false)
    private Phrase phrase;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public UserBookmark(User user, Phrase phrase) {
        this.user = user;
        this.phrase = phrase;
    }
}
