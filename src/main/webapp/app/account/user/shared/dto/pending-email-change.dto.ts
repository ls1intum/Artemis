/**
 * A request to change the email address of the signed-in account that still waits for confirmation.
 * Must match the server-side {@code PendingEmailChangeDTO}. The account keeps its current email address
 * until both addresses have confirmed the change.
 */
export interface PendingEmailChangeDTO {
    newEmail: string;
    /** ISO-8601 instant */
    requestedAt: string;
    /** ISO-8601 instant after which the request is no longer valid */
    expiresAt: string;
    /** True when the link sent to the current address was confirmed (or there is no current address). */
    currentAddressConfirmed: boolean;
    /** True when the link sent to the new address was confirmed. */
    newAddressConfirmed: boolean;
}

/**
 * Response of saving the basic account information. The name and language are saved immediately,
 * an email change only creates (or replaces) a pending request.
 */
export interface UpdateBasicInformationResponseDTO {
    pendingEmailChange: PendingEmailChangeDTO | null;
}
