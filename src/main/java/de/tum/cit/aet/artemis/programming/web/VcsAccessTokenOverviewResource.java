package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.localvc.service.VcsAccessTokenOverviewService;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessTokenType;
import de.tum.cit.aet.artemis.programming.dto.VcsAccessTokenOverviewDTO;

/**
 * REST controller that lets a user view and revoke the VCS access tokens they own (participation tokens plus repository-scoped staff tokens), for the user-settings token
 * overview. Every endpoint operates strictly on the current user's own tokens and never returns the token secret, only display metadata.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage("access/vcs-access-tokens")
@RestController
@RequestMapping("api/programming/")
public class VcsAccessTokenOverviewResource {

    private static final Logger log = LoggerFactory.getLogger(VcsAccessTokenOverviewResource.class);

    private final UserRepository userRepository;

    private final VcsAccessTokenOverviewService vcsAccessTokenOverviewService;

    public VcsAccessTokenOverviewResource(UserRepository userRepository, VcsAccessTokenOverviewService vcsAccessTokenOverviewService) {
        this.userRepository = userRepository;
        this.vcsAccessTokenOverviewService = vcsAccessTokenOverviewService;
    }

    /**
     * GET vcs-access-tokens : Returns all VCS access tokens the current user owns (participation and repository-scoped) as display-only overview entries. The token secret is never
     * included. The client (user settings) paginates the list.
     *
     * @return the current user's tokens as overview DTOs
     */
    @GetMapping("vcs-access-tokens")
    @EnforceAtLeastStudent
    public ResponseEntity<List<VcsAccessTokenOverviewDTO>> getVcsAccessTokens() {
        User user = userRepository.getUser();
        log.debug("REST request to get the VCS access token overview of user {}", user.getLogin());
        return ResponseEntity.ok(vcsAccessTokenOverviewService.getTokenOverviewForUser(user.getId()));
    }

    /**
     * DELETE vcs-access-tokens/{vcsAccessTokenId} : Revokes a single VCS access token the current user owns. Scoped to the current user, so a user can never revoke another user's
     * token. The next clone-dialog visit transparently re-mints a fresh token.
     *
     * @param vcsAccessTokenId the id of the token to revoke
     * @param tokenType        whether the token is a participation or a repository-scoped token (disambiguates the id, which is only unique within its own token table)
     * @return 204 No Content
     */
    @DeleteMapping("vcs-access-tokens/{vcsAccessTokenId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> revokeVcsAccessToken(@PathVariable long vcsAccessTokenId, @RequestParam("tokenType") VcsAccessTokenType tokenType) {
        User user = userRepository.getUser();
        log.debug("REST request to revoke {} VCS access token {} of user {}", tokenType, vcsAccessTokenId, user.getLogin());
        vcsAccessTokenOverviewService.revokeToken(user.getId(), tokenType, vcsAccessTokenId);
        return ResponseEntity.noContent().build();
    }
}
