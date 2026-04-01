package com.bookphrase.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AdminPhraseRequest(
        @NotBlank(message = "문구 내용은 필수입니다.")
        String text,

        @NotNull(message = "책 ID는 필수입니다.")
        Long bookId,

        @NotEmpty(message = "태그는 최소 1개 이상 선택해야 합니다.")
        List<Long> tagIds
) {}
