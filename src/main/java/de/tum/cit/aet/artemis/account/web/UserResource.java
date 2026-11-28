package de.tum.cit.aet.artemis.account.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.dto.SelectedLLMUsageDTO;
import de.tum.cit.aet.artemis.core.dto.UserInitializationDTO;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.lti.api.LtiApi;

/**
 * REST controller for managing users.
 * <p>
 * This class accesses the {@link User} entity, and needs to fetch its collection of authorities.
 * <p>
 * For a normal use-case, it would be better to have an eager relationship between User and Authority, and send everything to the client side: there would be no View Model and DTO,
 * a lot less code, and an outer-join which would be good for performance.
 * <p>
 * We use a View Model and a DTO for 3 reasons:
 * <ul>
 * <li>We want to keep a lazy association between the user and the authorities, because people will quite often do relationships with the user, and we don't want them to get the
 * authorities all the time for nothing (for performance reasons). This is the #1 goal: we should not impact our users' application because of this use-case.</li>
 * <li>Not having an outer join causes n+1 requests to the database. The {@code authorities} association uses {@code @BatchSize(20)} so the lookups are batched within a single
 * transaction, but each HTTP call pays the database round-trip. If this becomes a measured bottleneck, consider an explicit {@code @EntityGraph} or fetch join on the auth
 * path.</li>
 * <li>As this manages users, for security reasons, we'd rather have a DTO layer.</li>
 * </ul>
 * <p>
 * Another option would be to have a specific JPA entity graph to handle this case.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.ACCOUNT_SETTINGS)
@RestController
@SuppressWarnings("deprecation")
@RequestMapping("api/account/")
public class UserResource {

    private static final Logger log = LoggerFactory.getLogger(UserResource.class);

    private final UserCreationService userCreationService;

    private final Optional<LtiApi> ltiApi;

    private final UserRepository userRepository;

    private final AuditEventRepository auditEventRepository;

    private final UserAiPreferenceService userAiPreferenceService;

    public UserResource(AuditEventRepository auditEventRepository, UserRepository userRepository, UserCreationService userCreationService, Optional<LtiApi> ltiApi,
            UserAiPreferenceService userAiPreferenceService) {
        this.userRepository = userRepository;
        this.userAiPreferenceService = userAiPreferenceService;
        this.ltiApi = ltiApi;
        this.userCreationService = userCreationService;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Completes the one-time initialisation of an account that an LTI launch provisioned, by giving it the password it
     * authenticates with afterwards. Every other account is answered without being modified.
     * <p>
     * Whether the initialisation is still outstanding is decided by the lti module's own marker, never by
     * {@code activated}. Those are two different questions, and the flag cannot tell them apart: an account an administrator
     * deactivated looks exactly like one that has never been initialised. Deciding on the flag would therefore let this
     * endpoint activate a deactivated account and hand it a working password - reaching it needs only an authenticated
     * session, and a session issued before the deactivation keeps working, so a disabled account could restore itself.
     * <p>
     * The marker is claimed in one conditional statement, so exactly one request can proceed and a second call - or a
     * second concurrent call - returns no password.
     *
     * @return The ResponseEntity with a status 200 (Ok) and either an empty password or the newly created password
     */
    @PutMapping("users/initialize")
    @EnforceAtLeastStudent
    public ResponseEntity<UserInitializationDTO> initializeUser() {
        User user = userRepository.findOneWithAuthoritiesByLogin(SecurityUtils.getCurrentUserLogin().orElseThrow()).orElseThrow();
        boolean initializationOutstanding = user.isInternal() && ltiApi.isPresent() && ltiApi.get().needsInitialization(user);
        if (!initializationOutstanding || !ltiApi.get().claimInitialization(user)) {
            return ResponseEntity.ok().body(new UserInitializationDTO(null));
        }

        return ResponseEntity.ok().body(new UserInitializationDTO(userCreationService.storeInitialPasswordAndActivate(user).orElse(null)));
    }

    /**
     * PUT users/select-llm-usage : sets the user's AI selection decision and timestamp.
     * Updates both the AI selection (CLOUD_AI, LOCAL_AI, or NO_AI) and the decision timestamp
     * to the current time. Users can change their decision at any time.
     * Each change is recorded in the audit log.
     *
     * @param selectedLLMUsageDTO the DTO containing the user's choice regarding LLM usage
     * @return the ResponseEntity with status 200 (OK) on success,
     *         or with status 400 (Bad Request) if the selection is invalid
     */
    @PutMapping("users/select-llm-usage")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> setSelectedLLMUsageToTimestampAndEnum(@RequestBody @NotNull SelectedLLMUsageDTO selectedLLMUsageDTO) {
        User user = userRepository.getUser();
        ZonedDateTime hasSelectedTimestamp = ZonedDateTime.now();
        AiSelectionDecision selectedLLMUsage = selectedLLMUsageDTO.selection();
        if (selectedLLMUsage == null) {
            throw new IllegalArgumentException("LLM selection decision cannot be null");
        }
        AiSelectionDecision before = userAiPreferenceService.findDecision(user.getId());
        userAiPreferenceService.recordDecision(user.getId(), selectedLLMUsage, hasSelectedTimestamp);
        var auditEvent = new AuditEvent(user.getLogin(), Constants.AI_SELECTION_DECISION, "before=" + before + ";after=" + selectedLLMUsage + ";at=" + hasSelectedTimestamp);
        auditEventRepository.add(auditEvent);
        return ResponseEntity.ok().build();
    }
}
