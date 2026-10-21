package com.example.bulletinboard.ad;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of create and edit requests. Unknown fields, such as an owner, are ignored: the owner is never
 * taken from the request body (FR4.2, AC4.7).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdRequest(
        @NotBlank @Size(max = TITLE_MAX_LENGTH) String title,
        @NotBlank @Size(max = DESCRIPTION_MAX_LENGTH) String description
) {
    public static final int TITLE_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;
}
