package com.euripedes.authservice.service;

import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.contract.LoginRequestDto;
import com.euripedes.authservice.provider.AuthProvider;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthenticationServiceTest {
    @Test void authenticatesThroughSelectedProvider(){
        IdentityDto identity=new IdentityDto("id-1","euripedes","AD",List.of("GRP_ADMIN"));
        AuthProvider provider=new AuthProvider(){
            public String name(){return "AD";}
            public IdentityDto authenticate(LoginRequestDto r){return identity;}
            public IdentityDto resolve(String u){return identity;}
        };
        assertEquals(identity,new AuthenticationService(List.of(provider)).authenticate(new LoginRequestDto("euripedes","secret","AD")));
    }
}
