package de.tum.cit.aet.artemis.account.dto;

import de.tum.cit.aet.artemis.account.service.user.deletion.UserDeletionAction;
import de.tum.cit.aet.artemis.account.service.user.deletion.UserDeletionDataCategory;

public record UserDeletionImpactCategoryDTO(UserDeletionDataCategory category, UserDeletionAction action, long count) {
}
