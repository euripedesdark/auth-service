mod config;
mod controller;
mod domain;
mod server;
mod service;

use config::AuthConfig;
use controller::IdentityController;
use server::AuthServer;
use service::{ActiveDirectoryProvider, DefaultDomainResolver, JwtTokenService};
use std::sync::Arc;
use tracing::info;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .init();

    info!("Initializing Astral Auth Service (Rust OOP Architecture)...");

    let config = Arc::new(AuthConfig::load());

    let identity_provider = Arc::new(ActiveDirectoryProvider::new(
        &config.ldap_url,
        &config.ldap_base,
        &config.ldap_user_dn,
        &config.ldap_password,
    ));

    let token_service = Arc::new(JwtTokenService::new(
        &config.token_secret,
        &config.token_issuer,
        config.token_ttl_seconds,
    ));

    let domain_resolver = Arc::new(DefaultDomainResolver::new());

    let controller = Arc::new(IdentityController::new(
        identity_provider,
        token_service,
        domain_resolver,
    ));

    let server = AuthServer::new(config, controller);
    server.run().await
}
