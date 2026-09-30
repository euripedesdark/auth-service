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
            if(memberOf==null)return groups;
            var values=memberOf.getAll();
            while(values.hasMore()){
                String dn=String.valueOf(values.next());
                int comma=dn.indexOf(',');
                String cn=dn.regionMatches(true,0,"CN=",0,3)?dn.substring(3,comma<0?dn.length():comma):dn;
                if(!cn.isBlank())groups.add(cn);
            }
            return List.copyOf(groups);
        }catch(NamingException e){throw new IdentityResolutionException("Unable to resolve AD groups",e);}
    }
}
