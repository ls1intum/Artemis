package de.tum.cit.aet.artemis.core.dto.vm;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * View Model object for storing the user's key id, key secret and password.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KeyIdKeySecretAndPasswordVM(String keyId, String keySecret, String newPassword) {

}
