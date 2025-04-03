package de.tum.cit.aet.artemis.exam.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;

@Profile(PROFILE_CORE)
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
