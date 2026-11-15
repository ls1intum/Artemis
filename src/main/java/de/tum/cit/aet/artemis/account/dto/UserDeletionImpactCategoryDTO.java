package de.tum.cit.aet.artemis.account.dto;

import de.tum.cit.aet.artemis.account.service.user.deletion.UserDeletionAction;
import de.tum.cit.aet.artemis.account.service.user.deletion.UserDeletionDataCategory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = { "category", "action", "count" })
public record UserDeletionImpactCategoryDTO(UserDeletionDataCategory category, UserDeletionAction action, long count) {
}
