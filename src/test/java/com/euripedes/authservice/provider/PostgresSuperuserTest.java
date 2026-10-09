package com.euripedes.authservice.provider;

import com.euripedes.authservice.config.PgProperties;
import com.euripedes.authservice.contract.LoginRequestDto;
import java.sql.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostgresSuperuserTest {
    @ParameterizedTest @ValueSource(booleans={true,false})
    void privilegioVemDeRolsuperENaoDoNomePostgres(boolean superuser) throws Exception {
        var config = new PgProperties(); config.setUrl("jdbc:postgresql://localhost/erp");
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var result = mock(ResultSet.class);
        when(connection.isValid(3)).thenReturn(true);
        when(connection.prepareStatement("SELECT rolsuper FROM pg_catalog.pg_roles WHERE rolname = current_user")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean("rolsuper")).thenReturn(superuser);
        try (var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(eq(config.credentialUrl()), any(Properties.class)))
                    .thenReturn(connection);
            var identity = new PostgresProvider(config).authenticate(new LoginRequestDto("postgres", "teste", "POSTGRES"));
            assertEquals(superuser, identity.groups().contains("POSTGRES_SUPERUSER"));
            assertEquals("POSTGRES", identity.provider());
            verify(statement).setQueryTimeout(10);
            verify(connection).close();
        }
    }
    @Test void resolverNomeNaoInventaPrivilegio() {
        var config = new PgProperties();
        assertFalse(new PostgresProvider(config).resolve("postgres").groups().contains("POSTGRES_SUPERUSER"));
    }
    @Test void senhaRecusadaNaoGeraIdentidadeSuperuser() throws Exception {
        var config = new PgProperties(); config.setUrl("jdbc:postgresql://localhost/erp");
        SQLException refused = new SQLException("Invalid credentials");
        try (var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(anyString(), any(Properties.class)))
                    .thenThrow(refused);
            assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                    () -> new PostgresProvider(config).authenticate(new LoginRequestDto("postgres", "errada", "POSTGRES")));
        }
    }
}
