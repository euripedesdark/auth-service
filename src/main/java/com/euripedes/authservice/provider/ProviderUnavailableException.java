package com.euripedes.authservice.provider;

public class ProviderUnavailableException extends RuntimeException {
    private final String provider;
    public ProviderUnavailableException(String provider){super("Authentication provider unavailable: "+provider);this.provider=provider;}
    public ProviderUnavailableException(String provider,Throwable cause){super("Authentication provider unavailable: "+provider,cause);this.provider=provider;}
    public String getProvider(){return provider;}
}
