package com.euripedes.authservice.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class DomainAuthServiceTest {
    @Test void baseDnFromDomain() {
        assertEquals("dc=srvcloud,dc=cloud",
            DomainAuthService.toBaseDn("srvcloud.cloud"));
        assertEquals("dc=outro,dc=dominio,dc=local",
            DomainAuthService.toBaseDn("Outro.Dominio.Local"));
    }

    @Test void rejectsBlankDomain() {
        assertThrows(IllegalArgumentException.class,
            () -> DomainAuthService.toBaseDn("   "));
    }
}
