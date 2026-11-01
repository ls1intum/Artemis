package de.tum.cit.aet.artemis.account.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.config.AgentAccessEnabled;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.CreatePersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.dto.CreatedPersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.dto.PersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.PersonalAccessTokenService;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.jwt.JWTFilter;
import de.tum.cit.aet.artemis.core.security.jwt.JwtWithSource;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * REST controller for managing the personal access tokens of the current user. Present only when agent access is enabled.
 * <p>
 * None of these endpoints is annotated with {@code @AllowedScopes}, so a token can never be used to manage tokens.
 */
@Profile(PROFILE_CORE)
@Conditional(AgentAccessEnabled.class)
@Lazy
@FeatureUsage(UserFeature.AGENT_ACCESS)
@RestController
@RequestMapping("api/account/")
public class PersonalAccessTokenResource {

    private final PersonalAccessTokenService personalAccessTokenService;

    private final UserRepository userRepository;

    private final TokenProvider tokenProvider;

    public PersonalAccessTokenResource(PersonalAccessTokenService personalAccessTokenService, UserRepository userRepository, TokenProvider tokenProvider) {
        this.personalAccessTokenService = personalAccessTokenService;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
    }

    /**
     * GET /personal-access-tokens : the active tokens of the current user.
     *
     * @return the tokens, without secrets
     */
    @GetMapping("personal-access-tokens")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PersonalAccessTokenDTO>> getPersonalAccessTokens() {
        User user = userRepository.getUser();
        return ResponseEntity.ok(personalAccessTokenService.list(user));
    }

    /**
     * GET /personal-access-token-scopes : the scope names a token can be limited to.
     *
     * @return the scope names
     */
    @GetMapping("personal-access-token-scopes")
    @EnforceAtLeastStudent
    public ResponseEntity<List<String>> getPersonalAccessTokenScopes() {
        return ResponseEntity.ok(Arrays.stream(AgentScope.values()).map(AgentScope::getValue).toList());
    }

    /**
     * POST /personal-access-tokens : creates a token for the current user.
     *
     * @param request     the name, scopes and lifetime
     * @param httpRequest the request, which tells when the session that asks for the token was established
     * @return the token with its secret, which is shown only in this response
     * @throws URISyntaxException if the location header cannot be built
     */
    @PostMapping("personal-access-tokens")
    @EnforceAtLeastStudent
    public ResponseEntity<CreatedPersonalAccessTokenDTO> createPersonalAccessToken(@Valid @RequestBody CreatePersonalAccessTokenDTO request, HttpServletRequest httpRequest)
            throws URISyntaxException {
        User user = userRepository.getUser();
        CreatedPersonalAccessTokenDTO created = personalAccessTokenService.create(user, request, sessionStartedAt(httpRequest));
        return ResponseEntity.created(new URI("/api/account/personal-access-tokens/" + created.token().id())).body(created);
    }

    /**
     * When the session of a request was originally established, which a token rotation keeps, or {@code null} if the request carries no session token.
     */
    @Nullable
    private Instant sessionStartedAt(HttpServletRequest httpRequest) {
        JwtWithSource jwt = JWTFilter.extractValidJwt(httpRequest, tokenProvider);
        return jwt == null ? null : tokenProvider.getIssuedAtDate(jwt.jwt()).toInstant();
    }

    /**
     * DELETE /personal-access-tokens/{tokenId} : revokes a token of the current user.
     *
     * @param tokenId the token to revoke
     * @return 204 if the token was revoked, 404 if the user has no such active token
     */
    @DeleteMapping("personal-access-tokens/{tokenId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> revokePersonalAccessToken(@PathVariable long tokenId) {
        User user = userRepository.getUser();
        personalAccessTokenService.revoke(user, tokenId);
        return ResponseEntity.noContent().build();
    }
}
