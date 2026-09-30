package com.euripedes.authservice.service;

import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.contract.LoginRequestDto;
import com.euripedes.authservice.provider.AuthProvider;
import com.euripedes.authservice.provider.ProviderUnavailableException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Locale;

@Service
public class AuthenticationService {
    private final List<AuthProvider> providers;
    public AuthenticationService(List<AuthProvider> providers){this.providers=providers;}
    public IdentityDto authenticate(LoginRequestDto request){return provider(request.provider()).authenticate(request);}
    public IdentityDto resolve(Authentication authentication){
        if(authentication==null||!authentication.isAuthenticated())throw new IllegalStateException("No authenticated identity");
        return provider("AD").resolve(authentication.getName());
    }
    private AuthProvider provider(String name){
        return providers.stream().filter(p->p.name().equalsIgnoreCase(name)).findFirst()
            .orElseThrow(()->new ProviderUnavailableException(name.toUpperCase(Locale.ROOT)));
    }
}
