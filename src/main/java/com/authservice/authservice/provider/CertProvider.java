package com.authservice.authservice.provider;

import com.authservice.authservice.contract.IdentityDto;
import com.authservice.authservice.contract.LoginRequestDto;
import org.springframework.stereotype.Component;

@Component
public class CertProvider implements AuthProvider {
    public String name(){return "CERTIFICADO";}
    public IdentityDto authenticate(LoginRequestDto r){throw new ProviderUnavailableException(name());}
    public IdentityDto resolve(String u){throw new ProviderUnavailableException(name());}
}
