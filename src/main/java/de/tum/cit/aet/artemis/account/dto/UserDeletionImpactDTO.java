package de.tum.cit.aet.artemis.account.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = { "userId", "login", "automaticEligible", "legacyDeleted", "retentionOverrideRequired", "totalAffectedObjects", "impactFingerprint", "categories" })
public record UserDeletionImpactDTO(long userId, String login, boolean automaticEligible, boolean legacyDeleted, boolean retentionOverrideRequired, long totalAffectedObjects,
        String impactFingerprint, List<UserDeletionImpactCategoryDTO> categories) {
}
