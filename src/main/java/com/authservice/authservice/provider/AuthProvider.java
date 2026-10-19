package com.authservice.authservice.provider;

import com.authservice.authservice.contract.IdentityDto;
import com.authservice.authservice.contract.LoginRequestDto;

public interface AuthProvider {
    String name();
    IdentityDto authenticate(LoginRequestDto request);
    IdentityDto resolve(String username);
}
