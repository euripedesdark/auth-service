use crate::domain::{AuthError, Credentials, DomainInfo, ProviderDto};
use crate::service::{DomainResolver, IdentityProvider, TokenService};
use axum::{
    http::{header, HeaderMap, HeaderValue, StatusCode},
    response::{IntoResponse, Json, Response},
};
use serde::Deserialize;
use serde_json::json;
use std::sync::Arc;

#[derive(Deserialize, Debug)]
pub struct LoginPayload {
    pub username: String,
    pub password: String,
    pub provider: Option<String>,
}

#[derive(Deserialize, Debug)]
pub struct DomainLoginPayload {
    pub username: String,
    pub password: String,
    pub domain: String,
}

#[derive(Clone)]
pub struct IdentityController {
    identity_provider: Arc<dyn IdentityProvider>,
    token_service: Arc<dyn TokenService>,
    domain_resolver: Arc<dyn DomainResolver>,
}

impl IdentityController {
    pub fn new(
        identity_provider: Arc<dyn IdentityProvider>,
        token_service: Arc<dyn TokenService>,
        domain_resolver: Arc<dyn DomainResolver>,
    ) -> Self {
        Self {
            identity_provider,
            token_service,
            domain_resolver,
        }
    }

    pub fn extract_token(headers: &HeaderMap) -> Option<String> {
        headers
            .get(header::AUTHORIZATION)
            .and_then(|v| v.to_str().ok())
            .and_then(|h| h.strip_prefix("Bearer ").map(|t| t.trim().to_string()))
    }

    pub async fn handle_authenticate(&self, req: LoginPayload) -> Response {
        let creds = Credentials::new(req.username, req.password);
        match self.identity_provider.authenticate(&creds).await {
            Ok(identity) => {
                let token = match self.token_service.issue_token(&identity) {
                    Ok(t) => t,
                    Err(e) => return e.to_json_response().into_response(),
                };

                let mut headers = HeaderMap::new();
                let sso_cookie = format!("sso_token={}; Path=/; Domain=192.168.2.10; HttpOnly; SameSite=Lax", token);
                let astral_cookie = format!("astral_token={}; Path=/; SameSite=Lax", token);

                if let Ok(v) = HeaderValue::from_str(&sso_cookie) {
                    headers.append(header::SET_COOKIE, v);
                }
                if let Ok(v) = HeaderValue::from_str(&astral_cookie) {
                    headers.append(header::SET_COOKIE, v);
                }
                headers.insert(header::CONTENT_TYPE, HeaderValue::from_static("application/json"));

                let body = serde_json::to_string(&identity).unwrap_or_default();
                (StatusCode::OK, headers, body).into_response()
            }
            Err(e) => e.to_json_response().into_response(),
        }
    }

    pub async fn handle_token(&self, req: LoginPayload) -> Response {
        let creds = Credentials::new(req.username, req.password);
        match self.identity_provider.authenticate(&creds).await {
            Ok(identity) => match self.token_service.issue_response(&identity) {
                Ok(resp) => (StatusCode::OK, Json(resp)).into_response(),
                Err(e) => e.to_json_response().into_response(),
            },
            Err(e) => e.to_json_response().into_response(),
        }
    }

    pub fn handle_me(&self, headers: &HeaderMap) -> Response {
        let token = match Self::extract_token(headers) {
            Some(t) => t,
            None => return AuthError::MissingToken.to_json_response().into_response(),
        };

        match self.token_service.verify_token(&token) {
            Ok(claims) => {
                let identity = json!({
                    "identityId": claims.identity_id,
                    "username": claims.username,
                    "provider": claims.provider,
                    "groups": claims.groups,
                });
                (StatusCode::OK, Json(identity)).into_response()
            }
            Err(e) => e.to_json_response().into_response(),
        }
    }

    pub async fn handle_authenticate_domain(&self, req: DomainLoginPayload) -> Response {
        let creds = Credentials::new(req.username, req.password);
        match self.identity_provider.authenticate(&creds).await {
            Ok(identity) => (StatusCode::OK, Json(identity)).into_response(),
            Err(e) => e.to_json_response().into_response(),
        }
    }

    pub fn handle_resolve_domain(&self, domain: &str) -> Json<DomainInfo> {
        Json(self.domain_resolver.resolve(domain))
    }

    pub fn handle_providers(&self) -> Json<Vec<ProviderDto>> {
        Json(vec![ProviderDto {
            name: "AD".to_string(),
            display_name: "Active Directory".to_string(),
            available: true,
        }])
    }

    pub fn handle_health(&self) -> Json<serde_json::Value> {
        Json(json!({ "status": "UP" }))
    }

    pub fn handle_logout(&self) -> Response {
        let mut headers = HeaderMap::new();
        let clear_sso = "sso_token=; Path=/; Domain=192.168.2.10; HttpOnly; SameSite=Lax; Max-Age=0";
        let clear_astral = "astral_token=; Path=/; SameSite=Lax; Max-Age=0";
        headers.append(header::SET_COOKIE, HeaderValue::from_static(clear_sso));
        headers.append(header::SET_COOKIE, HeaderValue::from_static(clear_astral));
        headers.insert(header::CONTENT_TYPE, HeaderValue::from_static("application/json"));
        (StatusCode::OK, headers, r#"{"message":"Logged out"}"#).into_response()
    }
}
