package de.tum.cit.aet.artemis.account.dto;

import org.jspecify.annotations.Nullable;

public record UserDeletionResultDTO(@Nullable Long userId, String login, UserDeletionResultStatus status, @Nullable String reason) {
}
