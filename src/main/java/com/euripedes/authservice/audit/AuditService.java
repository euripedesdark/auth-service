package com.euripedes.authservice.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private static final Logger log=LoggerFactory.getLogger(AuditService.class);
    public void authenticationSuccess(String username,String provider){log.info("Authentication successful: provider={}, username={}",provider,username);}
    public void authenticationFailure(String provider){log.warn("Authentication failed or provider unavailable: provider={}",provider);}
}
