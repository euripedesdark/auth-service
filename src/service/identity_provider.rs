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
        let settings = LdapConnSettings::new().set_no_tls_verify(true);

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
            })?;

        let filter = format!(
            "(&(objectClass=user)(|(sAMAccountName={})(userPrincipalName={})(mail={})))",
            clean_user, credentials.username, credentials.username
        );

        let sr = ldap
            .search(&self.ldap_base, Scope::Subtree, &filter, vec!["*", "memberOf"])
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
        if entries.is_empty() {
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
            })?;

        Ok(Identity::new(sam_name.clone(), sam_name, "AD", groups))
    }
}
