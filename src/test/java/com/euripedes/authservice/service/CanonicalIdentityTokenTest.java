package com.euripedes.authservice.service;

import com.euripedes.authservice.config.TokenProperties;
import com.euripedes.authservice.contract.IdentityDto;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import com.euripedes.authservice.config.ApiTokenEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import static org.mockito.Mockito.*;

class CanonicalIdentityTokenTest {
    @Test
    void rejectsIncompleteCanonicalIdentity() {
        TokenService service = new TokenService(mock(ApiTokenEncoder.class), mock(TokenProperties.class));
        assertThrows(IllegalArgumentException.class,
            () -> service.issue(new IdentityDto(null, "euripedes", "AD", List.of())));
        assertThrows(IllegalArgumentException.class,
            () -> service.issue(new IdentityDto("123", "euripedes", "", List.of())));
    }

    @Test
    void differentProvidersCannotShareCanonicalIdentityClaims() {
        ApiTokenEncoder encoder = mock(ApiTokenEncoder.class);
        TokenProperties props = mock(TokenProperties.class);
        when(props.getTtlSeconds()).thenReturn(300L);
        when(props.getIssuer()).thenReturn("test");
        when(encoder.encode(any(JwsHeader.class), any(JwtClaimsSet.class))).thenAnswer(inv -> {
            JwtClaimsSet c = inv.getArgument(1);
            assertEquals(c.getSubject(), c.getClaim("identityId"));
            assertNotNull(c.getClaim("provider"));
            return "signed-token";
        });
        TokenService service = new TokenService(encoder, props);
        service.issue(new IdentityDto("guid-ad", "euripedes", "AD", List.of()));
        service.issue(new IdentityDto("id-pg", "euripedes", "POSTGRES", List.of()));
        verify(encoder, times(2)).encode(any(JwsHeader.class), any(JwtClaimsSet.class));
    }
}
