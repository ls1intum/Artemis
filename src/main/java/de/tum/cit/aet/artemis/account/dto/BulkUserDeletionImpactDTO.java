package de.tum.cit.aet.artemis.account.dto;

import java.util.List;

public record BulkUserDeletionImpactDTO(List<UserDeletionImpactDTO> users, long totalAffectedObjects, List<UserDeletionImpactCategoryDTO> categories) {
}
