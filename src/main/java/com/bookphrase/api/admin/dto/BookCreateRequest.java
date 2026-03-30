package com.bookphrase.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record BookCreateRequest(

        @NotBlank(message = "제목은 필수입니다.")
        String title,

        @NotBlank(message = "저자는 필수입니다.")
        String author,

        String isbn,
        String coverImageUrl,
        String aladdinUrl,
        String yes24Url
) {}
