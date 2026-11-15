package de.tum.cit.aet.artemis.account.dto;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = { "login", "status" })
public record UserDeletionResultDTO(@Nullable Long userId, String login, UserDeletionResultStatus status, @Nullable String reason) {
}
