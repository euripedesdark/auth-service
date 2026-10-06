package com.euripedes.authservice.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Controller
public class PageController {
    @GetMapping({"/admin", "/admin/"})
    public String admin(
            @RequestHeader(value = "X-Forwarded-Prefix", required = false) String prefix) {
        return "redirect:" + (prefix != null ? prefix : "") + "/admin/index.html";
    }

    @GetMapping({"/mfa", "/mfa/"})
    public String mfa(
            @RequestHeader(value = "X-Forwarded-Prefix", required = false) String prefix) {
        return "redirect:" + (prefix != null ? prefix : "") + "/mfa/index.html";
    }
}
