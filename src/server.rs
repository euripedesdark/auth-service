use crate::config::AuthConfig;
use crate::controller::{DomainLoginPayload, IdentityController, LoginPayload};
use crate::domain::{SyncWheelRequest, VerifyBindRequest};
use axum::{
    extract::{Query, State},
    http::HeaderMap,
    routing::{get, post},
    Json, Router,
};
use std::collections::HashMap;
use std::net::SocketAddr;
use std::sync::Arc;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;
use tower_http::services::ServeDir;
use tracing::info;

pub struct AuthServer {
    config: Arc<AuthConfig>,
    oauth: Arc<crate::oauth::Provider>,
    controller: Arc<IdentityController>,
}

impl AuthServer {
    pub fn new(config: Arc<AuthConfig>, controller: Arc<IdentityController>, oauth: Arc<crate::oauth::Provider>) -> Self {
        Self { config, controller, oauth }
    }

    pub fn build_router(&self) -> Router {
        let cors = CorsLayer::new()
            .allow_origin(Any)
            .allow_methods(Any)
            .allow_headers(Any);

        let web_dist = std::env::var("AUTH_WEB_DIST").unwrap_or_else(|_| "web/dist".into());
        Router::new()
            .nest_service("/admin", ServeDir::new(web_dist.clone()).append_index_html_on_directories(true))
            .nest_service("/mfa", ServeDir::new(web_dist).append_index_html_on_directories(true))
            .route(
                "/api/v1/identity/authenticate",
                post(|State(ctrl): State<Arc<IdentityController>>, Json(payload): Json<LoginPayload>| async move {
                    ctrl.handle_authenticate(payload).await
                }),
            )
            .route(
                "/api/v1/identity/token",
                post(|State(ctrl): State<Arc<IdentityController>>, Json(payload): Json<LoginPayload>| async move {
                    ctrl.handle_token(payload).await
                }),
            )
            .route(
                "/api/v1/identity/me",
                get(|State(ctrl): State<Arc<IdentityController>>, headers: HeaderMap| async move {
                    ctrl.handle_me(&headers)
                }),
            )
            .route(
                "/api/v1/identity/authenticated",
                get(|State(ctrl): State<Arc<IdentityController>>, headers: HeaderMap| async move {
                    ctrl.handle_me(&headers)
                }),
            )
            .route(
                "/api/v1/identity/groups",
                get(|State(ctrl): State<Arc<IdentityController>>, headers: HeaderMap| async move {
                    ctrl.handle_me(&headers)
                }),
            )
            .route(
                "/api/v1/identity/provider",
                get(|State(ctrl): State<Arc<IdentityController>>, headers: HeaderMap| async move {
                    ctrl.handle_me(&headers)
                }),
            )
            .route(
                "/api/v1/identity/authenticate-domain",
                post(|State(ctrl): State<Arc<IdentityController>>, Json(payload): Json<DomainLoginPayload>| async move {
                    ctrl.handle_authenticate_domain(payload).await
                }),
            )
            .route(
                "/api/v1/identity/resolve-domain",
                get(|State(ctrl): State<Arc<IdentityController>>, Query(params): Query<HashMap<String, String>>| async move {
                    let domain = params.get("domain").map(|s| s.as_str()).unwrap_or("srvcloud.cloud");
                    ctrl.handle_resolve_domain(domain)
                }),
            )
            .route(
                "/api/v1/identity/logout",
                post(|State(ctrl): State<Arc<IdentityController>>| async move {
                    ctrl.handle_logout()
                })
                .get(|State(ctrl): State<Arc<IdentityController>>| async move {
                    ctrl.handle_logout()
                }),
            )
            .route(
                "/api/v1/providers",
                get(|State(ctrl): State<Arc<IdentityController>>| async move {
                    ctrl.handle_providers()
                }),
            )
            .route(
                "/health",
                get(|State(ctrl): State<Arc<IdentityController>>| async move {
                    ctrl.handle_health()
                }),
            )
            .route(
                "/actuator/health",
                get(|State(ctrl): State<Arc<IdentityController>>| async move {
                    ctrl.handle_health()
                }),
            )
            // Rotas de Verificação e Sincronização de Privilégios Linux (Wheel / Samba AD & BIND)
            .route(
                "/api/v1/privileges/status",
                get(|State(ctrl): State<Arc<IdentityController>>| async move {
                    ctrl.handle_privileges_status().await
                }),
            )
            .route(
                "/api/v1/privileges/audit",
                get(|State(ctrl): State<Arc<IdentityController>>| async move {
                    ctrl.handle_privileges_status().await
                }),
            )
            .route(
                "/api/v1/privileges/verify",
                get(|State(ctrl): State<Arc<IdentityController>>, Query(params): Query<HashMap<String, String>>| async move {
                    ctrl.handle_privileges_verify(params)
                }),
            )
            .route(
                "/api/v1/privileges/verify-bind",
                post(|State(ctrl): State<Arc<IdentityController>>, Json(payload): Json<VerifyBindRequest>| async move {
                    ctrl.handle_verify_bind(payload)
                }),
            )
            .route(
                "/api/v1/privileges/sync-wheel",
                post(|State(ctrl): State<Arc<IdentityController>>, Json(payload): Json<SyncWheelRequest>| async move {
                    ctrl.handle_sync_wheel(payload).await
                }),
            )
            .layer(cors)
            .layer(TraceLayer::new_for_http())
            .with_state(self.controller.clone())
            .merge(crate::oauth::router(self.oauth.clone()))
    }

    pub async fn run(&self) -> Result<(), Box<dyn std::error::Error>> {
        let address = std::env::var("SERVER_ADDRESS").unwrap_or_else(|_| "127.0.0.1".into());
        let addr: SocketAddr = format!("{}:{}", address, self.config.server_port).parse()?;
        info!("Astral Auth Service listening on {}", addr);
        let listener = tokio::net::TcpListener::bind(addr).await?;
        let router = self.build_router();
        axum::serve(listener, router).await?;
        Ok(())
    }
}
