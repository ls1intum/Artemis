package de.tum.cit.aet.artemis.atlas.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the navigation of a learning path.
 *
 * @param predecessorLearningObject the predecessor learning object
 * @param currentLearningObject     the current learning object
 * @param successorLearningObject   the successor learning object
 * @param progress                  the progress of the learning path
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningPathNavigationDTO(@Nullable LearningPathNavigationObjectDTO predecessorLearningObject, @Nullable LearningPathNavigationObjectDTO currentLearningObject,
        @Nullable LearningPathNavigationObjectDTO successorLearningObject, int progress) {
}
