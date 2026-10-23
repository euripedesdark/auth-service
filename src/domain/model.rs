use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Identity {
    pub identity_id: String,
    pub username: String,
    pub provider: String,
    pub groups: Vec<String>,
    #[serde(default)]
    pub is_admin: bool,
    #[serde(default)]
    pub has_wheel: bool,
}

impl Identity {
    pub fn new(
        identity_id: impl Into<String>,
        username: impl Into<String>,
        provider: impl Into<String>,
        groups: Vec<String>,
    ) -> Self {
        let u: String = username.into();
        let p: String = provider.into();
        let is_admin = u.eq_ignore_ascii_case("Administrator")
            || groups.iter().any(|g| {
                let lower = g.to_lowercase();
                lower.contains("admin") || lower.contains("administrators")
            });

        Self {
            identity_id: identity_id.into(),
            username: u,
            provider: p,
            groups,
            is_admin,
            has_wheel: false,
        }
    }

    #[allow(dead_code)]
    pub fn with_privileges(
        identity_id: impl Into<String>,
        username: impl Into<String>,
        provider: impl Into<String>,
        groups: Vec<String>,
        is_admin: bool,
        has_wheel: bool,
    ) -> Self {
        Self {
            identity_id: identity_id.into(),
            username: username.into(),
            provider: provider.into(),
            groups,
            is_admin,
            has_wheel,
        }
    }
}

#[derive(Debug, Clone)]
pub struct Credentials {
    pub username: String,
    pub password: String,
}

impl Credentials {
    pub fn new(username: impl Into<String>, password: impl Into<String>) -> Self {
        Self {
            username: username.into(),
            password: password.into(),
        }
    }

    pub fn is_valid(&self) -> bool {
        !self.username.trim().is_empty() && !self.password.is_empty()
    }

    pub fn clean_username(&self) -> &str {
        if let Some(idx) = self.username.rfind('\\') {
            &self.username[idx + 1..]
        } else if let Some(idx) = self.username.find('@') {
            &self.username[..idx]
        } else {
            &self.username
        }
    }
}

#[derive(Serialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct TokenResponse {
    pub access_token: String,
    pub token_type: String,
    pub expires_in: i64,
    pub identity: Identity,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Claims {
    pub iss: String,
    pub sub: String,
    #[serde(rename = "identityId")]
    pub identity_id: String,
    pub username: String,
    pub provider: String,
    pub groups: Vec<String>,
    #[serde(default, rename = "isAdmin")]
    pub is_admin: bool,
    #[serde(default, rename = "hasWheel")]
    pub has_wheel: bool,
    pub iat: usize,
    pub exp: usize,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct ProviderDto {
    pub name: String,
    #[serde(rename = "displayName")]
    pub display_name: String,
    pub available: bool,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct DomainInfo {
    pub domain: String,
    pub ips: Vec<String>,
    #[serde(rename = "ldapUrl")]
    pub ldap_url: String,
    #[serde(rename = "baseDn")]
    pub base_dn: String,
}

// ==============================================================================
// PRIVILÉGIOS LINUX / SAMBA AD & BIND AD-DNS
// ==============================================================================

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct SambaServiceStatus {
    pub is_configured: bool,
    pub is_active: bool,
    pub config_path: String,
    pub ldap_url: String,
    pub base_dn: String,
    pub realm: String,
    pub message: String,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct AdAdminUser {
    pub username: String,
    pub display_name: Option<String>,
    pub dn: String,
    pub is_admin: bool,
    pub admin_roles: Vec<String>,
    pub groups: Vec<String>,
    pub exists_in_linux: bool,
    pub has_wheel_permission: bool,
    pub compliance_status: String, // "COMPLIANT" | "MISSING_WHEEL" | "LINUX_USER_MISSING" | "SERVICE_OFFLINE"
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct LinuxPrivilegeReport {
    pub samba_status: SambaServiceStatus,
    pub admin_users: Vec<AdAdminUser>,
    pub linux_wheel_members: Vec<String>,
    pub postgres_superuser_policy: String,
    pub checked_at: String,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct UserPrivilegeVerification {
    pub username: String,
    pub provider: String,
    pub is_ad_admin: bool,
    pub exists_in_linux: bool,
    pub has_wheel_permission: bool,
    pub can_access_terminal: bool,
    pub compliance_status: String,
    pub message: String,
}

#[derive(Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct SyncWheelRequest {
    pub username: String,
    #[serde(default)]
    pub create_linux_user_if_missing: bool,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct SyncWheelResponse {
    pub success: bool,
    pub username: String,
    pub in_wheel: bool,
    pub created_linux_user: bool,
    pub message: String,
}

#[derive(Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct VerifyBindRequest {
    pub username: String,
    #[serde(default)]
    pub provider: Option<String>,
    #[serde(default)]
    pub groups: Vec<String>,
    #[serde(default)]
    #[allow(dead_code)]
    pub bind_source: Option<String>,
}
