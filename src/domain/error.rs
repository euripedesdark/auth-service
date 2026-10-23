use axum::http::StatusCode;
use serde_json::{json, Value};

#[derive(Debug)]
pub enum AuthError {
    InvalidCredentials(String),
    MissingCredentials,
    InvalidToken(String),
    MissingToken,
    ServiceUnavailable(String),
    Internal(String),
}

impl std::fmt::Display for AuthError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::InvalidCredentials(msg) => write!(f, "Invalid credentials: {}", msg),
            Self::MissingCredentials => write!(f, "Username and password required"),
            Self::InvalidToken(msg) => write!(f, "Invalid token: {}", msg),
            Self::MissingToken => write!(f, "Missing authorization token"),
            Self::ServiceUnavailable(msg) => write!(f, "Service unavailable: {}", msg),
            Self::Internal(msg) => write!(f, "Internal error: {}", msg),
        }
    }
}

impl std::error::Error for AuthError {}

impl AuthError {
    pub fn status_code(&self) -> StatusCode {
        match self {
            Self::InvalidCredentials(_) | Self::MissingToken | Self::InvalidToken(_) => StatusCode::UNAUTHORIZED,
            Self::MissingCredentials => StatusCode::BAD_REQUEST,
            Self::ServiceUnavailable(_) => StatusCode::SERVICE_UNAVAILABLE,
            Self::Internal(_) => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }

    pub fn to_json_response(&self) -> (StatusCode, axum::Json<Value>) {
        (self.status_code(), axum::Json(json!({"error": self.to_string()})))
    }
}
