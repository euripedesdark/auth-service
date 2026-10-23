use crate::domain::DomainInfo;

pub trait DomainResolver: Send + Sync {
    fn resolve(&self, domain: &str) -> DomainInfo;
}

pub struct DefaultDomainResolver;

impl DefaultDomainResolver {
    pub fn new() -> Self {
        Self
    }
}

impl DomainResolver for DefaultDomainResolver {
    fn resolve(&self, domain: &str) -> DomainInfo {
        DomainInfo {
            domain: domain.to_string(),
            ips: vec!["127.0.0.1".to_string(), "192.168.2.10".to_string()],
            ldap_url: "ldaps://127.0.0.1:636".to_string(),
            base_dn: "DC=srvcloud,DC=cloud".to_string(),
        }
    }
}
