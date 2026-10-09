package com.euripedes.authservice.resolver;

import org.junit.jupiter.api.Test;
import org.springframework.ldap.core.DirContextAdapter;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupResolverTest {
    @Test void resolvesMemberOfGroups(){
        BasicAttributes attributes=new BasicAttributes(true);
        BasicAttribute memberOf=new BasicAttribute("memberOf");
        memberOf.add("CN=GRP_ADMIN,OU=Groups,DC=srvcloud,DC=cloud");
        memberOf.add("CN=GRP_FINANCEIRO,OU=Groups,DC=srvcloud,DC=cloud");
        attributes.put(memberOf);
        var groups=new GroupResolver().resolve(new DirContextAdapter(),attributes);
        assertEquals(java.util.List.of("GRP_ADMIN","GRP_FINANCEIRO"),groups);
    }
    @Test void returnsEmptyWhenMemberOfIsMissing(){
        BasicAttributes attributes=new BasicAttributes(true);
        assertEquals(java.util.List.of(),new GroupResolver().resolve(new DirContextAdapter(),attributes));
    }
    @Test void grupoPrimarioDomainUsersNaoDependeDeMemberOf(){
        BasicAttributes attributes=new BasicAttributes(true);
        attributes.put("primaryGroupID", "513");
        assertEquals(java.util.List.of("Domain Users"),new GroupResolver().resolve(new DirContextAdapter(),attributes));
    }
    @Test void outroGrupoPrimarioNaoViraDomainUsers(){
        BasicAttributes attributes=new BasicAttributes(true);
        attributes.put("primaryGroupID", "514");
        assertEquals(java.util.List.of(),new GroupResolver().resolve(new DirContextAdapter(),attributes));
    }
}
