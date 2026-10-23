use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Identity {
    pub identity_id: String,
    pub username: String,
    pub provider: String,
    pub groups: Vec<String>,
}

impl Identity {
    pub fn new(
        identity_id: impl Into<String>,
        username: impl Into<String>,
        provider: impl Into<String>,
        groups: Vec<String>,
    ) -> Self {
        Self {
            identity_id: identity_id.into(),
            username: username.into(),
            provider: provider.into(),
            groups,
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

#[derive(Serialize, Deserialize, Debug)]
pub struct Claims {
    pub iss: String,
    pub sub: String,
    #[serde(rename = "identityId")]
    pub identity_id: String,
    pub username: String,
    pub provider: String,
    pub groups: Vec<String>,
    pub iat: usize,
    pub exp: usize,
}

#[derive(Serialize)]
pub struct ProviderDto {
    pub name: String,
    #[serde(rename = "displayName")]
    pub display_name: String,
    pub available: bool,
}

#[derive(Serialize)]
pub struct DomainInfo {
    pub domain: String,
    pub ips: Vec<String>,
    #[serde(rename = "ldapUrl")]
    pub ldap_url: String,
    #[serde(rename = "baseDn")]
    pub base_dn: String,
}
