package de.tum.cit.aet.artemis.account.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = { "users", "totalAffectedObjects", "categories" })
public record BulkUserDeletionImpactDTO(List<UserDeletionImpactDTO> users, long totalAffectedObjects, List<UserDeletionImpactCategoryDTO> categories) {
}
