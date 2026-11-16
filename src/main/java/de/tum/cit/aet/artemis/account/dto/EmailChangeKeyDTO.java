package de.tum.cit.aet.artemis.account.dto;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The key from one of the two confirmation mails of an e-mail change.
 *
 * @param keyId     identifies the key
 * @param keySecret the secret that proves the mail was received
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EmailChangeKeyDTO(String keyId, String keySecret) {

    @Override
    public @NonNull String toString() {
        return "EmailChangeKeyDTO[keyId='" + keyId + "', keySecret=***]";
    }
}
