package de.tum.cit.aet.artemis.core.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.dto.EmailChangeConfirmationResultDTO;
import de.tum.cit.aet.artemis.account.dto.EmailChangeKeyDTO;
import de.tum.cit.aet.artemis.account.service.EmailChangeService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinute;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * REST controller for the public endpoint that confirms an e-mail change from the link in a confirmation mail. Kept apart from {@link PublicAccountResource}, whose constructor
 * is already at the limit of the code quality gate for the number of collaborators.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.SIGN_IN)
@RestController
@RequestMapping("api/core/public/")
public class PublicEmailChangeResource {

    private final EmailChangeService emailChangeService;

    public PublicEmailChangeResource(EmailChangeService emailChangeService) {
        this.emailChangeService = emailChangeService;
    }

    /**
     * {@code POST /account/confirm-email-change} : confirms one address of a pending e-mail change with the key from the mail that was sent to it. The e-mail address of the
     * account changes once both the current and the new address have been confirmed.
     * <p>
     * Public on purpose: the link is opened from the mailbox, where the user may not be signed in. The key is the proof, and is checked like a password reset key.
     *
     * @param key the key id and secret from the confirmation mail
     * @return ResponseEntity with status 200 (OK) and which address was confirmed and whether that completed the change
     * @throws BadRequestAlertException {@code 400 (Bad Request)} if the key is invalid or has expired, or if the new address has been taken in the meantime
     */
    @FeatureUsage(UserFeature.ACCOUNT_SETTINGS)
    @PostMapping("account/confirm-email-change")
    @EnforceNothing
    @LimitRequestsPerMinute(type = RateLimitType.ACCOUNT_MANAGEMENT)
    public ResponseEntity<EmailChangeConfirmationResultDTO> confirmEmailChange(@RequestBody EmailChangeKeyDTO key) {
        if (StringUtils.isEmpty(key.keyId()) || StringUtils.isEmpty(key.keySecret()) || key.keyId().length() < 10 || key.keySecret().length() < 10) {
            throw new BadRequestAlertException("The key to confirm the e-mail change is invalid or has expired", "Account", "invalidEmailChangeKey");
        }
        return ResponseEntity.ok(emailChangeService.confirm(key.keyId(), key.keySecret()));
    }
}
