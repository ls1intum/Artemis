package de.tum.cit.aet.artemis.modeling.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.service.ModelingDemoService;

/**
 * Lets the demo seeding create the demo content of the modeling module. Only delegates to {@link ModelingDemoService}, which holds the seeding logic.
 */
@Conditional(ModelingEnabled.class)
@Controller
@Lazy
public class ModelingDemoApi extends AbstractModelingApi {

    private final ModelingDemoService modelingDemoService;

    public ModelingDemoApi(ModelingDemoService modelingDemoService) {
        this.modelingDemoService = modelingDemoService;
    }

    /**
     * Creates the demo modeling exercises in the given course that do not exist yet, see {@link ModelingDemoService#createDemo(Course, List)}.
     *
     * @param course   the demo course the exercises belong to.
     * @param students the demo students, who submit to the exercise in assessment.
     * @return the ongoing exercise and the exercise in assessment, whether they already existed or were created by this call.
     */
    public List<ModelingExercise> createDemo(Course course, List<User> students) {
        return modelingDemoService.createDemo(course, students);
    }

    /**
     * Creates the class diagram exercise of the demo test exam, see {@link ModelingDemoService#createDemoExamExercise(ExerciseGroup)}.
     *
     * @param exerciseGroup the exercise group of the demo test exam the exercise belongs to.
     * @return the created exercise.
     */
    public ModelingExercise createDemoExamExercise(ExerciseGroup exerciseGroup) {
        return modelingDemoService.createDemoExamExercise(exerciseGroup);
    }
}
