use crate::domain::{AuthError, Claims, Identity, TokenResponse};
use chrono::Utc;
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use tracing::{error, warn};

pub trait TokenService: Send + Sync {
    fn issue_token(&self, identity: &Identity) -> Result<String, AuthError>;
    fn issue_response(&self, identity: &Identity) -> Result<TokenResponse, AuthError>;
    fn verify_token(&self, token: &str) -> Result<Claims, AuthError>;
}

pub struct JwtTokenService {
    secret: String,
    issuer: String,
    ttl_seconds: i64,
}

impl JwtTokenService {
    pub fn new(secret: impl Into<String>, issuer: impl Into<String>, ttl_seconds: i64) -> Self {
        Self {
            secret: secret.into(),
            issuer: issuer.into(),
            ttl_seconds,
        }
    }
}

impl TokenService for JwtTokenService {
    fn issue_token(&self, identity: &Identity) -> Result<String, AuthError> {
        let now = Utc::now().timestamp() as usize;
        let exp = now + (self.ttl_seconds as usize);

        let claims = Claims {
            iss: self.issuer.clone(),
            sub: identity.username.clone(),
            identity_id: identity.identity_id.clone(),
            username: identity.username.clone(),
            provider: identity.provider.clone(),
            groups: identity.groups.clone(),
            iat: now,
            exp,
        };

        encode(
            &Header::default(),
            &claims,
            &EncodingKey::from_secret(self.secret.as_bytes()),
        )
        .map_err(|e| {
            error!("Error encoding JWT: {}", e);
            AuthError::Internal("Failed to generate token".into())
        })
    }

    fn issue_response(&self, identity: &Identity) -> Result<TokenResponse, AuthError> {
        let token = self.issue_token(identity)?;
        Ok(TokenResponse {
            access_token: token,
            token_type: "Bearer".to_string(),
            expires_in: self.ttl_seconds,
            identity: identity.clone(),
        })
    }

    fn verify_token(&self, token: &str) -> Result<Claims, AuthError> {
        let mut validation = Validation::default();
        validation.set_issuer(&[&self.issuer]);
        validation.validate_exp = true;

        decode::<Claims>(
            token,
            &DecodingKey::from_secret(self.secret.as_bytes()),
            &validation,
        )
        .map(|data| data.claims)
        .map_err(|e| {
            warn!("Token validation error: {}", e);
            AuthError::InvalidToken("Invalid token".into())
        })
    }
}
