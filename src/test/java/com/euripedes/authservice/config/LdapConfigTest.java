package com.euripedes.authservice.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class LdapConfigTest {
    @Test void rejectsPlainLdapWhenSecureModeIsEnabled(){
        LdapProperties p=properties("ldap://ad.example.test");
        assertThrows(IllegalStateException.class,()->new LdapConfig().ldapContextSource(p));
    }
    @Test void allowsLdapsWhenSecureModeIsEnabled(){
        LdapProperties p=properties("ldaps://ad.example.test");
        assertDoesNotThrow(()->new LdapConfig().ldapContextSource(p).getUrls());
    }
    private LdapProperties properties(String url){
        LdapProperties p=new LdapProperties();
        p.setUrl(url); p.setBase("DC=example,DC=test");
        p.setUserDn("CN=svc-auth,DC=example,DC=test"); p.setPassword("test");
        p.setUserSearchBase("DC=example,DC=test");
        return p;
    }
}
