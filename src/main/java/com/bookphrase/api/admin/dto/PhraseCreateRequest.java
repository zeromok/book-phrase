package com.bookphrase.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PhraseCreateRequest(

        @NotBlank(message = "문구는 필수입니다.")
        String text,

        @NotNull(message = "책 ID는 필수입니다.")
        Long bookId,

        List<Long> tagIds
) {}
