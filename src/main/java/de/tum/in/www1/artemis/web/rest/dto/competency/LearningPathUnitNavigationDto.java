package de.tum.in.www1.artemis.web.rest.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.Lecture;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LearningPathUnitNavigationDto(LearningPathUnitDto previousUnit, LearningPathUnitDto currentUnit, LearningPathUnitDto nextUnit) {

    public static LearningPathUnitNavigationDto of(LearningObject previousUnit, boolean isPreviousUnitCompleted, LearningObject currentUnit, boolean isCurrentUnitCompleted,
            LearningObject nextUnit, boolean isNextUnitCompleted) {
        return new LearningPathUnitNavigationDto(LearningPathUnitDto.of(previousUnit, isPreviousUnitCompleted), LearningPathUnitDto.of(currentUnit, isCurrentUnitCompleted),
                LearningPathUnitDto.of(nextUnit, isNextUnitCompleted));
    }

    public record LearningPathUnitDto(Long id, boolean completed, UnitType type) {

        public static LearningPathUnitDto of(LearningObject learningObject, boolean isCompleted) {
            UnitType unitType;
            if (learningObject instanceof Exercise) {
                unitType = UnitType.EXERCISE;
            }
            else if (learningObject instanceof Lecture) {
                unitType = UnitType.LECTURE;
            }
            else {
                throw new IllegalArgumentException("Unknown learning object type");
            }
            return new LearningPathUnitDto(learningObject.getId(), isCompleted, unitType);
        }

        enum UnitType {
            EXERCISE, LECTURE
        }
    }
}
