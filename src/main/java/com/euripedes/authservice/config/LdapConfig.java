package com.euripedes.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

@Configuration
public class LdapConfig {
    @Bean
    public LdapContextSource ldapContextSource(LdapProperties p) {
        LdapContextSource source=new LdapContextSource();
        source.setUrl(p.getUrl()); source.setBase(p.getBase());
        source.setUserDn(p.getUserDn()); source.setPassword(p.getPassword());
        source.afterPropertiesSet(); return source;
    }
    @Bean public LdapTemplate ldapTemplate(LdapContextSource source){return new LdapTemplate(source);}
}
