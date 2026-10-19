package com.euripedes.authservice.config;

import com.euripedes.authservice.contract.LoginRequestDto;
import com.euripedes.authservice.service.AuthenticationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@ConditionalOnProperty(prefix = "auth.oauth2", name = "enabled", havingValue = "true")
public class OAuth2LoginPageController {

    static final String LOGIN_PATH = "/oauth2/login";

    private final AuthenticationService authService;
    private final SsoCookieUtil ssoCookieUtil;

    public OAuth2LoginPageController(AuthenticationService authService, SsoCookieUtil ssoCookieUtil) {
        this.authService = authService;
        this.ssoCookieUtil = ssoCookieUtil;
    }

    @GetMapping(value = LOGIN_PATH, produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage(HttpServletRequest request) {
        boolean erro = request.getParameter("error") != null;
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String csrfFields = csrf == null ? ""
                : "<input type=\"hidden\" name=\"" + csrf.getParameterName() + "\" value=\"" + csrf.getToken() + "\">";

        String continueUrl = request.getParameter("continue");
        String continueField = continueUrl != null && !continueUrl.isBlank()
                ? "<input type=\"hidden\" name=\"continue\" value=\"" + URLEncoder.encode(continueUrl, StandardCharsets.UTF_8) + "\">"
                : "";

        String token = null;
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("sso_token".equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        String autoSubmit = "";
        if (token != null && !token.isBlank()) {
            autoSubmit = "<script>document.forms[0].submit();</script>";
        }

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
                + autoSubmit
                + "<form method=\"post\" action=\"" + LOGIN_PATH + "\">"
                + csrfFields
                + continueField
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

    @PostMapping(value = LOGIN_PATH)
    public void loginSubmit(@RequestParam String username,
                            @RequestParam String password,
                            @RequestParam(required = false) String continueUrl,
                            HttpServletRequest request,
                            HttpServletResponse response) throws Exception {
        try {
            var identity = authService.authenticate(new com.euripedes.authservice.contract.LoginRequestDto(username, password, "AD"));
            String token = new com.euripedes.authservice.service.TokenService(
                    new com.euripedes.authservice.config.ApiTokenEncoder(
                            new com.euripedes.authservice.config.TokenConfig().jwtSecretKey(
                                    new com.euripedes.authservice.config.TokenProperties())),
                    new com.euripedes.authservice.config.TokenProperties())
                    .issue(identity).accessToken();
            ssoCookieUtil.setToken(response, token);

            if (continueUrl != null && !continueUrl.isBlank()) {
                response.sendRedirect(continueUrl);
            } else {
                response.sendRedirect("/oauth2/authorize");
            }
        } catch (Exception e) {
            response.sendRedirect(LOGIN_PATH + "?error=1");
        }
    }
}
