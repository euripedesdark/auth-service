package com.euripedes.authservice.provider;

import com.euripedes.authservice.config.PgProperties;
import com.euripedes.authservice.contract.LoginRequestDto;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.BadCredentialsException;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="IAM_TEST_DB_URL", matches=".+")
class PostgresProviderIntegrationTest {
    @Test void bancoRealDistingueSuperuserRoleNormalESenhaErrada() throws Exception {
        String url = System.getenv("IAM_TEST_DB_URL");
        String user = "iam_smoke", password = "ci-only-password";
        try (var connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT current_database()")) {
                assertTrue(result.next());
                assertEquals("iam_smoke", result.getString(1), "Teste exige banco descartavel iam_smoke");
            }
            statement.execute("CREATE ROLE iam_regular LOGIN NOSUPERUSER PASSWORD 'ci-only-regular-password'");
            try {
                var config = new PgProperties(); config.setUrl(url); config.setCredentialSslMode("disable");
                config.setSslMode("disable"); config.setServiceUser(user);
                var provider = new PostgresProvider(config);
                assertTrue(provider.authenticate(new LoginRequestDto(user, password, "POSTGRES"))
                        .groups().contains("POSTGRES_SUPERUSER"));
                assertFalse(provider.authenticate(new LoginRequestDto("iam_regular", "ci-only-regular-password", "POSTGRES"))
                        .groups().contains("POSTGRES_SUPERUSER"));
                assertThrows(BadCredentialsException.class,
                        () -> provider.authenticate(new LoginRequestDto(user, "wrong-password", "POSTGRES")));
            } finally { statement.execute("DROP ROLE iam_regular"); }
        }
    }
}
