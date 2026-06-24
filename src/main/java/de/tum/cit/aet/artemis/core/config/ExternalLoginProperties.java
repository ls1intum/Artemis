package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for the external-client browser-delegated login handoff (e.g. the VS Code extension).
 * <p>
 * Lets an external client obtain a full Artemis JWT after the user authenticates in the browser with
 * any method (passkey, SAML SSO, password), using a one-time code + PKCE exchange. The feature is
 * <strong>disabled by default</strong>: with an empty {@link #allowedRedirectSchemes} no callback is
 * ever accepted.
 */
@Profile(PROFILE_CORE)
@Lazy
@Configuration
@ConfigurationProperties(prefix = "artemis.external-login")
public class ExternalLoginProperties {

    /**
     * Allowed custom URI schemes for the extension callback (e.g. {@code vscode}, {@code vscode-insiders}).
     * Empty (default) disables the feature. {@code http} and {@code https} are always rejected.
     */
    private List<String> allowedRedirectSchemes = List.of();

    /**
     * Optional allowlist of callback authorities (the host part, e.g. the extension id
     * {@code aet-tum.iris-thaumantias}). Empty means any authority is accepted for an allowed scheme.
     * Setting this prevents another extension from receiving the one-time code.
     */
    private List<String> allowedRedirectAuthorities = List.of();

    /**
     * @return the allowed callback URI schemes ({@code http}/{@code https} are always rejected)
     */
    public List<String> getAllowedRedirectSchemes() {
        return allowedRedirectSchemes;
    }

    /**
     * @param allowedRedirectSchemes the allowed callback URI schemes
     */
    public void setAllowedRedirectSchemes(List<String> allowedRedirectSchemes) {
        this.allowedRedirectSchemes = allowedRedirectSchemes;
    }

    /**
     * @return the optional allowlist of callback authorities (empty = any authority allowed)
     */
    public List<String> getAllowedRedirectAuthorities() {
        return allowedRedirectAuthorities;
    }

    /**
     * @param allowedRedirectAuthorities the allowed callback authorities
     */
    public void setAllowedRedirectAuthorities(List<String> allowedRedirectAuthorities) {
        this.allowedRedirectAuthorities = allowedRedirectAuthorities;
    }
}
