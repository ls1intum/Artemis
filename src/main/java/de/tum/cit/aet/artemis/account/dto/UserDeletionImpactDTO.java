package de.tum.cit.aet.artemis.account.dto;

import java.util.List;

public record UserDeletionImpactDTO(long userId, String login, boolean automaticEligible, boolean legacyDeleted, boolean retentionOverrideRequired, long totalAffectedObjects,
        String impactFingerprint, List<UserDeletionImpactCategoryDTO> categories) {
}
