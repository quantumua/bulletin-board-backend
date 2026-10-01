package com.example.bulletinboard.ad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdRequest(
        @NotBlank @Size(max = TITLE_MAX_LENGTH) String title,
        @NotBlank @Size(max = DESCRIPTION_MAX_LENGTH) String description
) {
    public static final int TITLE_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;
}
