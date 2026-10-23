use crate::config::AuthConfig;
use crate::controller::{DomainLoginPayload, IdentityController, LoginPayload};
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
use tracing::info;

pub struct AuthServer {
    config: Arc<AuthConfig>,
    controller: Arc<IdentityController>,
}

impl AuthServer {
    pub fn new(config: Arc<AuthConfig>, controller: Arc<IdentityController>) -> Self {
        Self { config, controller }
    }

    pub fn build_router(&self) -> Router {
        let cors = CorsLayer::new()
            .allow_origin(Any)
            .allow_methods(Any)
            .allow_headers(Any);

        Router::new()
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
            .layer(cors)
            .layer(TraceLayer::new_for_http())
            .with_state(self.controller.clone())
    }

    pub async fn run(&self) -> Result<(), Box<dyn std::error::Error>> {
        let addr = SocketAddr::from(([0, 0, 0, 0], self.config.server_port));
        info!("Astral Auth Service listening on {}", addr);
        let listener = tokio::net::TcpListener::bind(addr).await?;
        let router = self.build_router();
        axum::serve(listener, router).await?;
        Ok(())
    }
}
