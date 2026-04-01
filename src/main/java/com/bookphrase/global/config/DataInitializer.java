package com.bookphrase.global.config;

import com.bookphrase.domain.book.entity.Book;
import com.bookphrase.domain.book.repository.BookRepository;
import com.bookphrase.domain.phrase.entity.Phrase;
import com.bookphrase.domain.phrase.repository.PhraseRepository;
import com.bookphrase.domain.tag.entity.Tag;
import com.bookphrase.domain.tag.repository.TagRepository;
import com.bookphrase.domain.user.entity.User;
import com.bookphrase.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final TagRepository tagRepository;
    private final BookRepository bookRepository;
    private final PhraseRepository phraseRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 항상 실행: 어드민 계정 보장
        ensureAdminExists();

        // 이미 데이터가 있으면 스킵
        if (tagRepository.count() > 0) {
            log.info("[DataInitializer] 초기 데이터 이미 존재. 스킵.");
            return;
        }

        log.info("[DataInitializer] 초기 테스트 데이터 삽입 시작");

        // 태그 생성
        Tag tagComfort  = save(Tag.builder().name("위로받고싶다").emoji("🤗").build());
        Tag tagMotivate = save(Tag.builder().name("자극받고싶다").emoji("🔥").build());
        Tag tagRest     = save(Tag.builder().name("쉬고싶다").emoji("😴").build());
        Tag tagGrow     = save(Tag.builder().name("성장하고싶다").emoji("🌱").build());

        // 책 1: 각성
        Book book1 = bookRepository.save(Book.builder()
                .title("각성")
                .author("김요한")
                .isbn("9791190382070")
                .coverImageUrl("https://image.aladin.co.kr/product/29520/97/cover/k472838022_1.jpg")
                .aladdinUrl("https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=368643292")
                .yes24Url("https://www.yes24.com/Product/Goods/149869906")
                .build());

        // 책 2: 미움받을 용기
        Book book2 = bookRepository.save(Book.builder()
                .title("미움받을 용기")
                .author("기시미 이치로, 고가 후미타케")
                .isbn("9788996991342")
                .coverImageUrl("https://image.aladin.co.kr/product/4011/21/cover/8996991341_1.jpg")
                .aladdinUrl("https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=48463031")
                .yes24Url("https://www.yes24.com/Product/Goods/116599423")
                .build());

        // 책 3: 아주 작은 습관의 힘
        Book book3 = bookRepository.save(Book.builder()
                .title("아주 작은 습관의 힘")
                .author("제임스 클리어")
                .isbn("9791162242965")
                .coverImageUrl("https://image.aladin.co.kr/product/19101/11/cover/k012634616_1.jpg")
                .aladdinUrl("https://www.aladin.co.kr/shop/wproduct.aspx?ItemId=379447436")
                .yes24Url("https://www.yes24.com/Product/Goods/69655504")
                .build());

        // 문구 등록
        createPhrase("누구든 인생을 살아주지 않는다.", book1, tagMotivate, tagGrow);
        createPhrase("멈춰야 보이는 것들이 있다.", book1, tagComfort, tagRest);
        createPhrase("지금 이 순간에도 누군가는 포기하지 않는다.", book1, tagMotivate);

        createPhrase("자유란 타인에게 미움받을 것을 두려워하지 않는 것이다.", book2, tagComfort, tagGrow);
        createPhrase("과거는 바꿀 수 없지만, 지금 이 순간의 의미는 내가 정한다.", book2, tagComfort, tagMotivate);
        createPhrase("중요한 것은 무엇이 주어졌는가가 아니라, 주어진 것을 어떻게 쓰는가다.", book2, tagGrow, tagMotivate);

        createPhrase("1% 더 나아지는 것이 쌓이면 37배가 된다.", book3, tagGrow, tagMotivate);
        createPhrase("습관은 정체성의 표현이다.", book3, tagGrow);
        createPhrase("시스템이 목표보다 중요하다.", book3, tagGrow, tagMotivate);

        log.info("[DataInitializer] 태그 {}개, 책 3개, 문구 9개 삽입 완료", tagRepository.count());
    }

    private void ensureAdminExists() {
        if (!userRepository.existsByEmail("admin@bookphrase.com")) {
            userRepository.save(User.builder()
                    .email("admin@bookphrase.com")
                    .password(passwordEncoder.encode("Admin1234!"))
                    .nickname("관리자")
                    .role("ADMIN")
                    .build());
            log.info("[DataInitializer] 어드민 계정 생성 완료");
        }
    }

    private Tag save(Tag tag) {
        return tagRepository.save(tag);
    }

    private void createPhrase(String text, Book book, Tag... tags) {
        Phrase phrase = Phrase.builder().text(text).book(book).build();
        for (Tag tag : tags) {
            phrase.addTag(tag);
        }
        phraseRepository.save(phrase);
    }
}
