package com.euripedes.authservice.resolver;

import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.stereotype.Component;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.util.ArrayList;
import java.util.List;

@Component
public class GroupResolver {
    public List<String> resolve(DirContextAdapter context,Attributes attributes){
        List<String> groups=new ArrayList<>();
        try{
            Attribute memberOf=attributes.get("memberOf");
            Attribute primaryGroup = attributes.get("primaryGroupID");
            if (primaryGroup != null && "513".equals(String.valueOf(primaryGroup.get())))
                groups.add("Domain Users");
            if(memberOf==null)return List.copyOf(groups);
            var values=memberOf.getAll();
            while(values.hasMore()){
                String dn=String.valueOf(values.next());
                int comma=dn.indexOf(',');
                String cn=dn.regionMatches(true,0,"CN=",0,3)?dn.substring(3,comma<0?dn.length():comma):dn;
                if(!cn.isBlank() && groups.stream().noneMatch(g -> g.equalsIgnoreCase(cn)))groups.add(cn);
            }
            return List.copyOf(groups);
        }catch(NamingException e){throw new IdentityResolutionException("Unable to resolve AD groups",e);}
    }
}
