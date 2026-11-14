package de.tum.cit.aet.artemis.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What a confirmation link did.
 *
 * @param completed whether both addresses have now confirmed, so the e-mail address of the account has changed
 * @param address   which of the two addresses the link confirmed
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EmailChangeConfirmationResultDTO(boolean completed, EmailChangeAddress address) {

    /**
     * The two addresses that have to confirm an e-mail change.
     */
    public enum EmailChangeAddress {
        CURRENT, NEW
    }
}
