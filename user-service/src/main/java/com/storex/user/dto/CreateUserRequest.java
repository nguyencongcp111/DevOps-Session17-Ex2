package com.storex.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email
) {
}
