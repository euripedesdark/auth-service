use crate::domain::{AuthError, Credentials, Identity};
use async_trait::async_trait;
use ldap3::{LdapConnAsync, LdapConnSettings, Scope, SearchEntry};
use tracing::{error, warn};

#[async_trait]
pub trait IdentityProvider: Send + Sync {
    async fn authenticate(&self, credentials: &Credentials) -> Result<Identity, AuthError>;
}

pub struct ActiveDirectoryProvider {
    ldap_url: String,
    ldap_base: String,
    admin_dn: String,
    admin_password: String,
}

impl ActiveDirectoryProvider {
    pub fn new(
        ldap_url: impl Into<String>,
        ldap_base: impl Into<String>,
        admin_dn: impl Into<String>,
        admin_password: impl Into<String>,
    ) -> Self {
        Self {
            ldap_url: ldap_url.into(),
            ldap_base: ldap_base.into(),
            admin_dn: admin_dn.into(),
            admin_password: admin_password.into(),
        }
    }
}

#[async_trait]
impl IdentityProvider for ActiveDirectoryProvider {
    async fn authenticate(&self, credentials: &Credentials) -> Result<Identity, AuthError> {
        if !credentials.is_valid() {
            return Err(AuthError::MissingCredentials);
        }

        let clean_user = credentials.clean_username();
        let mut settings = LdapConnSettings::new().set_conn_timeout(std::time::Duration::from_secs(5));
        if let Ok(path) = std::env::var("AUTH_LDAP_CA_FILE") {
            let bytes = std::fs::read(path).map_err(|_| AuthError::Internal("LDAP CA file unreadable".into()))?;
            let certificate = native_tls::Certificate::from_pem(&bytes).map_err(|_| AuthError::Internal("Invalid LDAP CA certificate".into()))?;
            let connector = native_tls::TlsConnector::builder().add_root_certificate(certificate).build()
                .map_err(|_| AuthError::Internal("LDAP TLS configuration failed".into()))?;
            settings = settings.set_connector(connector);
        }

        let (conn, mut ldap) = LdapConnAsync::with_settings(settings.clone(), &self.ldap_url)
            .await
            .map_err(|e| {
                error!("ActiveDirectoryProvider connection error: {}", e);
                AuthError::ServiceUnavailable("LDAP connection failed".into())
            })?;
        ldap3::drive!(conn);

        ldap.simple_bind(&self.admin_dn, &self.admin_password)
            .await
            .map_err(|e| {
                error!("ActiveDirectoryProvider admin bind error: {}", e);
                AuthError::Internal("LDAP bind failed".into())
            })?.success().map_err(|_| AuthError::InvalidCredentials("LDAP service bind rejected".into()))?;

        let filter = format!(
            "(&(objectClass=user)(|(sAMAccountName={})(userPrincipalName={})(mail={})))",
            ldap3::ldap_escape(clean_user), ldap3::ldap_escape(&credentials.username), ldap3::ldap_escape(&credentials.username)
        );

        let sr = ldap
            .search(&self.ldap_base, Scope::Subtree, &filter, vec!["sAMAccountName", "userPrincipalName", "objectGUID", "objectSid", "memberOf"])
            .await
            .map_err(|e| {
                error!("LDAP search error: {}", e);
                AuthError::Internal("LDAP search failed".into())
            })?
            .success()
            .map_err(|e| {
                warn!("LDAP search failed: {}", e);
                AuthError::InvalidCredentials("Invalid credentials".into())
            })?;

        let (entries, _) = sr;
        if entries.len() != 1 {
            warn!("User not found in AD: {}", clean_user);
            return Err(AuthError::InvalidCredentials("Invalid credentials".into()));
        }

        let se = SearchEntry::construct(entries[0].clone());
        let user_dn = se.dn.clone();
        let sam_name = se
            .attrs
            .get("sAMAccountName")
            .and_then(|v| v.first())
            .cloned()
            .unwrap_or_else(|| clean_user.to_string());

        let mut groups: Vec<String> = Vec::new();
        if let Some(member_of) = se.attrs.get("memberOf") {
            for m in member_of {
                let cn = if let Some(stripped) = m.strip_prefix("CN=") {
                    if let Some(comma) = stripped.find(',') {
                        &stripped[..comma]
                    } else {
                        stripped
                    }
                } else {
                    m.as_str()
                };
                if !cn.is_empty() && !groups.contains(&cn.to_string()) {
                    groups.push(cn.to_string());
                }
            }
        }

        // Verify password with user bind
        let (u_conn, mut u_ldap) = LdapConnAsync::with_settings(settings, &self.ldap_url)
            .await
            .map_err(|e| {
                error!("User bind connection error: {}", e);
                AuthError::ServiceUnavailable("LDAP connection failed".into())
            })?;
        ldap3::drive!(u_conn);

        u_ldap.simple_bind(&user_dn, &credentials.password)
            .await
            .map_err(|e| {
                warn!("User bind verification failed for '{}': {}", user_dn, e);
                AuthError::InvalidCredentials("Invalid credentials".into())
            })?.success().map_err(|_| AuthError::InvalidCredentials("Invalid credentials".into()))?;

        let identity_id = stable_identity_id(&se).ok_or_else(|| AuthError::Internal("AD user has no stable identifier".into()))?;
        let mut identity = Identity::new(identity_id, sam_name, "AD", groups);
        identity.is_admin = identity.groups.iter().any(|group| {
            ["Domain Admins", "Administrators", "Enterprise Admins"].iter().any(|admin| group.eq_ignore_ascii_case(admin))
        });
        Ok(identity)
    }
}


fn stable_identity_id(entry: &SearchEntry) -> Option<String> {
    use base64::Engine;
    if let Some(guid) = entry.bin_attrs.get("objectGUID").and_then(|v| v.first()).filter(|v| v.len() == 16) {
        return Some(format!("{:08x}-{:04x}-{:04x}-{:02x}{:02x}-{:02x}{:02x}{:02x}{:02x}{:02x}{:02x}",
            u32::from_le_bytes(guid[0..4].try_into().ok()?), u16::from_le_bytes(guid[4..6].try_into().ok()?), u16::from_le_bytes(guid[6..8].try_into().ok()?),
            guid[8],guid[9],guid[10],guid[11],guid[12],guid[13],guid[14],guid[15]));
    }
    if let Some(sid) = entry.bin_attrs.get("objectSid").and_then(|v| v.first()) {
        return Some(format!("sid:{}", base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(sid)));
    }
    entry.attrs.get("userPrincipalName").and_then(|v| v.first()).filter(|v| !v.is_empty()).cloned()
}

#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    async fn rejected_ldap_bind_is_not_treated_as_success() {
        use tokio::io::{AsyncReadExt, AsyncWriteExt};
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            let mut data = [0u8;4096];
            socket.read(&mut data).await.unwrap();
            // LDAP BindResponse, message id 1, resultCode invalidCredentials (49).
            socket.write_all(&[0x30,0x0c,0x02,0x01,0x01,0x61,0x07,0x0a,0x01,0x31,0x04,0x00,0x04,0x00]).await.unwrap();
        });
        let provider = ActiveDirectoryProvider::new(format!("ldap://{address}"),"DC=example,DC=test","service@example.test","bad-password");
        let result = tokio::time::timeout(std::time::Duration::from_secs(3), provider.authenticate(&Credentials::new("alice","bad-password"))).await.unwrap();
        assert!(matches!(result, Err(AuthError::InvalidCredentials(_))));
    }
    #[test]
    fn guid_is_stable_when_account_name_changes() {
        let mut entry = SearchEntry { dn:"CN=Alice".into(), attrs:std::collections::HashMap::new(), bin_attrs:std::collections::HashMap::new() };
        entry.bin_attrs.insert("objectGUID".into(),vec![vec![0x33,0x22,0x11,0x00,0x55,0x44,0x77,0x66,0x88,0x99,0xaa,0xbb,0xcc,0xdd,0xee,0xff]]);
        assert_eq!(stable_identity_id(&entry).unwrap(),"00112233-4455-6677-8899-aabbccddeeff");
        entry.attrs.insert("sAMAccountName".into(),vec!["renamed".into()]);
        assert_eq!(stable_identity_id(&entry).unwrap(),"00112233-4455-6677-8899-aabbccddeeff");
    }
}
