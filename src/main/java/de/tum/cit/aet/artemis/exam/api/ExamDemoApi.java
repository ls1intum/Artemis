package de.tum.cit.aet.artemis.exam.api;

import java.util.SequencedMap;
import java.util.function.Function;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.service.ExamDemoService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Lets the demo seeding create the demo content of the exam module. Only delegates to {@link ExamDemoService}, which holds the seeding logic.
 */
@Conditional(ExamEnabled.class)
@Controller
@Lazy
public class ExamDemoApi extends AbstractExamApi {

    private final ExamDemoService examDemoService;

    public ExamDemoApi(ExamDemoService examDemoService) {
        this.examDemoService = examDemoService;
    }

    /**
     * Creates the test exam of the demo course if it does not exist yet, with one mandatory exercise group per given creator, see {@link ExamDemoService#createDemo}.
     *
     * @param course           the demo course the exam belongs to.
     * @param exerciseCreators the creators of the exam exercises by the title of their exercise group, in the order of the groups.
     */
    public void createDemo(Course course, SequencedMap<String, Function<ExerciseGroup, ? extends Exercise>> exerciseCreators) {
        examDemoService.createDemo(course, exerciseCreators);
    }
}
