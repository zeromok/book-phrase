package com.bookphrase.global.config;

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

/**
 * 앱 시작 시 필수 기초 데이터를 보장합니다.
 *
 * 보장 항목:
 * 1. 어드민 계정 (admin@bookphrase.com)
 * 2. 태그 4종 (파이프라인이 Claude에게 전달하는 감성 태그)
 *
 * 책/문구는 하드코딩하지 않습니다.
 * → Admin API (POST /api/v1/admin/pipeline)로 수동 실행하거나
 * → 스케줄러(매일 KST 06:00)가 자동으로 채웁니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureAdminExists();
        ensureTagsExist();
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
