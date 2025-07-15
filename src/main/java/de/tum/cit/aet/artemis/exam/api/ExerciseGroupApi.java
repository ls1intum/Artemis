package de.tum.cit.aet.artemis.exam.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;

@ConditionalOnProperty(name = "artemis.exam.enabled", havingValue = "true")
@Controller
public class ExerciseGroupApi extends AbstractExamApi {

    private final ExerciseGroupRepository exerciseGroupRepository;

    public ExerciseGroupApi(ExerciseGroupRepository exerciseGroupRepository) {
        this.exerciseGroupRepository = exerciseGroupRepository;
    }

    public ExerciseGroup findByIdElseThrow(long id) {
        return exerciseGroupRepository.findByIdElseThrow(id);
    }
}
