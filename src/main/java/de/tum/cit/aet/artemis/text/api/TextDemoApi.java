package de.tum.cit.aet.artemis.text.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.service.TextDemoService;

/**
 * Lets the demo seeding create the demo content of the text exercise module. Only delegates to {@link TextDemoService}, which holds the seeding logic.
 */
@Conditional(TextEnabled.class)
@Controller
@Lazy
public class TextDemoApi extends AbstractTextApi {

    private final TextDemoService textDemoService;

    public TextDemoApi(TextDemoService textDemoService) {
        this.textDemoService = textDemoService;
    }

    /**
     * Creates the demo essays of the demo course that do not exist yet, see {@link TextDemoService#createDemo(Course, List, User)}.
     *
     * @param course   the demo course the essays belong to.
     * @param students the demo students, who submit the essay that is already over.
     * @param tutor    the demo tutor, who assesses the submissions.
     * @return the ongoing and the finished essay, in this order, whether they already existed or were created by this call.
     */
    public List<TextExercise> createDemo(Course course, List<User> students, User tutor) {
        return textDemoService.createDemo(course, students, tutor);
    }

    /**
     * Creates the essay of the demo test exam, see {@link TextDemoService#createDemoExamExercise(ExerciseGroup)}.
     *
     * @param exerciseGroup the exercise group of the demo test exam the essay belongs to.
     * @return the created essay.
     */
    public TextExercise createDemoExamExercise(ExerciseGroup exerciseGroup) {
        return textDemoService.createDemoExamExercise(exerciseGroup);
    }
}
