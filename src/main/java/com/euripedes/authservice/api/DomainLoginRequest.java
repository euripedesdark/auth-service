package com.euripedes.authservice.api;

import jakarta.validation.constraints.NotBlank;

public record DomainLoginRequest(@NotBlank String username, @NotBlank String password,
                                 @NotBlank String domain) {}
