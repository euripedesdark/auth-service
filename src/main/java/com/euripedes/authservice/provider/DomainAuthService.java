package com.euripedes.authservice.provider;

import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.resolver.GroupResolver;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/**
 * Autentica contra outro AD da rede pelo nome do domínio.
 * Resolve o domínio via DNS da máquina (SRV/A) e faz bind LDAPS direto
 * com a credencial do usuário — sem depender da config fixa do yml.
 */
@Service
public class DomainAuthService {
    private static final int TIMEOUT_MS = 5000;
    private final GroupResolver groupResolver;

    public DomainAuthService(GroupResolver groupResolver) {
        this.groupResolver = groupResolver;
    }

    public record Resolved(String domain, List<String> ips, String ldapUrl, String baseDn) {}

    public Resolved resolve(String domain) {
        String clean = domain.trim().toLowerCase(Locale.ROOT);
        List<String> ips = new ArrayList<>();
        try {
            for (InetAddress a : InetAddress.getAllByName(clean)) ips.add(a.getHostAddress());
        } catch (Exception e) {
            throw new ProviderUnavailableException("DNS:" + clean, e);
        }
        if (ips.isEmpty()) throw new ProviderUnavailableException("DNS:" + clean);
        return new Resolved(clean, List.copyOf(ips), "ldaps://" + clean + ":636", toBaseDn(clean));
    }

    public static String toBaseDn(String domain) {
        String[] parts = domain.trim().toLowerCase(Locale.ROOT).split("\\.");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append("dc=").append(p);
        }
        if (sb.length() == 0) throw new IllegalArgumentException("Dominio invalido");
        return sb.toString();
    }

    public IdentityDto authenticate(String username, String password, String domain) {
        Resolved r = resolve(domain);
        String user = username.trim();
        LdapContextSource src = new LdapContextSource();
        src.setUrl(r.ldapUrl());
        src.setBase(r.baseDn());
        src.setUserDn(user.contains("@") ? user : user + "@" + r.domain());
        src.setPassword(password);
        src.setBaseEnvironmentProperties(Map.of(
            "com.sun.jndi.ldap.connect.timeout", String.valueOf(TIMEOUT_MS),
            "com.sun.jndi.ldap.read.timeout", String.valueOf(TIMEOUT_MS)));
        try {
            src.afterPropertiesSet();
            // Bind com a própria credencial: se falhar, é credencial inválida.
            src.getContext(src.getUserDn(), src.getPassword());
        } catch (org.springframework.ldap.AuthenticationException e) {
            throw new BadCredentialsException("Invalid credentials", e);
        } catch (RuntimeException e) {
            throw new ProviderUnavailableException("AD:" + r.domain(), e);
        }
        LdapTemplate t = new LdapTemplate(src);
        t.setIgnorePartialResultException(true);
        try {
            ContextMapper<IdentityDto> mapper = context -> {
                DirContextOperations ctx = (DirContextOperations) context;
                return new IdentityDto(ctx.getNameInNamespace(), user, AdProvider.NAME,
                    groupResolver.resolve((DirContextAdapter) ctx, ctx.getAttributes()));
            };
            return t.search(LdapQueryBuilder.query().where("objectClass").is("user")
                    .and("sAMAccountName").is(user), mapper)
                .stream().findFirst()
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        } catch (BadCredentialsException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ProviderUnavailableException("AD:" + r.domain(), e);
        }
    }
}
