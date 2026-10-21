package com.example.bulletinboard.auth;

import com.example.bulletinboard.account.AccountService;
import com.example.bulletinboard.account.UserAccount;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sign-up body (FR1.1-FR1.5). The email may carry surrounding whitespace, which is removed before it
 * is compared (FR1.2), so its format check tolerates it; {@link AccountService} applies every rule
 * again on the trimmed values before hashing, including the 254-character email limit and the 72-byte
 * password limit.
 */
public record SignupRequest(
        @NotBlank @Pattern(regexp = "\\s*" + AccountService.EMAIL_REGEX + "\\s*", message = "must be a valid email address")
        String email,
        @NotBlank @Size(max = UserAccount.DISPLAY_NAME_MAX_LENGTH) String displayName,
        @NotNull @Size(min = AccountService.PASSWORD_MIN_LENGTH, max = AccountService.PASSWORD_MAX_LENGTH) String password
) {

    @Override
    public String toString() {
        // Never print the password or the email (NFR1.2, NFR2.2)
        return "SignupRequest[...]";
    }
}
