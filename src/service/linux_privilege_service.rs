use crate::config::AuthConfig;
use crate::domain::{
    AdAdminUser, AuthError, LinuxPrivilegeReport, SambaServiceStatus,
    SyncWheelRequest, SyncWheelResponse, UserPrivilegeVerification, VerifyBindRequest,
};
use async_trait::async_trait;
use chrono::Utc;
use ldap3::{LdapConnAsync, LdapConnSettings, Scope, SearchEntry};
use std::collections::HashSet;
use std::fs;
use std::path::Path;
use std::process::Command;
use std::sync::Arc;
use tracing::info;

#[async_trait]
pub trait LinuxPrivilegeService: Send + Sync {
    fn check_samba_status(&self) -> SambaServiceStatus;
    fn get_linux_wheel_members(&self) -> Vec<String>;
    fn check_user_exists_in_linux(&self, username: &str) -> bool;
    fn check_user_in_wheel(&self, username: &str) -> (bool, bool);
    async fn audit_privileges(&self) -> Result<LinuxPrivilegeReport, AuthError>;
    fn verify_user_privilege(
        &self,
        username: &str,
        provider: Option<&str>,
        groups: &[String],
    ) -> UserPrivilegeVerification;
    fn verify_bind_user(&self, req: &VerifyBindRequest) -> UserPrivilegeVerification;
    async fn sync_wheel_permission(&self, req: &SyncWheelRequest) -> Result<SyncWheelResponse, AuthError>;
}

pub struct DefaultLinuxPrivilegeService {
    config: Arc<AuthConfig>,
}

impl DefaultLinuxPrivilegeService {
    pub fn new(config: Arc<AuthConfig>) -> Self {
        Self { config }
    }

    fn read_wheel_members_from_etc_group() -> Vec<String> {
        let mut members = Vec::new();
        if let Ok(content) = fs::read_to_string("/etc/group") {
            for line in content.lines() {
                let trimmed = line.trim();
                if trimmed.starts_with("wheel:") {
                    let parts: Vec<&str> = trimmed.split(':').collect();
                    if parts.len() >= 4 {
                        for m in parts[3].split(',') {
                            let m_clean = m.trim().to_string();
                            if !m_clean.is_empty() && !members.contains(&m_clean) {
                                members.push(m_clean);
                            }
                        }
                    }
                }
            }
        }
        members
    }

    fn read_wheel_members_from_getent() -> Vec<String> {
        let mut members = Vec::new();
        if let Ok(out) = Command::new("getent").args(["group", "wheel"]).output() {
            if out.status.success() {
                let line = String::from_utf8_lossy(&out.stdout);
                let trimmed = line.trim();
                let parts: Vec<&str> = trimmed.split(':').collect();
                if parts.len() >= 4 {
                    for m in parts[3].split(',') {
                        let m_clean = m.trim().to_string();
                        if !m_clean.is_empty() && !members.contains(&m_clean) {
                            members.push(m_clean);
                        }
                    }
                }
            }
        }
        members
    }

    fn is_safe_username(u: &str) -> bool {
        !u.is_empty()
            && u.len() <= 64
            && u.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '_' || c == '-')
    }
}

#[async_trait]
impl LinuxPrivilegeService for DefaultLinuxPrivilegeService {
    fn check_samba_status(&self) -> SambaServiceStatus {
        let config_path = "/etc/samba/smb.conf".to_string();
        let is_configured = Path::new(&config_path).exists();

        let mut is_active = false;
        if let Ok(out) = Command::new("sudo").args(["/usr/bin/systemctl", "is-active", "samba"]).output() {
            if out.status.success() && String::from_utf8_lossy(&out.stdout).trim() == "active" {
                is_active = true;
            }
        }
        if !is_active {
            if let Ok(out) = Command::new("systemctl").args(["is-active", "samba"]).output() {
                if out.status.success() && String::from_utf8_lossy(&out.stdout).trim() == "active" {
                    is_active = true;
                }
            }
        }

        let message = if is_active && is_configured {
            "Samba Active Directory Domain Controller está ONLINE e operacional".to_string()
        } else if is_configured {
            "Samba Active Directory está configurado mas o serviço está INATIVO".to_string()
        } else {
            "Samba Active Directory NÃO está configurado no sistema host".to_string()
        };

        SambaServiceStatus {
            is_configured,
            is_active,
            config_path,
            ldap_url: self.config.ldap_url.clone(),
            base_dn: self.config.ldap_base.clone(),
            realm: "srvcloud.cloud".to_string(),
            message,
        }
    }

    fn get_linux_wheel_members(&self) -> Vec<String> {
        let mut members = Self::read_wheel_members_from_etc_group();
        for m in Self::read_wheel_members_from_getent() {
            if !members.contains(&m) {
                members.push(m);
            }
        }
        members
    }

    fn check_user_exists_in_linux(&self, username: &str) -> bool {
        if let Ok(content) = fs::read_to_string("/etc/passwd") {
            for line in content.lines() {
                if let Some(user) = line.split(':').next() {
                    if user.eq_ignore_ascii_case(username) {
                        return true;
                    }
                }
            }
        }
        if let Ok(out) = Command::new("getent").args(["passwd", username]).output() {
            if out.status.success() && !out.stdout.is_empty() {
                return true;
            }
        }
        false
    }

    fn check_user_in_wheel(&self, username: &str) -> (bool, bool) {
        let exists = self.check_user_exists_in_linux(username);
        let wheel_members = self.get_linux_wheel_members();
        let mut in_wheel = wheel_members.iter().any(|m| m.eq_ignore_ascii_case(username));

        if !in_wheel && exists {
            if let Ok(out) = Command::new("id").args(["-nG", username]).output() {
                if out.status.success() {
                    let groups_str = String::from_utf8_lossy(&out.stdout);
                    if groups_str.split_whitespace().any(|g| g.eq_ignore_ascii_case("wheel")) {
                        in_wheel = true;
                    }
                }
            }
        }

        (exists, in_wheel)
    }

    async fn audit_privileges(&self) -> Result<LinuxPrivilegeReport, AuthError> {
        let samba_status = self.check_samba_status();
        let linux_wheel_members = self.get_linux_wheel_members();

        let mut admin_users = Vec::new();

        if samba_status.is_active && samba_status.is_configured {
            let settings = LdapConnSettings::new().set_no_tls_verify(true);
            let ldap_res = LdapConnAsync::with_settings(settings, &self.config.ldap_url).await;

            let mut fetched_via_ldap = false;
            if let Ok((conn, mut ldap)) = ldap_res {
                ldap3::drive!(conn);
                if ldap.simple_bind(&self.config.ldap_user_dn, &self.config.ldap_password).await.is_ok() {
                    let filter = "(&(objectClass=user)(|(sAMAccountName=Administrator)(memberOf=CN=Domain Admins,CN=Users,DC=srvcloud,DC=cloud)(memberOf=CN=Administrators,CN=Builtin,DC=srvcloud,DC=cloud)(memberOf=CN=Enterprise Admins,CN=Users,DC=srvcloud,DC=cloud)))";
                    let attrs = vec!["sAMAccountName", "displayName", "memberOf", "distinguishedName"];

                    if let Ok(sr) = ldap.search(&self.config.ldap_base, Scope::Subtree, filter, attrs).await {
                        if let Ok((entries, _)) = sr.success() {
                            for entry in entries {
                                let se = SearchEntry::construct(entry);
                                let sam = se.attrs.get("sAMAccountName").and_then(|v| v.first()).cloned().unwrap_or_default();
                                if sam.is_empty() {
                                    continue;
                                }

                                let display_name = se.attrs.get("displayName").and_then(|v| v.first()).cloned();
                                let dn = se.dn.clone();

                                let mut groups = Vec::new();
                                let mut admin_roles = Vec::new();

                                if sam.eq_ignore_ascii_case("Administrator") {
                                    admin_roles.push("Administrator (Conta Principal)".to_string());
                                }

                                if let Some(member_of) = se.attrs.get("memberOf") {
                                    for m in member_of {
                                        let cn = if let Some(stripped) = m.strip_prefix("CN=") {
                                            if let Some(comma) = stripped.find(',') {
                                                &stripped[..comma]
                                            } else {
                                                stripped
                                            }
                                        } else {
                                            m.as_str()
                                        };
                                        if !cn.is_empty() {
                                            groups.push(cn.to_string());
                                            if cn.eq_ignore_ascii_case("Domain Admins") {
                                                admin_roles.push("Domain Admins".to_string());
                                            } else if cn.eq_ignore_ascii_case("Administrators") {
                                                admin_roles.push("Administrators (Builtin)".to_string());
                                            } else if cn.eq_ignore_ascii_case("Enterprise Admins") {
                                                admin_roles.push("Enterprise Admins".to_string());
                                            }
                                        }
                                    }
                                }

                                let (exists_in_linux, has_wheel_permission) = self.check_user_in_wheel(&sam);
                                let compliance_status = if has_wheel_permission {
                                    "COMPLIANT".to_string()
                                } else if exists_in_linux {
                                    "MISSING_WHEEL".to_string()
                                } else {
                                    "LINUX_USER_MISSING".to_string()
                                };

                                admin_users.push(AdAdminUser {
                                    username: sam,
                                    display_name,
                                    dn,
                                    is_admin: true,
                                    admin_roles,
                                    groups,
                                    exists_in_linux,
                                    has_wheel_permission,
                                    compliance_status,
                                });
                            }
                            fetched_via_ldap = !admin_users.is_empty();
                        }
                    }
                }
            }

            // Fallback via samba-tool se LDAP falhar ou não retornar
            if !fetched_via_ldap {
                let mut admin_names = HashSet::new();
                admin_names.insert("Administrator".to_string());

                if let Ok(out) = Command::new("sudo").args(["/usr/bin/samba-tool", "group", "listmembers", "Domain Admins"]).output() {
                    if out.status.success() {
                        for line in String::from_utf8_lossy(&out.stdout).lines() {
                            let u = line.trim();
                            if !u.is_empty() {
                                admin_names.insert(u.to_string());
                            }
                        }
                    }
                }

                for name in admin_names {
                    let (exists_in_linux, has_wheel_permission) = self.check_user_in_wheel(&name);
                    let compliance_status = if has_wheel_permission {
                        "COMPLIANT".to_string()
                    } else if exists_in_linux {
                        "MISSING_WHEEL".to_string()
                    } else {
                        "LINUX_USER_MISSING".to_string()
                    };

                    let mut admin_roles = Vec::new();
                    if name.eq_ignore_ascii_case("Administrator") {
                        admin_roles.push("Administrator (Conta Principal)".to_string());
                    }
                    admin_roles.push("Domain Admins".to_string());

                    admin_users.push(AdAdminUser {
                        username: name.clone(),
                        display_name: Some(name.clone()),
                        dn: format!("CN={},CN=Users,{}", name, self.config.ldap_base),
                        is_admin: true,
                        admin_roles,
                        groups: vec!["Domain Admins".to_string()],
                        exists_in_linux,
                        has_wheel_permission,
                        compliance_status,
                    });
                }
            }
        }

        Ok(LinuxPrivilegeReport {
            samba_status,
            admin_users,
            linux_wheel_members,
            postgres_superuser_policy: "ESTRITO: Superusuários PostgreSQL (rolsuper=true) são isolados e NÃO possuem privilégios no grupo wheel nem terminal no Linux.".to_string(),
            checked_at: Utc::now().to_rfc3339(),
        })
    }

    fn verify_user_privilege(
        &self,
        username: &str,
        provider: Option<&str>,
        groups: &[String],
    ) -> UserPrivilegeVerification {
        if provider.map(|p| p.eq_ignore_ascii_case("postgres") || p.eq_ignore_ascii_case("postgresql")).unwrap_or(false)
            || groups.iter().any(|g| g.eq_ignore_ascii_case("superuser") || g.eq_ignore_ascii_case("pg_roles"))
        {
            return UserPrivilegeVerification {
                username: username.to_string(),
                provider: "Postgres".to_string(),
                is_ad_admin: false,
                exists_in_linux: false,
                has_wheel_permission: false,
                can_access_terminal: false,
                compliance_status: "DENIED_POSTGRES".to_string(),
                message: "Acesso Negado: Superusuários PostgreSQL possuem privilégios restritos ao banco de dados e são proibidos de acesso ao grupo wheel ou terminal do host Linux.".to_string(),
            };
        }

        let is_ad_admin = username.eq_ignore_ascii_case("Administrator")
            || groups.iter().any(|g| {
                let lower = g.to_lowercase();
                lower.contains("admin") || lower.contains("administrators")
            });

        let (exists_in_linux, has_wheel_permission) = self.check_user_in_wheel(username);
        let can_access_terminal = is_ad_admin || has_wheel_permission || groups.iter().any(|g| g == "wheel");

        let compliance_status = if has_wheel_permission {
            "COMPLIANT".to_string()
        } else if is_ad_admin && exists_in_linux {
            "MISSING_WHEEL".to_string()
        } else if is_ad_admin && !exists_in_linux {
            "LINUX_USER_MISSING".to_string()
        } else {
            "STANDARD_USER".to_string()
        };

        let message = if has_wheel_permission {
            format!("Usuário '{}' possui privilégio concedido no grupo wheel do Linux.", username)
        } else if is_ad_admin {
            format!("Usuário '{}' é Administrador do Samba AD, porém ainda não possui permissão ativa no grupo wheel do Linux.", username)
        } else {
            format!("Usuário '{}' validado como operador padrão sem permissões de administração Linux.", username)
        };

        UserPrivilegeVerification {
            username: username.to_string(),
            provider: provider.unwrap_or("AD").to_string(),
            is_ad_admin,
            exists_in_linux,
            has_wheel_permission,
            can_access_terminal,
            compliance_status,
            message,
        }
    }

    fn verify_bind_user(&self, req: &VerifyBindRequest) -> UserPrivilegeVerification {
        let prov = req.provider.as_deref().unwrap_or("AD");
        self.verify_user_privilege(&req.username, Some(prov), &req.groups)
    }

    async fn sync_wheel_permission(&self, req: &SyncWheelRequest) -> Result<SyncWheelResponse, AuthError> {
        let username = req.username.trim();
        if !Self::is_safe_username(username) {
            return Err(AuthError::InvalidCredentials("Nome de usuário inválido para sincronização no host Linux.".to_string()));
        }

        let lower = username.to_lowercase();
        if lower == "postgres" || lower == "sa" || lower == "saas" || lower == "root" || lower == "auth" {
            return Err(AuthError::InvalidCredentials("Operação não permitida: Contas de serviço ou de banco de dados não podem receber privilégio wheel.".to_string()));
        }

        // Verifica status do Samba AD
        let samba_status = self.check_samba_status();
        if !samba_status.is_active || !samba_status.is_configured {
            return Err(AuthError::ServiceUnavailable("Samba Active Directory está inativo ou não configurado para validar administradores.".to_string()));
        }

        // Verifica se usuário é realmente administrador do AD
        let is_admin_name = username.eq_ignore_ascii_case("Administrator");
        let is_in_domain_admins = if is_admin_name {
            true
        } else {
            let out = Command::new("sudo").args(["/usr/bin/samba-tool", "group", "listmembers", "Domain Admins"]).output();
            if let Ok(o) = out {
                String::from_utf8_lossy(&o.stdout).lines().any(|l| l.trim().eq_ignore_ascii_case(username))
            } else {
                false
            }
        };

        if !is_in_domain_admins {
            return Err(AuthError::InvalidCredentials(format!(
                "Usuário '{}' não foi encontrado no grupo 'Domain Admins' nem como 'Administrator' no Samba AD.",
                username
            )));
        }

        let (exists, in_wheel) = self.check_user_in_wheel(username);
        if in_wheel {
            return Ok(SyncWheelResponse {
                success: true,
                username: username.to_string(),
                in_wheel: true,
                created_linux_user: false,
                message: format!("Usuário '{}' já pertence ao grupo wheel no host Linux.", username),
            });
        }

        let mut created_linux_user = false;

        if !exists {
            if !req.create_linux_user_if_missing {
                return Err(AuthError::InvalidCredentials(format!(
                    "Usuário '{}' não existe localmente no Linux. Habilite 'createLinuxUserIfMissing' para criá-lo automaticamente.",
                    username
                )));
            }

            let add_res = Command::new("sudo")
                .args(["/usr/sbin/useradd", "-M", "-s", "/bin/bash", "-G", "wheel", username])
                .output()
                .map_err(|e| AuthError::Internal(format!("Falha ao invocar useradd: {}", e)))?;

            if !add_res.status.success() {
                let err_msg = String::from_utf8_lossy(&add_res.stderr);
                return Err(AuthError::Internal(format!("Falha ao criar usuário Linux '{}': {}", username, err_msg.trim())));
            }
            created_linux_user = true;
            info!("Usuário Linux '{}' criado com sucesso e adicionado ao grupo wheel.", username);
        } else {
            let mod_res = Command::new("sudo")
                .args(["/usr/sbin/usermod", "-aG", "wheel", username])
                .output()
                .map_err(|e| AuthError::Internal(format!("Falha ao invocar usermod: {}", e)))?;

            if !mod_res.status.success() {
                let err_msg = String::from_utf8_lossy(&mod_res.stderr);
                return Err(AuthError::Internal(format!("Falha ao adicionar usuário '{}' ao grupo wheel: {}", username, err_msg.trim())));
            }
            info!("Usuário Linux '{}' adicionado com sucesso ao grupo wheel.", username);
        }

        let (_, verified_in_wheel) = self.check_user_in_wheel(username);

        Ok(SyncWheelResponse {
            success: verified_in_wheel,
            username: username.to_string(),
            in_wheel: verified_in_wheel,
            created_linux_user,
            message: format!("Privilégio do grupo wheel verificado e sincronizado com sucesso para o usuário '{}'.", username),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_read_wheel_members_from_etc_group() {
        let members = DefaultLinuxPrivilegeService::read_wheel_members_from_etc_group();
        // Na máquina dc-erp, euripedes ou root deve estar listado
        assert!(members.contains(&"euripedes".to_string()) || members.contains(&"root".to_string()));
    }

    #[test]
    fn test_is_safe_username() {
        assert!(DefaultLinuxPrivilegeService::is_safe_username("Administrator"));
        assert!(DefaultLinuxPrivilegeService::is_safe_username("admin-srvcloud"));
        assert!(DefaultLinuxPrivilegeService::is_safe_username("euripedes.silva"));
        assert!(!DefaultLinuxPrivilegeService::is_safe_username(""));
        assert!(!DefaultLinuxPrivilegeService::is_safe_username("user;rm -rf /"));
        assert!(!DefaultLinuxPrivilegeService::is_safe_username("user$(whoami)"));
    }

    #[test]
    fn test_postgres_superuser_strictly_denied() {
        let config = Arc::new(AuthConfig::load());
        let service = DefaultLinuxPrivilegeService::new(config);

        let check = service.verify_user_privilege("postgres", Some("Postgres"), &["superuser".to_string()]);
        assert_eq!(check.compliance_status, "DENIED_POSTGRES");
        assert!(!check.has_wheel_permission);
        assert!(!check.can_access_terminal);
        assert!(!check.is_ad_admin);
    }

    #[test]
    fn test_ad_admin_verification() {
        let config = Arc::new(AuthConfig::load());
        let service = DefaultLinuxPrivilegeService::new(config);

        let check = service.verify_user_privilege("Administrator", Some("AD"), &["Domain Admins".to_string()]);
        assert!(check.is_ad_admin);
        assert!(check.can_access_terminal);
    }

    #[test]
    fn test_verify_bind_from_ad_dns() {
        let config = Arc::new(AuthConfig::load());
        let service = DefaultLinuxPrivilegeService::new(config);

        let req = VerifyBindRequest {
            username: "admin-srvcloud".to_string(),
            provider: Some("AD".to_string()),
            groups: vec!["Domain Admins".to_string()],
            bind_source: Some("astral-ad-dns".to_string()),
        };

        let check = service.verify_bind_user(&req);
        assert!(check.is_ad_admin);
        assert!(check.can_access_terminal);
    }
}
