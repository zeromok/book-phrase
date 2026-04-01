package com.bookphrase.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminBookRequest(
        @NotBlank(message = "ISBN은 필수입니다.")
        String isbn,

        String yes24Url
) {}
