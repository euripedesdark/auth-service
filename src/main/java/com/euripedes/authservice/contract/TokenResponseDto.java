package com.euripedes.authservice.contract;

public record TokenResponseDto(String accessToken,String tokenType,long expiresIn,IdentityDto identity){}
