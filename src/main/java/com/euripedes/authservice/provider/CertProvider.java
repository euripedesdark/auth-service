package com.euripedes.authservice.provider;

import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.contract.LoginRequestDto;
import org.springframework.stereotype.Component;

@Component
public class CertProvider implements AuthProvider {
    public String name(){return "CERTIFICADO";}
    public IdentityDto authenticate(LoginRequestDto r){throw new ProviderUnavailableException(name());}
    public IdentityDto resolve(String u){throw new ProviderUnavailableException(name());}
}
