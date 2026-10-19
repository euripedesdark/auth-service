package com.authservice.authservice.contract;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(@NotBlank String username,@NotBlank String password,@NotBlank String provider){}
