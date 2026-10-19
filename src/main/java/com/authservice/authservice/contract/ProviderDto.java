package com.authservice.authservice.contract;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name="Provider")
public record ProviderDto(String provider){}
