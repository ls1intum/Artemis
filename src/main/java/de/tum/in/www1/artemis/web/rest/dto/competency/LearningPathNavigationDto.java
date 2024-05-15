package de.tum.in.www1.artemis.web.rest.dto.competency;

import org.springframework.lang.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the navigation of a learning path.
 *
 * @param predecessorLearningObject the predecessor learning object
 * @param currentLearningObject     the current learning object
 * @param successorLearningObject   the successor learning object
 * @param progress                  the progress of the learning path
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LearningPathNavigationDto(@Nullable LearningPathNavigationObjectDto predecessorLearningObject, LearningPathNavigationObjectDto currentLearningObject,
        @Nullable LearningPathNavigationObjectDto successorLearningObject, int progress) {
}
