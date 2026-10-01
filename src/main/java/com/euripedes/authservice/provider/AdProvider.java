package com.euripedes.authservice.provider;

import com.euripedes.authservice.config.LdapProperties;
import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.contract.LoginRequestDto;
import com.euripedes.authservice.resolver.GroupResolver;
import com.euripedes.authservice.resolver.IdentityResolver;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class AdProvider implements AuthProvider, AuthenticationProvider {
    public static final String NAME="AD";
    private final LdapTemplate ldapTemplate;
    private final IdentityResolver identityResolver;
    private final GroupResolver groupResolver;
    private final LdapProperties properties;

    public AdProvider(LdapTemplate ldapTemplate,IdentityResolver identityResolver,GroupResolver groupResolver,LdapProperties properties){
        this.ldapTemplate=ldapTemplate;this.identityResolver=identityResolver;this.groupResolver=groupResolver;this.properties=properties;
    }
    public String name(){return NAME;}

    public IdentityDto authenticate(LoginRequestDto request){
        if(!NAME.equalsIgnoreCase(request.provider())) throw new ProviderUnavailableException(request.provider());
        try{
            ldapTemplate.authenticate(
                LdapQueryBuilder.query().where("sAMAccountName").is(request.username()),
                request.password());
            return identityResolver.resolve(request.username(),NAME,groupResolver);
        }catch(BadCredentialsException e){throw e;}
        catch(org.springframework.ldap.AuthenticationException e){throw new BadCredentialsException("Invalid credentials",e);}
        catch(DataAccessResourceFailureException e){throw new ProviderUnavailableException(NAME,e);}
        catch(RuntimeException e){throw new ProviderUnavailableException(NAME,e);}
    }

    public IdentityDto resolve(String username){
        try{return identityResolver.resolve(username,NAME,groupResolver);}
        catch(RuntimeException e){throw new ProviderUnavailableException(NAME,e);}
    }

    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if(!supports(authentication.getClass())) return null;
        try {
            IdentityDto identity=authenticate(new LoginRequestDto(authentication.getName(),String.valueOf(authentication.getCredentials()),NAME));
            return UsernamePasswordAuthenticationToken.authenticated(identity.username(),null,java.util.List.of());
        } catch (ProviderUnavailableException e) {
            throw new AuthenticationServiceException(e.getMessage(),e);
        }
    }
    public boolean supports(Class<?> type){return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);}
}
