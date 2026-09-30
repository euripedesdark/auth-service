package com.euripedes.authservice.resolver;
public class IdentityNotFoundException extends RuntimeException {
    public IdentityNotFoundException(String username){super("Identity not found: "+username);}
}
