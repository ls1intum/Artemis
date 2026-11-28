package de.tum.cit.aet.artemis.account.dto;

import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.PendingEmailChange;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An e-mail change the account has requested and that is waiting for both addresses to confirm it. The keys are deliberately not part
 * of it: they only ever leave the server in the mails.
 *
 * @param newEmail                the address the account will have once both addresses have confirmed
 * @param requestedAt             when the change was requested
 * @param expiresAt               when the request stops being confirmable
 * @param currentAddressConfirmed whether the owner of the current address has confirmed; already true for an account that has no address yet
 * @param newAddressConfirmed     whether the owner of the new address has confirmed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PendingEmailChangeDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String newEmail, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant requestedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean currentAddressConfirmed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean newAddressConfirmed) {

    /**
     * @param change   the pending change
     * @param validity how long a request can be confirmed after it was made
     * @return the change as shown to its owner
     */
    public static PendingEmailChangeDTO of(PendingEmailChange change, Duration validity) {
        return new PendingEmailChangeDTO(change.getNewEmail(), change.getRequestedAt(), change.getRequestedAt().plus(validity), change.getCurrentConfirmedAt() != null,
                change.getNewConfirmedAt() != null);
    }
}
