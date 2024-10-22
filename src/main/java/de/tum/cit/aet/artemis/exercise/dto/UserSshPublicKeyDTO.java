package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserSshPublicKeyDTO(Long id, String label, String publicKey, String keyHash, ZonedDateTime creationDate, ZonedDateTime lastUsedDate, ZonedDateTime expiryDate) {

    public static UserSshPublicKeyDTO of(UserSshPublicKey userSshPublicKey) {
        return new UserSshPublicKeyDTO(userSshPublicKey.getId(), userSshPublicKey.getLabel(), userSshPublicKey.getPublicKey(), userSshPublicKey.getKeyHash(),
                userSshPublicKey.getCreationDate(), userSshPublicKey.getLastUsedDate(), userSshPublicKey.getExpiryDate());
    }
}
