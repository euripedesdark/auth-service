#[derive(Clone, Debug)]
pub struct AuthConfig {
    pub ldap_url: String,
    pub ldap_base: String,
    pub ldap_user_dn: String,
    pub ldap_password: String,
    pub token_secret: String,
    pub token_issuer: String,
    pub token_ttl_seconds: i64,
    pub server_port: u16,
}

impl AuthConfig {
    pub fn load() -> Self {
        let _ = dotenvy::from_path("/etc/auth-service/auth.env");
        let _ = dotenvy::dotenv();

        let ldap_url = std::env::var("AUTH_LDAP_URL")
            .unwrap_or_else(|_| "ldaps://127.0.0.1:636".to_string());
        let ldap_base = std::env::var("AUTH_LDAP_BASE")
            .unwrap_or_else(|_| "DC=srvcloud,DC=cloud".to_string());
        let ldap_user_dn = std::env::var("AUTH_LDAP_USER_DN")
            .unwrap_or_else(|_| "Administrator@srvcloud.cloud".to_string());
        let ldap_password = std::env::var("AUTH_LDAP_PASSWORD")
            .unwrap_or_else(|_| "Copa@@2026".to_string());
        let token_secret = std::env::var("AUTH_TOKEN_SECRET").unwrap_or_else(|_| {
            "dd1f4702cbb4d061075ff7c58c63e3f48a214bf6117e4453ad4ee20b2f48e3ae".to_string()
        });
        let token_issuer = std::env::var("AUTH_TOKEN_ISSUER")
            .unwrap_or_else(|_| "auth-service".to_string());
        let token_ttl_seconds = std::env::var("AUTH_TOKEN_TTL_SECONDS")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(900);
        let server_port = std::env::var("SERVER_PORT")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(8181);

        Self {
            ldap_url,
            ldap_base,
            ldap_user_dn,
            ldap_password,
            token_secret,
            token_issuer,
            token_ttl_seconds,
            server_port,
        }
    }
}
