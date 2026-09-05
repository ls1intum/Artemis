package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Configuration properties for rate limiting functionality.
 *
 * <p>
 * This class binds the artemis.rate-limiting.* properties from application configuration
 * and provides type-safe access to rate limiting settings.
 * </p>
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
@ConfigurationProperties(prefix = "artemis.rate-limiting")
public class RateLimitingProperties {

    /**
     * Whether rate limiting is enabled globally.
     * Default: false (disabled)
     */
    private boolean enabled = false;

    /**
     * Addresses exempt from every rate limit, as literal IPv4 or IPv6 addresses or CIDR blocks.
     * <p>
     * The limits are per client address, which is the right shape for protecting against one abusive
     * client but the wrong shape for a load generator: a benchmark drives thousands of logins from a
     * single address and would be throttled almost immediately, so a run would measure the limiter
     * rather than the system. Listing that address here is the same exemption the reverse proxy
     * already supports for its own limits.
     * <p>
     * Empty by default. Only add an address that is under your control.
     */
    private List<String> exemptAddresses = new ArrayList<>();

    /**
     * Requests per minute for public endpoints.
     * If not specified, uses the default from {@link de.tum.cit.aet.artemis.core.security.RateLimitType#ACCOUNT_MANAGEMENT}.
     */
    private Integer accountManagementRequestsPerMinute;

    /**
     * Requests per minute for login-related endpoints.
     * If not specified, uses the default from {@link de.tum.cit.aet.artemis.core.security.RateLimitType#AUTHENTICATION}.
     */
    private Integer authenticationRequestsPerMinute;

    /**
     * Requests per minute for the unauthenticated login-options lookup.
     * If not specified, uses the default from {@link de.tum.cit.aet.artemis.core.security.RateLimitType#LOGIN_OPTIONS}.
     */
    private Integer loginOptionsRequestsPerMinute;

    /**
     * Requests per minute for the problem-statement rendering endpoint.
     * If not specified, uses the default from {@link de.tum.cit.aet.artemis.core.security.RateLimitType#PROBLEM_STATEMENT_RENDERING}.
     */
    private Integer problemStatementRenderingRequestsPerMinute;

    /**
     * Requests per minute for the AI search pipeline endpoint.
     * If not specified, uses the default from {@link de.tum.cit.aet.artemis.core.security.RateLimitType#AI_SEARCH_PIPELINE}.
     */
    private Integer aiSearchPipelineRequestsPerMinute;

    /**
     * Requests per minute for the build agent clone-token check on the git https path.
     * If not specified, uses the default from {@link de.tum.cit.aet.artemis.core.security.RateLimitType#BUILD_AGENT_CLONE_TOKEN}.
     */
    private Integer buildAgentCloneTokenRequestsPerMinute;

    /**
     * Requests per minute for the git-backed online-editor endpoints, counted per user.
     * If not specified, uses the default from {@link de.tum.cit.aet.artemis.core.security.RateLimitType#REPOSITORY_EDITOR}.
     */
    private Integer repositoryEditorRequestsPerMinute;

    public List<String> getExemptAddresses() {
        return exemptAddresses;
    }

    public void setExemptAddresses(List<String> exemptAddresses) {
        this.exemptAddresses = exemptAddresses;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getAccountManagementRequestsPerMinute() {
        return accountManagementRequestsPerMinute;
    }

    public void setAccountManagementRequestsPerMinute(Integer accountManagementRequestsPerMinute) {
        this.accountManagementRequestsPerMinute = accountManagementRequestsPerMinute;
    }

    public Integer getAuthenticationRequestsPerMinute() {
        return authenticationRequestsPerMinute;
    }

    public void setAuthenticationRequestsPerMinute(Integer authenticationRequestsPerMinute) {
        this.authenticationRequestsPerMinute = authenticationRequestsPerMinute;
    }

    public Integer getLoginOptionsRequestsPerMinute() {
        return loginOptionsRequestsPerMinute;
    }

    public void setLoginOptionsRequestsPerMinute(Integer loginOptionsRequestsPerMinute) {
        this.loginOptionsRequestsPerMinute = loginOptionsRequestsPerMinute;
    }

    public Integer getProblemStatementRenderingRequestsPerMinute() {
        return problemStatementRenderingRequestsPerMinute;
    }

    public void setProblemStatementRenderingRequestsPerMinute(Integer problemStatementRenderingRequestsPerMinute) {
        this.problemStatementRenderingRequestsPerMinute = problemStatementRenderingRequestsPerMinute;
    }

    public Integer getAiSearchPipelineRequestsPerMinute() {
        return aiSearchPipelineRequestsPerMinute;
    }

    public Integer getBuildAgentCloneTokenRequestsPerMinute() {
        return buildAgentCloneTokenRequestsPerMinute;
    }

    public void setBuildAgentCloneTokenRequestsPerMinute(Integer buildAgentCloneTokenRequestsPerMinute) {
        this.buildAgentCloneTokenRequestsPerMinute = buildAgentCloneTokenRequestsPerMinute;
    }

    public void setAiSearchPipelineRequestsPerMinute(Integer aiSearchPipelineRequestsPerMinute) {
        this.aiSearchPipelineRequestsPerMinute = aiSearchPipelineRequestsPerMinute;
    }

    public Integer getRepositoryEditorRequestsPerMinute() {
        return repositoryEditorRequestsPerMinute;
    }

    public void setRepositoryEditorRequestsPerMinute(Integer repositoryEditorRequestsPerMinute) {
        this.repositoryEditorRequestsPerMinute = repositoryEditorRequestsPerMinute;
    }
}
