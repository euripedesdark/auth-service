package com.authservice.authservice.provider;

import com.authservice.authservice.contract.IdentityDto;
import com.authservice.authservice.contract.LoginRequestDto;
import org.springframework.stereotype.Component;

@Component
public class LinuxProvider implements AuthProvider {
    public String name(){return "LINUX";}
    public IdentityDto authenticate(LoginRequestDto r){throw new ProviderUnavailableException(name());}
    public IdentityDto resolve(String u){throw new ProviderUnavailableException(name());}
}
