//! First-party OAuth2 authorization server: authorization code + mandatory S256 PKCE.
use crate::{
    domain::{Credentials, Identity},
    service::{IdentityProvider, LinuxPrivilegeService},
};
use axum::{
    extract::{Form, Query, State},
    http::{header, HeaderMap, HeaderValue, StatusCode},
    response::{Html, IntoResponse, Redirect, Response},
    routing::get,
    Json, Router,
};
use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine};
use openssl::{rand::rand_bytes, sha::sha256};
use serde::Deserialize;
use serde_json::json;
use std::{
    collections::HashMap,
    sync::Arc,
    time::{Duration, Instant},
};
use tokio::sync::Mutex;
use url::Url;

const TRANSACTION_TTL: Duration = Duration::from_secs(300);
const CODE_TTL: Duration = Duration::from_secs(60);
const TOKEN_TTL: Duration = Duration::from_secs(900);
const SESSION_TTL: Duration = Duration::from_secs(28800);
const MAX_ENTRIES: usize = 4096;

#[derive(Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Client {
    pub client_id: String,
    pub client_secret: String,
    pub redirect_uris: Vec<String>,
}
#[derive(Clone, Deserialize)]
pub struct Authorization {
    pub response_type: String,
    pub client_id: String,
    pub redirect_uri: String,
    pub state: String,
    pub code_challenge: String,
    pub code_challenge_method: String,
    #[serde(default)]
    pub scope: String,
}
struct LoginTransaction {
    request: Authorization,
    expires: Instant,
    attempts: u8,
}
struct Grant {
    request: Authorization,
    identity: Identity,
    expires: Instant,
}
struct Access {
    identity: Identity,
    scope: String,
    expires: Instant,
}
struct Session {
    identity: Identity,
    expires: Instant,
}
#[derive(Default)]
struct Store {
    logins: HashMap<String, LoginTransaction>,
    codes: HashMap<String, Grant>,
    tokens: HashMap<String, Access>,
    sessions: HashMap<String, Session>,
}
impl Store {
    fn prune(&mut self) {
        let now = Instant::now();
        self.logins.retain(|_, v| v.expires > now);
        self.codes.retain(|_, v| v.expires > now);
        self.tokens.retain(|_, v| v.expires > now);
        self.sessions.retain(|_, v| v.expires > now);
    }
    fn full(&self) -> bool {
        self.logins.len() + self.codes.len() + self.tokens.len() + self.sessions.len()
            >= MAX_ENTRIES
    }
}
pub struct Provider {
    issuer: Option<Url>,
    clients: Vec<Client>,
    identity: Arc<dyn IdentityProvider>,
    privileges: Option<Arc<dyn LinuxPrivilegeService>>,
    store: Mutex<Store>,
    login_slots: tokio::sync::Semaphore,
}
impl Provider {
    pub fn load(
        identity: Arc<dyn IdentityProvider>,
        privileges: Arc<dyn LinuxPrivilegeService>,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let clients = match std::env::var("AUTH_OAUTH_CLIENTS_FILE") {
            Ok(path) => serde_json::from_slice::<Vec<Client>>(&std::fs::read(path)?)?,
            Err(_) => Vec::new(),
        };
        let mut provider = Self::new(clients, identity)?;
        provider.privileges = Some(privileges);
        if let Ok(issuer) = std::env::var("AUTH_OAUTH_ISSUER_URL") {
            let issuer = Url::parse(&issuer)?;
            if !secure_url(&issuer)
                || issuer.query().is_some()
                || issuer.fragment().is_some()
                || !issuer.username().is_empty()
                || issuer.password().is_some()
            {
                return Err("Invalid OAuth issuer URL".into());
            }
            provider.issuer = Some(issuer);
        }
        if !provider.clients.is_empty() && provider.issuer.is_none() {
            return Err("AUTH_OAUTH_ISSUER_URL required when registering clients".into());
        }
        Ok(provider)
    }
    pub fn new(
        clients: Vec<Client>,
        identity: Arc<dyn IdentityProvider>,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let mut ids = std::collections::HashSet::new();
        for c in &clients {
            if c.client_id.is_empty()
                || c.client_secret.len() < 32
                || c.redirect_uris.is_empty()
                || !ids.insert(c.client_id.clone())
            {
                return Err("Invalid or duplicate OAuth client registration".into());
            }
            for uri in &c.redirect_uris {
                let url = Url::parse(uri)?;
                if !secure_url(&url)
                    || url.fragment().is_some()
                    || url.query().is_some()
                    || !url.username().is_empty()
                    || url.password().is_some()
                {
                    return Err("OAuth redirect must be exact HTTPS (HTTP only on loopback), without query/fragment/userinfo".into());
                }
            }
        }
        Ok(Self {
            issuer: None,
            clients,
            identity,
            privileges: None,
            store: Mutex::new(Store::default()),
            login_slots: tokio::sync::Semaphore::new(16),
        })
    }
    fn validate(&self, request: &Authorization) -> bool {
        request.response_type == "code"
            && request.code_challenge_method == "S256"
            && request.code_challenge.len() == 43
            && request
                .code_challenge
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
            && (16..=512).contains(&request.state.len())
            && request
                .scope
                .split_whitespace()
                .all(|s| matches!(s, "profile" | "groups"))
            && self.clients.iter().any(|c| {
                c.client_id == request.client_id && c.redirect_uris.contains(&request.redirect_uri)
            })
    }
    async fn grant(&self, request: Authorization, identity: Identity) -> Response {
        let code = match random() {
            Ok(v) => v,
            Err(_) => return error(StatusCode::INTERNAL_SERVER_ERROR, "server_error"),
        };
        let mut url = match Url::parse(&request.redirect_uri) {
            Ok(v) => v,
            Err(_) => return error(StatusCode::BAD_REQUEST, "invalid_request"),
        };
        url.query_pairs_mut()
            .append_pair("code", &code)
            .append_pair("state", &request.state);
        let mut store = self.store.lock().await;
        store.prune();
        if store.full() {
            return error(StatusCode::SERVICE_UNAVAILABLE, "temporarily_unavailable");
        }
        store.codes.insert(
            code,
            Grant {
                request,
                identity,
                expires: Instant::now() + CODE_TTL,
            },
        );
        no_store(Redirect::to(url.as_str()).into_response())
    }
}
pub fn secure_url(url: &Url) -> bool {
    url.scheme() == "https"
        || (url.scheme() == "http"
            && matches!(url.host_str(), Some("127.0.0.1" | "localhost" | "[::1]")))
}
fn constant_eq(a: &[u8], b: &[u8]) -> bool {
    a.len() == b.len() && openssl::memcmp::eq(a, b)
}
fn random() -> Result<String, openssl::error::ErrorStack> {
    let mut bytes = [0u8; 32];
    rand_bytes(&mut bytes)?;
    Ok(URL_SAFE_NO_PAD.encode(bytes))
}
fn cookie(headers: &HeaderMap, name: &str) -> Option<String> {
    headers
        .get(header::COOKIE)?
        .to_str()
        .ok()?
        .split(';')
        .filter_map(|v| v.trim().split_once('='))
        .find(|(key, _)| *key == name)
        .map(|(_, v)| v.to_owned())
}
fn set_cookie(response: &mut Response, name: &str, value: &str, secure: bool, age: u64) {
    if let Ok(header) = HeaderValue::from_str(&format!(
        "{name}={value}; Path=/; HttpOnly; SameSite=Lax; Max-Age={age}{}",
        if secure { "; Secure" } else { "" }
    )) {
        response.headers_mut().append(header::SET_COOKIE, header);
    }
}
fn no_store(mut response: Response) -> Response {
    response
        .headers_mut()
        .insert(header::CACHE_CONTROL, HeaderValue::from_static("no-store"));
    response
        .headers_mut()
        .insert("referrer-policy", HeaderValue::from_static("no-referrer"));
    response
}
fn error(status: StatusCode, message: &str) -> Response {
    no_store((status, Json(json!({"error":message}))).into_response())
}
fn login_page(ticket: &str, failed: bool) -> Response {
    // Only server-generated base64url is interpolated. No untrusted URL/identity is reflected.
    let message = if failed {
        "Credenciais invalidas ou diretorio indisponivel. Tente novamente."
    } else {
        "Entre com sua conta corporativa para continuar no Astral."
    };
    let mut response = no_store(Html(format!(r#"<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Astral — Autenticacao central</title><style>body{{font:16px system-ui;background:#0b1220;color:#eff6ff;display:grid;place-items:center;min-height:95vh;margin:0}}main{{width:min(85vw,390px);padding:32px;background:#152238;border-radius:16px}}label,input,button{{display:block;box-sizing:border-box;width:100%;margin:12px 0}}input,button{{padding:12px;border-radius:6px;border:1px solid #64748b}}button{{background:#0284c7;color:white;cursor:pointer}}p{{line-height:1.5}}</style><main><h1>Astral Identity</h1><p>{message}</p><form method="post"><input type="hidden" name="transaction" value="{ticket}"><label>Usuario corporativo<input name="username" autocomplete="username" required maxlength="256"></label><label>Senha<input name="password" type="password" autocomplete="current-password" required maxlength="4096"></label><button type="submit">Entrar e continuar</button></form></main></html>"#)).into_response());
    response.headers_mut().insert("content-security-policy", HeaderValue::from_static("default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'"));
    response
}
async fn authorize(
    State(provider): State<Arc<Provider>>,
    headers: HeaderMap,
    Query(request): Query<Authorization>,
) -> Response {
    if !provider.validate(&request) {
        return error(StatusCode::BAD_REQUEST, "invalid_request");
    }
    let mut store = provider.store.lock().await;
    store.prune();
    if let Some(identity) = cookie(&headers, "AUTH_OAUTH_SESSION")
        .and_then(|key| store.sessions.get(&key))
        .map(|s| s.identity.clone())
    {
        drop(store);
        return provider.grant(request, identity).await;
    }
    if store.full() {
        return error(StatusCode::SERVICE_UNAVAILABLE, "temporarily_unavailable");
    }
    let ticket = match random() {
        Ok(v) => v,
        Err(_) => return error(StatusCode::INTERNAL_SERVER_ERROR, "server_error"),
    };
    let secure = request.redirect_uri.starts_with("https://");
    store.logins.insert(
        ticket.clone(),
        LoginTransaction {
            request,
            expires: Instant::now() + TRANSACTION_TTL,
            attempts: 0,
        },
    );
    let mut response = login_page(&ticket, false);
    set_cookie(&mut response, "AUTH_OAUTH_LOGIN", &ticket, secure, 300);
    response
}
#[derive(Deserialize)]
struct Login {
    transaction: String,
    username: String,
    password: String,
}
async fn login(
    State(provider): State<Arc<Provider>>,
    headers: HeaderMap,
    Form(form): Form<Login>,
) -> Response {
    if form.username.len() > 256 || form.password.len() > 4096 || form.transaction.len() != 43 {
        return error(StatusCode::BAD_REQUEST, "invalid_request");
    }
    if cookie(&headers, "AUTH_OAUTH_LOGIN").as_deref() != Some(&form.transaction) {
        return error(StatusCode::BAD_REQUEST, "invalid_request");
    }
    let request = {
        let mut store = provider.store.lock().await;
        store.prune();
        match store.logins.remove(&form.transaction) {
            Some(mut transaction) if transaction.attempts < 5 => {
                transaction.attempts += 1;
                let request = transaction.request.clone();
                store.logins.insert(form.transaction.clone(), transaction);
                request
            }
            _ => return error(StatusCode::BAD_REQUEST, "invalid_request"),
        }
    };
    let _permit = match provider.login_slots.try_acquire() {
        Ok(permit) => permit,
        Err(_) => return error(StatusCode::SERVICE_UNAVAILABLE, "temporarily_unavailable"),
    };
    let credentials = Credentials::new(form.username, form.password);
    let mut identity = match tokio::time::timeout(
        Duration::from_secs(10),
        provider.identity.authenticate(&credentials),
    )
    .await
    {
        Ok(Ok(identity)) => identity,
        _ => return login_page(&form.transaction, true),
    };
    if let Some(privileges) = &provider.privileges {
        identity.has_wheel = !identity.provider.to_uppercase().starts_with("POSTGRES")
            && privileges.check_user_in_wheel(&identity.username).1;
    }
    // Consume transaction atomically after async authentication, preventing concurrent replay.
    let mut store = provider.store.lock().await;
    if store
        .logins
        .remove(&form.transaction)
        .filter(|t| t.expires > Instant::now())
        .is_none()
    {
        return error(StatusCode::BAD_REQUEST, "invalid_request");
    }
    let session = match random() {
        Ok(v) => v,
        Err(_) => return error(StatusCode::INTERNAL_SERVER_ERROR, "server_error"),
    };
    if store.full() {
        return error(StatusCode::SERVICE_UNAVAILABLE, "temporarily_unavailable");
    }
    store.sessions.insert(
        session.clone(),
        Session {
            identity: identity.clone(),
            expires: Instant::now() + SESSION_TTL,
        },
    );
    drop(store);
    let secure = request.redirect_uri.starts_with("https://");
    let mut response = provider.grant(request, identity).await;
    set_cookie(
        &mut response,
        "AUTH_OAUTH_SESSION",
        &session,
        secure,
        SESSION_TTL.as_secs(),
    );
    set_cookie(&mut response, "AUTH_OAUTH_LOGIN", "", secure, 0);
    response
}
#[derive(Deserialize)]
struct TokenRequest {
    grant_type: String,
    code: String,
    redirect_uri: String,
    code_verifier: String,
    client_id: String,
    client_secret: String,
}
async fn token(State(provider): State<Arc<Provider>>, Form(form): Form<TokenRequest>) -> Response {
    let valid_client = provider.clients.iter().any(|c| {
        c.client_id == form.client_id
            && constant_eq(c.client_secret.as_bytes(), form.client_secret.as_bytes())
    });
    if !valid_client {
        return error(StatusCode::UNAUTHORIZED, "invalid_client");
    }
    if form.grant_type != "authorization_code" {
        return error(StatusCode::BAD_REQUEST, "unsupported_grant_type");
    }
    let mut store = provider.store.lock().await;
    store.prune();
    // A code presented by another client cannot consume the rightful client's code.
    if store
        .codes
        .get(&form.code)
        .map(|g| g.request.client_id.as_str())
        != Some(&form.client_id)
    {
        return error(StatusCode::BAD_REQUEST, "invalid_grant");
    }
    let grant = match store.codes.remove(&form.code) {
        Some(v) => v,
        None => return error(StatusCode::BAD_REQUEST, "invalid_grant"),
    };
    let verifier_valid = (43..=128).contains(&form.code_verifier.len())
        && form
            .code_verifier
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"-._~".contains(&b));
    let challenge = URL_SAFE_NO_PAD.encode(sha256(form.code_verifier.as_bytes()));
    if !verifier_valid
        || grant.request.redirect_uri != form.redirect_uri
        || !constant_eq(
            challenge.as_bytes(),
            grant.request.code_challenge.as_bytes(),
        )
    {
        return error(StatusCode::BAD_REQUEST, "invalid_grant");
    }
    if store.full() {
        return error(StatusCode::SERVICE_UNAVAILABLE, "temporarily_unavailable");
    }
    let access = match random() {
        Ok(v) => v,
        Err(_) => return error(StatusCode::INTERNAL_SERVER_ERROR, "server_error"),
    };
    store.tokens.insert(
        access.clone(),
        Access {
            identity: grant.identity,
            scope: grant.request.scope.clone(),
            expires: Instant::now() + TOKEN_TTL,
        },
    );
    no_store(Json(json!({"access_token":access,"token_type":"Bearer","expires_in":TOKEN_TTL.as_secs(),"scope":grant.request.scope})).into_response())
}
async fn userinfo(State(provider): State<Arc<Provider>>, headers: HeaderMap) -> Response {
    let bearer = headers
        .get(header::AUTHORIZATION)
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "));
    let mut store = provider.store.lock().await;
    store.prune();
    match bearer.and_then(|v| store.tokens.get(v)) {
        Some(access) => {
            let mut identity = access.identity.clone();
            if !access.scope.split_whitespace().any(|s| s == "groups") {
                identity.groups.clear();
            }
            no_store(Json(identity).into_response())
        }
        None => error(StatusCode::UNAUTHORIZED, "invalid_token"),
    }
}
async fn logout(State(provider): State<Arc<Provider>>, headers: HeaderMap) -> Response {
    if let Some(key) = cookie(&headers, "AUTH_OAUTH_SESSION") {
        provider.store.lock().await.sessions.remove(&key);
    }
    let mut response = no_store(StatusCode::NO_CONTENT.into_response());
    set_cookie(&mut response, "AUTH_OAUTH_SESSION", "", true, 0);
    response
}
async fn metadata(State(provider): State<Arc<Provider>>) -> Response {
    let issuer = match &provider.issuer {
        Some(v) => v.as_str().trim_end_matches('/'),
        None => return error(StatusCode::SERVICE_UNAVAILABLE, "oauth_not_configured"),
    };
    no_store(
        Json(json!({"issuer":issuer,
        "authorization_endpoint":format!("{issuer}/oauth2/authorize"),
        "token_endpoint":format!("{issuer}/oauth2/token"),
        "userinfo_endpoint":format!("{issuer}/oauth2/userinfo"),
        "response_types_supported":["code"],"grant_types_supported":["authorization_code"],
        "token_endpoint_auth_methods_supported":["client_secret_post"],
        "code_challenge_methods_supported":["S256"],"scopes_supported":["profile","groups"]}))
        .into_response(),
    )
}
pub fn router(provider: Arc<Provider>) -> Router {
    Router::new()
        .route("/.well-known/oauth-authorization-server", get(metadata))
        .route("/oauth2/authorize", get(authorize).post(login))
        .route("/oauth2/token", axum::routing::post(token))
        .route("/oauth2/userinfo", get(userinfo))
        .route("/oauth2/logout", axum::routing::post(logout))
        .with_state(provider)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::AuthError;
    struct TestIdentity;
    #[async_trait::async_trait]
    impl IdentityProvider for TestIdentity {
        async fn authenticate(&self, credentials: &Credentials) -> Result<Identity, AuthError> {
            if credentials.password != "correct-password" {
                return Err(AuthError::MissingCredentials);
            }
            Ok(Identity::with_privileges(
                "GUID-123",
                "alice@ad.local",
                "AD",
                vec!["Domain Users".into()],
                false,
                false,
            ))
        }
    }
    async fn fixture() -> (Arc<Provider>, String, reqwest::Client) {
        let provider = Arc::new(
            Provider::new(
                vec![Client {
                    client_id: "astral-hci".into(),
                    client_secret: "c".repeat(48),
                    redirect_uris: vec!["http://127.0.0.1/callback".into()],
                }],
                Arc::new(TestIdentity),
            )
            .unwrap(),
        );
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let base = format!("http://{}", listener.local_addr().unwrap());
        let service = router(provider.clone());
        tokio::spawn(async {
            axum::serve(listener, service).await.unwrap();
        });
        (
            provider,
            base,
            reqwest::Client::builder()
                .redirect(reqwest::redirect::Policy::none())
                .build()
                .unwrap(),
        )
    }
    fn authorization(base: &str, redirect: &str) -> Url {
        let mut url = Url::parse(&format!("{base}/oauth2/authorize")).unwrap();
        url.query_pairs_mut()
            .append_pair("response_type", "code")
            .append_pair("client_id", "astral-hci")
            .append_pair("redirect_uri", redirect)
            .append_pair("scope", "profile groups")
            .append_pair("state", "state-bound-to-browser-1234567890")
            .append_pair(
                "code_challenge",
                &URL_SAFE_NO_PAD.encode(sha256("v".repeat(43).as_bytes())),
            )
            .append_pair("code_challenge_method", "S256");
        url
    }
    fn cookies(response: &reqwest::Response) -> String {
        response
            .headers()
            .get_all(header::SET_COOKIE)
            .iter()
            .map(|v| v.to_str().unwrap().split(';').next().unwrap())
            .collect::<Vec<_>>()
            .join("; ")
    }
    fn code(response: &reqwest::Response) -> String {
        Url::parse(response.headers()[header::LOCATION].to_str().unwrap())
            .unwrap()
            .query_pairs()
            .find(|(key, _)| key == "code")
            .unwrap()
            .1
            .to_string()
    }
    async fn exchange(
        http: &reqwest::Client,
        base: &str,
        code: &str,
        verifier: &str,
        redirect: &str,
        secret: &str,
    ) -> reqwest::Response {
        http.post(format!("{base}/oauth2/token"))
            .form(&[
                ("grant_type", "authorization_code"),
                ("code", code),
                ("client_id", "astral-hci"),
                ("client_secret", secret),
                ("redirect_uri", redirect),
                ("code_verifier", verifier),
            ])
            .send()
            .await
            .unwrap()
    }
    #[tokio::test]
    async fn authorization_code_pkce_login_sso_userinfo_and_replay_protection() {
        let (provider, base, http) = fixture().await;
        let auth = authorization(&base, "http://127.0.0.1/callback");
        let invalid = http
            .get(authorization(&base, "https://attacker.invalid/callback"))
            .send()
            .await
            .unwrap();
        assert_eq!(invalid.status(), 400);
        assert!(!invalid.headers().contains_key(header::LOCATION));
        let page = http.get(auth.clone()).send().await.unwrap();
        let login_cookie = cookies(&page);
        let html = page.text().await.unwrap();
        let transaction = html
            .split("name=\"transaction\" value=\"")
            .nth(1)
            .unwrap()
            .split('"')
            .next()
            .unwrap();
        let form = [
            ("transaction", transaction),
            ("username", "alice@ad.local"),
            ("password", "correct-password"),
        ];
        assert_eq!(
            http.post(auth.clone())
                .form(&form)
                .send()
                .await
                .unwrap()
                .status(),
            400
        );
        let invalid_login = http
            .post(auth.clone())
            .header(header::COOKIE, &login_cookie)
            .form(&[
                ("transaction", transaction),
                ("username", "alice"),
                ("password", "wrong"),
            ])
            .send()
            .await
            .unwrap();
        assert_eq!(invalid_login.status(), 200);
        assert!(!invalid_login.headers().contains_key(header::LOCATION));
        let login = http
            .post(auth.clone())
            .header(header::COOKIE, login_cookie)
            .form(&form)
            .send()
            .await
            .unwrap();
        assert_eq!(login.status(), 303);
        let session = cookies(&login);
        let first_code = code(&login);
        let secret = "c".repeat(48);
        let verifier = "v".repeat(43);
        let redirect = "http://127.0.0.1/callback";
        assert_eq!(
            exchange(&http, &base, &first_code, &verifier, redirect, "bad-secret")
                .await
                .status(),
            401
        );
        assert_eq!(
            exchange(
                &http,
                &base,
                &first_code,
                &"x".repeat(43),
                redirect,
                &secret
            )
            .await
            .status(),
            400
        );
        assert_eq!(
            exchange(&http, &base, &first_code, &verifier, redirect, &secret)
                .await
                .status(),
            400
        );
        // A later authorization reuses only the provider's own HttpOnly session.
        let authorized = http
            .get(auth.clone())
            .header(header::COOKIE, &session)
            .send()
            .await
            .unwrap();
        assert_eq!(authorized.status(), 303);
        let good_code = code(&authorized);
        let token = exchange(&http, &base, &good_code, &verifier, redirect, &secret).await;
        assert_eq!(token.status(), 200);
        assert_eq!(token.headers()[header::CACHE_CONTROL], "no-store");
        let body: serde_json::Value = token.json().await.unwrap();
        assert!(body.get("id_token").is_none());
        let identity: serde_json::Value = http
            .get(format!("{base}/oauth2/userinfo"))
            .bearer_auth(body["access_token"].as_str().unwrap())
            .send()
            .await
            .unwrap()
            .json()
            .await
            .unwrap();
        assert_eq!(identity["identityId"], "GUID-123");
        assert_eq!(identity["provider"], "AD");
        assert_eq!(identity["isAdmin"], false);
        assert_eq!(
            exchange(&http, &base, &good_code, &verifier, redirect, &secret)
                .await
                .status(),
            400
        );
        assert_eq!(
            http.get(format!("{base}/oauth2/userinfo"))
                .bearer_auth("fake-token")
                .send()
                .await
                .unwrap()
                .status(),
            401
        );
        let another = http
            .get(auth.clone())
            .header(header::COOKIE, &session)
            .send()
            .await
            .unwrap();
        let expired = code(&another);
        provider
            .store
            .lock()
            .await
            .codes
            .get_mut(&expired)
            .unwrap()
            .expires = Instant::now() - Duration::from_secs(1);
        assert_eq!(
            exchange(&http, &base, &expired, &verifier, redirect, &secret)
                .await
                .status(),
            400
        );
        let another = http
            .get(auth)
            .header(header::COOKIE, session)
            .send()
            .await
            .unwrap();
        assert_eq!(
            exchange(
                &http,
                &base,
                &code(&another),
                &verifier,
                "https://attacker.invalid",
                &secret
            )
            .await
            .status(),
            400
        );
    }
    #[test]
    fn reject_unsafe_redirect_registrations() {
        for redirect in [
            "http://example.com/cb",
            "https://example.com/cb#fragment",
            "https://user@example.com/cb",
        ] {
            assert!(Provider::new(
                vec![Client {
                    client_id: "id".into(),
                    client_secret: "s".repeat(40),
                    redirect_uris: vec![redirect.into()]
                }],
                Arc::new(TestIdentity)
            )
            .is_err());
        }
    }
}
