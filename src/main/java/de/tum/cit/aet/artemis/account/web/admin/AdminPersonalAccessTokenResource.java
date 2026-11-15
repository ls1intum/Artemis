package de.tum.cit.aet.artemis.account.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.config.AgentAccessEnabled;
import de.tum.cit.aet.artemis.account.dto.PersonalAccessTokenDTO;
import de.tum.cit.aet.artemis.account.service.PersonalAccessTokenService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * REST controller for administrators to inspect and revoke the personal access tokens of any user. Present only when agent access is enabled.
 * <p>
 * None of these endpoints is annotated with {@code @AllowedScopes}, so a token can never be used to manage tokens.
 */
@Profile(PROFILE_CORE)
@Conditional(AgentAccessEnabled.class)
@Lazy
@FeatureUsage(UserFeature.AGENT_ACCESS)
@RestController
@RequestMapping("api/account/admin/")
public class AdminPersonalAccessTokenResource {

    private static final Logger log = LoggerFactory.getLogger(AdminPersonalAccessTokenResource.class);

    private static final Pattern LINE_BREAK = Pattern.compile("[\\r\\n]");

    private final PersonalAccessTokenService personalAccessTokenService;

    public AdminPersonalAccessTokenResource(PersonalAccessTokenService personalAccessTokenService) {
        this.personalAccessTokenService = personalAccessTokenService;
    }

    /**
     * GET /admin/users/{login}/personal-access-tokens : lists the active tokens of a user, without secrets.
     *
     * @param login the login of the owner
     * @return the tokens
     */
    @GetMapping("users/{login}/personal-access-tokens")
    @EnforceAdmin
    public ResponseEntity<List<PersonalAccessTokenDTO>> getPersonalAccessTokensOfUser(@PathVariable String login) {
        return ResponseEntity.ok(personalAccessTokenService.listForLogin(login));
    }

    /**
     * DELETE /admin/users/{login}/personal-access-tokens : revokes every active token of a user, for example after the account was deprovisioned in an external system.
     *
     * @param login the login of the owner
     * @return 204 whether or not the user had active tokens
     */
    @DeleteMapping("users/{login}/personal-access-tokens")
    @EnforceAdmin
    public ResponseEntity<Void> revokePersonalAccessTokensOfUser(@PathVariable String login) {
        int revoked = personalAccessTokenService.revokeAllForLogin(login);
        // The login comes from the URL path, so strip line breaks before logging it to prevent log injection
        log.info("Revoked {} personal access tokens of user {}", revoked, LINE_BREAK.matcher(login).replaceAll("_"));
        return ResponseEntity.noContent().build();
    }
}
