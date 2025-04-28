package de.tum.cit.aet.artemis.exam.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;

@Conditional(ExamEnabled.class)
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
