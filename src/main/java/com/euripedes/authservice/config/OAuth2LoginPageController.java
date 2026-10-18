package com.euripedes.authservice.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tela de login do authorization server.
 *
 * <p>O Grafana autentica pelo fluxo OAuth2/OIDC deste servico, e o fluxo
 * comeca aqui: o navegador abre {@code /oauth2/authorize}, cai nesta
 * pagina, a pessoa entra com a conta do AD e volta para o Grafana.
 *
 * <p>E' um controller, e nao um template, porque o login acontece dentro do
 * iframe do painel, dentro do ERP. O HTML vem inline, sem CSS externo, para
 * o navegador nao pedir nenhum arquivo alem do proprio formulario.
 */
@RestController
@ConditionalOnProperty(prefix = "auth.oauth2", name = "enabled", havingValue = "true")
public class OAuth2LoginPageController {

    static final String LOGIN_PATH = "/oauth2/login";

    @GetMapping(value = LOGIN_PATH, produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage(HttpServletRequest request) {
        boolean erro = request.getParameter("error") != null;
        return "<!doctype html>"
                + "<html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Entrar</title><style>"
                + ":root{color-scheme:dark}"
                + "*{box-sizing:border-box}"
                + "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:#111217;color:#d8d9da;font:14px/1.4 -apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,sans-serif}"
                + "form{width:100%;max-width:320px;padding:24px}"
                + "h1{font-size:17px;font-weight:500;margin:0 0 4px;color:#fff}"
                + "p.sub{margin:0 0 20px;color:#9fa7b3;font-size:13px}"
                + "label{display:block;margin:0 0 6px;color:#9fa7b3;font-size:12px}"
                + "input{width:100%;padding:9px 10px;margin:0 0 16px;border-radius:3px;"
                + "border:1px solid #343741;background:#0b0c0e;color:#d8d9da;font-size:14px}"
                + "input:focus{outline:none;border-color:#6e9fff}"
                + "button{width:100%;padding:10px;border:0;border-radius:3px;cursor:pointer;"
                + "background:#3274d9;color:#fff;font-size:14px;font-weight:500}"
                + ".erro{margin:0 0 16px;padding:9px 10px;border-radius:3px;background:#3d1f22;"
                + "border:1px solid #6d3b41;color:#ff8f8f;font-size:13px}"
                + "</style></head><body>"
                + "<form method=\"post\" action=\"" + LOGIN_PATH + "\">"
                + "<h1>Brasil SaaS &middot; Autentica&ccedil;&atilde;o</h1>"
                + "<p class=\"sub\">Use a mesma conta do sistema.</p>"
                + (erro ? "<p class=\"erro\">Usu&aacute;rio ou senha inv&aacute;lidos.</p>" : "")
                + "<label for=\"username\">Usu&aacute;rio</label>"
                + "<input id=\"username\" name=\"username\" autocomplete=\"username\" autofocus required>"
                + "<label for=\"password\">Senha</label>"
                + "<input id=\"password\" name=\"password\" type=\"password\" autocomplete=\"current-password\" required>"
                + "<button type=\"submit\">Entrar</button>"
                + "</form></body></html>";
    }
}