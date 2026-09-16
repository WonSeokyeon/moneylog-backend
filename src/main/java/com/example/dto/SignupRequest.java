package com.example.dto;

import com.example.dto.validation.MaxByteLength;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email @Size(max = 255) String email,

        @NotBlank
        @Size(min = 6, message = "비밀번호는 6자 이상이어야 합니다.")
        @MaxByteLength(value = 72, message = "비밀번호가 너무 깁니다. (한글은 1자가 3바이트로 계산됩니다)")
        String password,

        @NotBlank @Size(min = 1, max = 50) String nickname
) {
}
