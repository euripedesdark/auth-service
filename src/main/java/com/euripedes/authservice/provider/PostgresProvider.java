package com.euripedes.authservice.provider;

import com.euripedes.authservice.config.PgProperties;
import com.euripedes.authservice.contract.IdentityDto;
import com.euripedes.authservice.contract.LoginRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Properties;

/**
 * Provedor de identidade contra o banco do ERP (provider POSTGRES).
 *
 * A senha e' conferida pelo proprio Postgres: abre-se uma conexao com o
 * usuario e a senha que a pessoa digitou. Se o Postgres recusar, a credencial
 * esta errada; se aceitar, a pessoa existe no banco.
 *
 * <b>A conexao de checagem nunca leva o certificado do servico.</b> O
 * pg_hba.conf deste servidor tem
 * {@code hostssl "brasil-saas" sa 127.0.0.1/32 cert clientcert=verify-full}
 * como primeira linha, e o metodo {@code cert} ignora a senha: uma conexao
 * com o certificado da role sa entra com qualquer senha, ate vazia. Se a
 * checagem usasse o certificado, o login aceitaria senha errada. Ver
 * PgProperties.credentialUrl().
 *
 * Ha um segundo caminho, para o usuario que existe na aplicacao mas nao e
 * role do Postgres: comparar a senha com o hash BCrypt em
 * bc_core_usuario.senha_hash, lido pela conexao de servico. Quem entra por
 * qualquer um dos dois caminhos recebe o grupo de acesso total, definido em
 * AUTH_PG_ACCESS_GROUP; nao ha gerenciamento de perfil por enquanto. O
 * provider AD segue o caminho dele, com a role vinda do grupo do AD.
 */
@Component
public class PostgresProvider implements AuthProvider {

    public static final String NAME = "POSTGRES";

    private static final Logger log = LoggerFactory.getLogger(PostgresProvider.class);

    /** Tabela de usuarios do ERP. Sempre qualificada com o schema. */
    private static final String TABELA_USUARIOS = "bc_core_usuario";

    private final PgProperties properties;

    public PostgresProvider(PgProperties properties){this.properties=properties;}

    @Override
    public String name(){return NAME;}

    @Override
    public IdentityDto authenticate(LoginRequestDto request){
        String username = request.username().trim();

        if(!configurado())
            throw new ProviderUnavailableException(NAME);

        // 1) senha conferida pelo Postgres, como role.
        Optional<Boolean> superuser = validarNoPostgres(username, request.password());
        if(superuser.isPresent()){
            log.info("Login autenticado pelo Postgres (role): username='{}', provider='{}'",username,NAME);
            return identidade(username, superuser.get());
        }

        // 2) nao e role: a senha bate no hash guardado pela aplicacao?
        if(senhaBateNaTabela(username,request.password())){
            log.info("Login autenticado pelo hash da tabela de usuarios: username='{}', provider='{}'",
                    username,NAME);
            return identidade(username);
        }

        throw new BadCredentialsException("Credenciais invalidas");
    }

    @Override
    public IdentityDto resolve(String username){
        // O provedor nao guarda estado: quem chama ja autenticou antes. Aqui
        // so devolve a identidade com o grupo de acesso total.
        return identidade(username);
    }

    private boolean configurado(){
        return properties.getUrl()!=null&&!properties.getUrl().isBlank();
    }

    /**
     * Abre uma conexao sem certificado de cliente e com o usuario e a senha da
     * pessoa. O Postgres confere a senha ao aceitar: senha errada ou role
     * inexistente resultam em SQLException.
     */
    private Optional<Boolean> validarNoPostgres(String username,String password){
        Properties info = new Properties();
        info.setProperty("user",username);
        info.setProperty("password",password);
        info.setProperty("connectTimeout",String.valueOf(properties.getConnectTimeoutSeconds()));
        try(Connection c=DriverManager.getConnection(properties.credentialUrl(),info)){
            if (!c.isValid(3)) return Optional.empty();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT rolsuper FROM pg_catalog.pg_roles WHERE rolname = current_user")) {
                ps.setQueryTimeout(properties.getQueryTimeoutSeconds());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getBoolean("rolsuper")) : Optional.empty();
                }
            }
        }catch(SQLException e){
            // SQLException aqui e' senha errada, role inexistente ou role que
            // so aceita certificado. Nos tres casos a resposta e a mesma: nao
            // autenticado. E o comportamento certo: role de servico (sa) nao
            // deve conseguir entrar pela tela de login.
            log.debug("Postgres recusou as credenciais de '{}': {}",username,primeiraLinha(e));
            return Optional.empty();
        }catch(RuntimeException e){
            throw new ProviderUnavailableException(NAME,e);
        }
    }

    /**
     * Compara a senha com o hash em bc_core_usuario.senha_hash.
     *
     * O hash e' BCrypt, o mesmo formato do Spring Security. A comparacao fica
     * em Java para nao depender do pgcrypto, que pode nao estar instalado.
     */
    private boolean senhaBateNaTabela(String username,String password){
        String url = properties.serviceUrl();
        if(url==null)return false;

        String sql = "SELECT senha_hash, ativo FROM " + properties.getSchema() + "." + TABELA_USUARIOS
                + " WHERE username = ? LIMIT 1";

        try(Connection c=DriverManager.getConnection(url,propriedadesDeServico());
            PreparedStatement ps=c.prepareStatement(sql)){
            ps.setQueryTimeout(properties.getQueryTimeoutSeconds());
            ps.setString(1,username);
            try(ResultSet rs=ps.executeQuery()){
                if(!rs.next())return false;
                if(!rs.getBoolean("ativo")){
                    log.info("Usuario '{}' existe mas esta inativo",username);
                    return false;
                }
                return Bcrypt.matches(password,rs.getString("senha_hash"));
            }
        }catch(SQLException e){
            log.warn("Nao consegui consultar a tabela de usuarios para '{}': {}",
                    username,primeiraLinha(e));
            return false;
        }catch(RuntimeException e){
            throw new ProviderUnavailableException(NAME,e);
        }
    }

    /**
     * Propriedades da conexao de servico: a que autentica pelo certificado do
     * auth-service, sem senha.
     *
     * O user precisa vir explicito. Sem ele o driver usa o usuario do sistema
     * operacional -- root, quando o servico roda como root -- e o Postgres
     * responde "no password was provided", porque so a role configurada em
     * service-user tem linha de certificado no pg_hba.conf.
     */
    private Properties propriedadesDeServico(){
        Properties info = new Properties();
        if(properties.getServiceUser()!=null&&!properties.getServiceUser().isBlank())
            info.setProperty("user",properties.getServiceUser());
        info.setProperty("connectTimeout",String.valueOf(properties.getConnectTimeoutSeconds()));
        return info;
    }

    private IdentityDto identidade(String username){
        return identidade(username, false);
    }

    private IdentityDto identidade(String username, boolean superuser){
        String group = properties.getAccessGroup();
        List<String> groups = new ArrayList<>();
        if (group != null && !group.isBlank()) groups.add(group);
        // O privilegio vem da role autenticada, nunca do nome informado ou do hash ERP.
        if (superuser) groups.add("POSTGRES_SUPERUSER");
        return new IdentityDto(username,username,NAME,List.copyOf(groups));
    }

    private static String primeiraLinha(SQLException e){
        String mensagem=e.getMessage();
        if(mensagem==null)return e.getClass().getSimpleName();
        int quebra=mensagem.indexOf('\n');
        return quebra<0?mensagem:mensagem.substring(0,quebra);
    }

    /**
     * BCrypt sem acoplar o provider ao modulo de seguranca do Spring: o
     * unico requisito aqui e' comparar senha com hash.
     *
     * Se nao der para carregar o comparador, devolve false em vez de aceitar.
     * Aceitar sem comparacao seria devolver o login para qualquer senha.
     */
    static final class Bcrypt {

        private Bcrypt(){}

        static boolean matches(String raw,String hash){
            if(raw==null||hash==null)return false;
            String h=hash.trim();
            // Sem o prefixo $2a$/$2b$/$2y$ nao e' BCrypt, e nao ha o que
            // comparar de forma confiavel.
            if(h.length()<20||h.charAt(0)!='$')return false;
            try{
                Class<?> encoder=Class.forName("org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder");
                Object instance=encoder.getDeclaredConstructor().newInstance();
                Object ok=encoder.getMethod("matches",CharSequence.class,String.class).invoke(instance,raw,h);
                return Boolean.TRUE.equals(ok);
            }catch(Exception e){
                log.warn("Comparador BCrypt indisponivel ({}); senha nao validada",
                        e.getClass().getSimpleName());
                return false;
            }
        }
    }
}