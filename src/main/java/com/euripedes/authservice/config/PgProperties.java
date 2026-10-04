package com.euripedes.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuracao do provedor de banco (provider POSTGRES).
 *
 * Sao duas conexoes distintas, e a separacao e' o ponto de seguranca:
 *
 * <ul>
 *   <li><b>de servico</b> -- com o certificado do auth-service
 *       (AUTH_PG_SSL_CERT/KEY, sslmode=verify-ca). E' a mesma infraestrutura
 *       que o ERP usa para abrir o datasource, e serve para ler o hash da
 *       senha em bc_core_usuario.</li>
 *   <li><b>de credencial</b> -- sem certificado de cliente, so TLS. E' por
 *       aqui que a senha digitada na tela e' conferida pelo Postgres.</li>
 * </ul>
 *
 * Nunca misturar as duas. O pg_hba.conf deste servidor tem
 * {@code hostssl "brasil-saas" sa 127.0.0.1/32 cert clientcert=verify-full}
 * como primeira linha, e o metodo {@code cert} nao consulta senha nenhuma:
 * uma conexao com o certificado da role sa entra com qualquer senha, ate
 * vazia. Se a checagem de senha usasse essa conexao, o login seria uma
 * porta aberta. Por isso a checagem sempre vai sem certificado.
 *
 * O mesmo cuidado ja existe no ERP, em
 * PostgresRoleAuthenticationService.openRoleConnection.
 */
@Component
@ConfigurationProperties(prefix = "auth.pg")
public class PgProperties {

    /** URL base, sem usuario, sem senha e sem parametros de ssl. Ex.: jdbc:postgresql://localhost:5432/brasil-saas */
    private String url;

    /** Schema onde a tabela de usuarios vive. Ex.: brasil_saas */
    private String schema = "brasil_saas";

    /** TLS da conexao de servico: verify-ca, require, disable... */
    private String sslMode = "verify-ca";

    /** Certificado da CA que assina o certificado do servico. */
    private String sslRootCert;

    /** Certificado do servico (CN=sa). */
    private String sslCert;

    /** Chave privada do servico, em PKCS#8. */
    private String sslKey;

    /**
     * TLS da conexao de credencial. Padrao require: criptografa o trajeto sem
     * exigir certificado de cliente, que e' justamente o que permite ao
     * Postgres chegar na linha de senha.
     */
    private String credentialSslMode = "require";

    /**
     * Role da conexao de servico, a que autentica por certificado.
     *
     * Precisa ser explicita: sem ela o driver assume o usuario do sistema
     * operacional (root, quando o servico roda como root) e o Postgres
     * responde "no password was provided", porque so a role sa tem linha de
     * certificado no pg_hba.conf.
     */
    private String serviceUser = "sa";

    /**
     * Grupo devolvido a quem entra pelo banco. O ERP trata esse grupo como
     * acesso total. Vem de AUTH_PG_ACCESS_GROUP.
     */
    private String accessGroup = "BRASIL_SAAS_TODOS";

    /** Timeout de conexao, em segundos. */
    private int connectTimeoutSeconds = 5;

    /** Timeout de consulta, em segundos. */
    private int queryTimeoutSeconds = 10;

    public String getUrl(){return url;}
    public void setUrl(String url){this.url=url;}
    public String getSchema(){return schema;}
    public void setSchema(String schema){this.schema=schema;}
    public String getSslMode(){return sslMode;}
    public void setSslMode(String sslMode){this.sslMode=sslMode;}
    public String getSslRootCert(){return sslRootCert;}
    public void setSslRootCert(String sslRootCert){this.sslRootCert=sslRootCert;}
    public String getSslCert(){return sslCert;}
    public void setSslCert(String sslCert){this.sslCert=sslCert;}
    public String getSslKey(){return sslKey;}
    public void setSslKey(String sslKey){this.sslKey=sslKey;}
    public String getCredentialSslMode(){return credentialSslMode;}
    public void setCredentialSslMode(String credentialSslMode){this.credentialSslMode=credentialSslMode;}
    public String getServiceUser(){return serviceUser;}
    public void setServiceUser(String serviceUser){this.serviceUser=serviceUser;}
    public String getAccessGroup(){return accessGroup;}
    public void setAccessGroup(String accessGroup){this.accessGroup=accessGroup;}
    public int getConnectTimeoutSeconds(){return connectTimeoutSeconds;}
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds){this.connectTimeoutSeconds=connectTimeoutSeconds;}
    public int getQueryTimeoutSeconds(){return queryTimeoutSeconds;}
    public void setQueryTimeoutSeconds(int queryTimeoutSeconds){this.queryTimeoutSeconds=queryTimeoutSeconds;}

    /** URL da conexao de servico, com o certificado do auth-service. */
    public String serviceUrl(){
        if(url==null||url.isBlank())return null;
        String base=limpar(url);
        StringBuilder sb=new StringBuilder(base).append(base.contains("?")?'&':'?');
        sb.append("sslmode=").append(sslMode);
        if(temTexto(sslRootCert))sb.append("&sslrootcert=").append(sslRootCert);
        if(temTexto(sslCert))sb.append("&sslcert=").append(sslCert);
        if(temTexto(sslKey))sb.append("&sslkey=").append(sslKey);
        return sb.toString();
    }

    /**
     * URL da conexao de credencial: mesma base, sem nenhum parametro de
     * certificado, com o TLS da credential-ssl-mode.
     *
     * Remover sslcert/sslkey e o essencial. Com o certificado do servico no
     * pedido, o pg_hba casa a linha {@code cert} da role sa e a senha digitada
     * deixa de ser conferida -- qualquer uma passaria.
     */
    public String credentialUrl(){
        if(url==null||url.isBlank())return null;
        String base=limpar(url);
        return base+(base.contains("?")?"&":"?")+"sslmode="+credentialSslMode;
    }

    /** Tira da URL os parametros de certificado e o sslmode que vierem junto. */
    private static String limpar(String url){
        return url.replaceAll("(?i)([?&])sslcert=[^&]*&?","$1")
                  .replaceAll("(?i)([?&])sslkey=[^&]*&?","$1")
                  .replaceAll("(?i)([?&])sslrootcert=[^&]*&?","$1")
                  .replaceAll("(?i)([?&])sslmode=[^&]*&?","$1")
                  .replaceAll("[?&]+$","");
    }

    private static boolean temTexto(String valor){
        return valor!=null&&!valor.isBlank();
    }
}