use crate::domain::{
    AuthError, Credentials, DomainInfo, ProviderDto, SyncWheelRequest, VerifyBindRequest,
};
use crate::service::{DomainResolver, IdentityProvider, LinuxPrivilegeService, TokenService};
use axum::{
    http::{header, HeaderMap, HeaderValue, StatusCode},
    response::{IntoResponse, Json, Response},
};
use serde::Deserialize;
use serde_json::json;
use std::collections::HashMap;
use std::sync::Arc;

#[derive(Deserialize, Debug)]
pub struct LoginPayload {
    pub username: String,
    pub password: String,
    #[allow(dead_code)]
    pub provider: Option<String>,
}

#[derive(Deserialize, Debug)]
pub struct DomainLoginPayload {
    pub username: String,
    pub password: String,
    #[allow(dead_code)]
    pub domain: String,
}

#[derive(Clone)]
pub struct IdentityController {
    identity_provider: Arc<dyn IdentityProvider>,
    token_service: Arc<dyn TokenService>,
    domain_resolver: Arc<dyn DomainResolver>,
    privilege_service: Arc<dyn LinuxPrivilegeService>,
}

impl IdentityController {
    pub fn new(
        identity_provider: Arc<dyn IdentityProvider>,
        token_service: Arc<dyn TokenService>,
        domain_resolver: Arc<dyn DomainResolver>,
        privilege_service: Arc<dyn LinuxPrivilegeService>,
    ) -> Self {
        Self {
            identity_provider,
            token_service,
            domain_resolver,
            privilege_service,
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
            Ok(mut identity) => {
                let (_, in_wheel) = self.privilege_service.check_user_in_wheel(&identity.username);
                identity.has_wheel = in_wheel;

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
            Ok(mut identity) => {
                let (_, in_wheel) = self.privilege_service.check_user_in_wheel(&identity.username);
                identity.has_wheel = in_wheel;

                match self.token_service.issue_response(&identity) {
                    Ok(resp) => (StatusCode::OK, Json(resp)).into_response(),
                    Err(e) => e.to_json_response().into_response(),
                }
            }
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
                    "isAdmin": claims.is_admin,
                    "hasWheel": claims.has_wheel,
                });
                (StatusCode::OK, Json(identity)).into_response()
            }
            Err(e) => e.to_json_response().into_response(),
        }
    }

    pub async fn handle_authenticate_domain(&self, req: DomainLoginPayload) -> Response {
        let creds = Credentials::new(req.username, req.password);
        match self.identity_provider.authenticate(&creds).await {
            Ok(mut identity) => {
                let (_, in_wheel) = self.privilege_service.check_user_in_wheel(&identity.username);
                identity.has_wheel = in_wheel;
                (StatusCode::OK, Json(identity)).into_response()
            }
            Err(e) => e.to_json_response().into_response(),
        }
    }

    pub fn handle_resolve_domain(&self, domain: &str) -> Json<DomainInfo> {
        Json(self.domain_resolver.resolve(domain))
    }

    pub fn handle_providers(&self) -> Json<Vec<ProviderDto>> {
        Json(vec![
            ProviderDto {
                name: "AD".to_string(),
                display_name: "Active Directory (Samba 4 DC)".to_string(),
                available: true,
            },
            ProviderDto {
                name: "Linux".to_string(),
                display_name: "Linux Host (wheel/sudo)".to_string(),
                available: true,
            },
        ])
    }

    pub fn handle_health(&self) -> Json<serde_json::Value> {
        let samba = self.privilege_service.check_samba_status();
        Json(json!({
            "status": "UP",
            "sambaAd": {
                "active": samba.is_active,
                "configured": samba.is_configured
            }
        }))
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

    // ==============================================================================
    // ROTAS DE VERIFICAÇÃO DE PRIVILÉGIOS LINUX / SAMBA AD & BIND AD-DNS
    // ==============================================================================

    pub async fn handle_privileges_status(&self) -> Response {
        match self.privilege_service.audit_privileges().await {
            Ok(report) => (StatusCode::OK, Json(report)).into_response(),
            Err(e) => e.to_json_response().into_response(),
        }
    }

    pub fn handle_privileges_verify(&self, params: HashMap<String, String>) -> Response {
        let username = params.get("username").map(|s| s.as_str()).unwrap_or("");
        if username.trim().is_empty() {
            return (
                StatusCode::BAD_REQUEST,
                Json(json!({"error": "Parâmetro 'username' é obrigatório."})),
            )
                .into_response();
        }

        let provider = params.get("provider").map(|s| s.as_str());
        let groups: Vec<String> = params
            .get("groups")
            .map(|g| g.split(',').map(|s| s.trim().to_string()).filter(|s| !s.is_empty()).collect())
            .unwrap_or_default();

        let verification = self.privilege_service.verify_user_privilege(username, provider, &groups);
        (StatusCode::OK, Json(verification)).into_response()
    }

    pub fn handle_verify_bind(&self, payload: VerifyBindRequest) -> Response {
        let verification = self.privilege_service.verify_bind_user(&payload);
        (StatusCode::OK, Json(verification)).into_response()
    }

    pub async fn handle_sync_wheel(&self, payload: SyncWheelRequest) -> Response {
        match self.privilege_service.sync_wheel_permission(&payload).await {
            Ok(res) => (StatusCode::OK, Json(res)).into_response(),
            Err(e) => e.to_json_response().into_response(),
        }
    }
}
