package de.tum.cit.aet.artemis.account.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The result of saving the basic information of the own account.
 *
 * @param pendingEmailChange the e-mail change that is now waiting for confirmation, absent when there is none. The saved address stays
 *                               unchanged until both the current and the new address have confirmed.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BasicInformationUpdateResultDTO(@Nullable PendingEmailChangeDTO pendingEmailChange) {
}
