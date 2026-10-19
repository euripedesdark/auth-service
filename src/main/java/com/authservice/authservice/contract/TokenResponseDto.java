package com.authservice.authservice.contract;

public record TokenResponseDto(String accessToken,String tokenType,long expiresIn,IdentityDto identity){}
