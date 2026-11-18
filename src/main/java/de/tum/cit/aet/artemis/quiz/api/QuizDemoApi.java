package de.tum.cit.aet.artemis.quiz.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.service.QuizDemoService;

/**
 * Lets the demo seeding create the demo content of the quiz module. Only delegates to {@link QuizDemoService}, which holds the seeding logic.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class QuizDemoApi implements AbstractApi {

    private final QuizDemoService quizDemoService;

    public QuizDemoApi(QuizDemoService quizDemoService) {
        this.quizDemoService = quizDemoService;
    }

    /**
     * Creates the demo quizzes in the given course that do not exist yet, see {@link QuizDemoService#createDemo(Course, List)}.
     *
     * @param course   the demo course the quizzes belong to.
     * @param students the demo students, who take the quiz that has ended.
     * @return the ongoing quiz and the quiz that has ended, whether they already existed or were created by this call.
     */
    public List<QuizExercise> createDemo(Course course, List<User> students) {
        return quizDemoService.createDemo(course, students);
    }

    /**
     * Creates the quiz of the demo test exam, see {@link QuizDemoService#createDemoExamExercise(ExerciseGroup)}.
     *
     * @param exerciseGroup the exercise group of the demo test exam the quiz belongs to.
     * @return the created quiz.
     */
    public QuizExercise createDemoExamExercise(ExerciseGroup exerciseGroup) {
        return quizDemoService.createDemoExamExercise(exerciseGroup);
    }
}
