package com.euripedes.authservice.config;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

@Configuration
public class LdapConfig {
    @Bean
    public LdapContextSource ldapContextSource(LdapProperties p) {
        if (p.isRequireSecure() && !p.getUrl().toLowerCase(java.util.Locale.ROOT).startsWith("ldaps://"))
            throw new IllegalStateException("Secure LDAP is required; AUTH_LDAP_URL must use ldaps://");
        LdapContextSource source=new LdapContextSource();
        source.setUrl(p.getUrl()); source.setBase(p.getBase());
        source.setUserDn(p.getUserDn()); source.setPassword(p.getPassword());
        source.setBaseEnvironmentProperties(Map.of(
            "com.sun.jndi.ldap.connect.timeout",String.valueOf(p.getConnectTimeoutMs()),
            "com.sun.jndi.ldap.read.timeout",String.valueOf(p.getReadTimeoutMs()),
            "java.naming.ldap.attributes.binary","objectGUID objectSid"));
        source.afterPropertiesSet();
        return source;
    }
    @Bean
    public LdapTemplate ldapTemplate(LdapContextSource source){
        LdapTemplate template=new LdapTemplate(source);
        template.setIgnorePartialResultException(true);
        template.setIgnoreNameNotFoundException(false);
        return template;
    }
}
