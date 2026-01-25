package com.myyak.service.authService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class RedirectUriValidator {

    private final List<String> allowedRedirectUris;
    private final String defaultRedirectUri;

    public RedirectUriValidator(
            @Value("${oauth.allowed-redirect-uris:myyak://oauth/callback}") List<String> allowedRedirectUris) {
        this.allowedRedirectUris = allowedRedirectUris;
        this.defaultRedirectUri = allowedRedirectUris.isEmpty() ? "myyak://oauth/callback" : allowedRedirectUris.get(0);
        log.info("Initialized RedirectUriValidator with allowed URIs: {}", allowedRedirectUris);
        log.info("Default redirect URI: {}", defaultRedirectUri);
    }

    /**
     * Checks if a redirect URI is allowed
     * @param redirectUri The redirect URI to validate
     * @return true if the URI is in the allowlist, false otherwise
     */
    public boolean isAllowed(String redirectUri) {
        if (redirectUri == null) {
            return false;
        }
        boolean allowed = allowedRedirectUris.contains(redirectUri);
        if (!allowed) {
            log.warn("Redirect URI not in allowlist: {}", redirectUri);
        }
        return allowed;
    }

    /**
     * Gets the default redirect URI (first in the list or fallback)
     * @return The default redirect URI
     */
    public String getDefaultRedirectUri() {
        return defaultRedirectUri;
    }
}
