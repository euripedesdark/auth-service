package com.euripedes.authservice.provider;

import com.euripedes.authservice.config.PgProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regra que nao pode quebrar em silencio: a conexao que confere a senha
 * digitada nao pode levar o certificado do servico.
 *
 * O pg_hba.conf deste servidor tem
 * {@code hostssl "brasil-saas" sa 127.0.0.1/32 cert clientcert=verify-full}
 * como primeira linha. Com o certificado no pedido, o Postgres casa essa
 * linha e o metodo cert ignora a senha: entrou-se com qualquer senha, ate
 * vazia. Se a credentialUrl() passar a carregar o certificado, o login volta
 * a aceitar senha errada sem nenhum aviso no console.
 */
class PgPropertiesCredentialUrlTest {

    private PgProperties completo(){
        PgProperties p=new PgProperties();
        p.setUrl("jdbc:postgresql://localhost:5432/brasil-saas");
        p.setSslMode("verify-ca");
        p.setSslRootCert("/etc/brasil-saas/pki/ca.crt");
        p.setSslCert("/etc/brasil-saas/pki/issued/sa.crt");
        p.setSslKey("/etc/brasil-saas/pki/private/sa.pk8");
        return p;
    }

    @Test void aUrlDeCredencialNaoTrazCertificadoNemChave(){
        String url=completo().credentialUrl();
        assertFalse(url.contains("sslcert"),"credentialUrl nao pode mandar sslcert: "+url);
        assertFalse(url.contains("sslkey"),"credentialUrl nao pode mandar sslkey: "+url);
        assertFalse(url.contains("sslrootcert"),"credentialUrl nao pode mandar sslrootcert: "+url);
    }

    @Test void aUrlDeCredencialExigeTls(){
        String url=completo().credentialUrl();
        assertTrue(url.contains("sslmode=require"),"a senha nao pode trafegar sem TLS: "+url);
    }

    @Test void aUrlDeServicoTrazOCertificado(){
        String url=completo().serviceUrl();
        assertTrue(url.contains("sslcert=/etc/brasil-saas/pki/issued/sa.crt"),url);
        assertTrue(url.contains("sslkey=/etc/brasil-saas/pki/private/sa.pk8"),url);
        assertTrue(url.contains("sslmode=verify-ca"),url);
    }

    @Test void asDuasUrlsNaoSeConfundem(){
        PgProperties p=completo();
        assertFalse(p.credentialUrl().equals(p.serviceUrl()),
                "se as duas urls fossem iguais, a checagem de senha usaria o certificado");
    }

    @Test void limpaParametrosDeCertificadoQueVenhamNaPropriaUrl(){
        // Se alguem montar AUTH_PG_URL ja com sslcert, a limpeza tem de
        // remover. Do contrario a excecao volta a existir.
        PgProperties p=new PgProperties();
        p.setUrl("jdbc:postgresql://localhost:5432/brasil-saas"
                +"?sslmode=verify-ca&sslcert=/tmp/sa.crt&sslkey=/tmp/sa.pk8&sslrootcert=/tmp/ca.crt");
        String url=p.credentialUrl();
        assertFalse(url.contains("sslcert"),url);
        assertFalse(url.contains("sslkey"),url);
        assertFalse(url.contains("sslrootcert"),url);
        assertTrue(url.contains("sslmode=require"),url);
    }

    @Test void naoDeixaSoquerSobrarAmpersandOuInterrogacao(){
        PgProperties p=new PgProperties();
        p.setUrl("jdbc:postgresql://localhost:5432/brasil-saas?sslkey=/tmp/sa.pk8");
        String url=p.credentialUrl();
        assertFalse(url.endsWith("?"),url);
        assertFalse(url.endsWith("&"),url);
        assertTrue(url.contains("sslmode=require"),url);
    }

    @Test void semUrlConfiguradaNadaEconstruido(){
        PgProperties p=new PgProperties();
        assertTrue(p.credentialUrl()==null);
        assertTrue(p.serviceUrl()==null);
    }

    @Test void defaultsSaoOsEsperados(){
        PgProperties p=new PgProperties();
        assertTrue("sa".equals(p.getServiceUser()),
                "a role de servico precisa ser a que tem linha de certificado no pg_hba");
        assertTrue("require".equals(p.getCredentialSslMode()));
        assertTrue("brasil_saas".equals(p.getSchema()));
        assertTrue("BRASIL_SAAS_TODOS".equals(p.getAccessGroup()));
    }
}