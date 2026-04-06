package com.bookphrase.global.config;

import com.bookphrase.domain.tag.entity.Tag;
import com.bookphrase.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 시작 시 필수 기초 데이터를 보장합니다.
 *
 * 보장 항목:
 * 1. 태그 4종 (파이프라인이 Claude에게 전달하는 감성 태그)
 *
 * 어드민 계정은 SecurityConfig(InMemoryUserDetailsManager)로 관리합니다.
 * 책/문구는 Admin API 또는 스케줄러(매일 KST 06:00)가 채웁니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final TagRepository tagRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureTagsExist();
    }

    private void ensureTagsExist() {
        if (tagRepository.count() > 0) {
            log.info("[DataInitializer] 태그 이미 존재. 스킵.");
            return;
        }

        tagRepository.save(Tag.builder().name("위로받고싶다").emoji("🤗").build());
        tagRepository.save(Tag.builder().name("자극받고싶다").emoji("🔥").build());
        tagRepository.save(Tag.builder().name("쉬고싶다").emoji("😴").build());
        tagRepository.save(Tag.builder().name("성장하고싶다").emoji("🌱").build());

        log.info("[DataInitializer] 태그 4종 생성 완료");
    }
}
