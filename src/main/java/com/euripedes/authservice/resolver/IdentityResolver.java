package com.euripedes.authservice.resolver;

import com.euripedes.authservice.contract.IdentityDto;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class IdentityResolver {
    private final LdapTemplate ldapTemplate;
    public IdentityResolver(LdapTemplate ldapTemplate){this.ldapTemplate=ldapTemplate;}
    public IdentityDto resolve(String username,String provider,GroupResolver groupResolver){
        ContextMapper<IdentityDto> mapper = context -> {
            DirContextOperations ctx = (DirContextOperations) context;
            DirContextAdapter adapter = (DirContextAdapter) ctx;
            return new IdentityDto(
                    ctx.getNameInNamespace(),
                    username,
                    provider,
                    groupResolver.resolve(adapter, ctx.getAttributes()));
        };
        return ldapTemplate.search(
            LdapQueryBuilder.query().where("objectClass").is("user").and("sAMAccountName").is(username),
            mapper)
            .stream().findFirst().orElseThrow(() -> new IdentityNotFoundException(username));
    }
}