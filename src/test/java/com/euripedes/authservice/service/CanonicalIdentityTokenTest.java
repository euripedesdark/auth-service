package com.euripedes.authservice.service;

import com.euripedes.authservice.config.TokenProperties;
import com.euripedes.authservice.contract.IdentityDto;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CanonicalIdentityTokenTest {
    @Test
    void rejectsIncompleteCanonicalIdentity() {
        TokenService service = new TokenService(mock(JwtEncoder.class), mock(TokenProperties.class));
        assertThrows(IllegalArgumentException.class,
            () -> service.issue(new IdentityDto(null, "euripedes", "AD", List.of())));
        assertThrows(IllegalArgumentException.class,
            () -> service.issue(new IdentityDto("123", "euripedes", "", List.of())));
    }

    @Test
    void differentProvidersCannotShareCanonicalIdentityClaims() {
        JwtEncoder encoder = mock(JwtEncoder.class);
        TokenProperties props = mock(TokenProperties.class);
        when(props.getTtlSeconds()).thenReturn(300L);
        when(props.getIssuer()).thenReturn("test");
        when(encoder.encode(any(JwtEncoderParameters.class))).thenAnswer(inv -> {
            JwtEncoderParameters p = inv.getArgument(0);
            assertEquals(p.getClaims().getSubject(), p.getClaims().getClaim("identityId"));
            assertNotNull(p.getClaims().getClaim("provider"));
            return new org.springframework.security.oauth2.jwt.Jwt(
                "signed-token", java.time.Instant.now(), java.time.Instant.now().plusSeconds(300),
                java.util.Map.of("alg", "HS256"), p.getClaims().getClaims());
        });
        TokenService service = new TokenService(encoder, props);
        service.issue(new IdentityDto("guid-ad", "euripedes", "AD", List.of()));
        service.issue(new IdentityDto("id-pg", "euripedes", "POSTGRES", List.of()));
        verify(encoder, times(2)).encode(any(JwtEncoderParameters.class));
    }
}
