package de.tum.in.www1.artemis.domain.exam.monitoring.factory;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.ExamActionType;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActionFactory;
import de.tum.in.www1.artemis.domain.exam.monitoring.actions.SwitchedExerciseAction;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.ExamActionDTO;
import de.tum.in.www1.artemis.service.dto.exam.monitoring.actions.SwitchedExerciseActionDTO;

@Service
public class SwitchedExerciseActionFactory implements ExamActionFactory {

    private final ExerciseService exerciseService;

    public SwitchedExerciseActionFactory(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @Override
    public ExamAction create(ExamActionDTO examActionDTO) {
        Exercise exercise = exerciseService.findById(((SwitchedExerciseActionDTO) examActionDTO).getExerciseId()).orElse(null);
        return new SwitchedExerciseAction(exercise);
    }

    @Override
    public boolean match(ExamActionType examActionType) {
        return examActionType == ExamActionType.SWITCHED_EXERCISE;
    }
}
