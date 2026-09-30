package com.euripedes.authservice.domain.ldap;

import com.euripedes.authservice.domain.model.IdentityDto;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class LdapGroupResolver {

    private final LdapTemplate ldapTemplate;

    public LdapGroupResolver(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    public List<String> resolveGroups(String username) {
        LdapQuery query = LdapQueryBuilder.query()
                .base("OU=BrasilCloud,DC=srvcloud,DC=cloud")
                .filter("(&(objectClass=user)(sAMAccountName=" + username + "))")
                .countOfMatches();

        // Get the groups the user is member of
        List<String> groups = ldapTemplate.search(
                "OU=BrasilCloud,DC=srvcloud,DC=cloud",
                "(objectClass/group)",
                new String[]{"member"},
                (ctx, attrs) -> {
                    String dn = ctx.getNameInNamespace();
                    return dn;
                });

        // Extract group names from DNs
        return groups.stream()
                .map(dn -> dn.replace("CN=", "").replace(",OU=BrasilCloud,DC=srvcloud,DC=cloud", ""))
                .collect(Collectors.toList());
    }

    public IdentityDto getIdentity(String username) {
        IdentityDto identity = new IdentityDto();
        identity.setUsername(username);
        identity.setProvider("AD");
        identity.setGroups(resolveGroups(username));
        identity.setIdentityId("uid=" + username + ",OU=Users,DC=srvcloud,DC=cloud");
        return identity;
    }
