package com.euripedes.authservice.provider;

import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.contract.LoginRequestDto;

public interface AuthProvider {
    String name();
    IdentityDto authenticate(LoginRequestDto request);
    IdentityDto resolve(String username);
}
