package de.tum.cit.aet.artemis.demo.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Function;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.api.AccountDemoApi.DemoUsers;
import de.tum.cit.aet.artemis.assessment.api.AssessmentDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.api.ExamDemoApi;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.api.FileUploadDemoApi;
import de.tum.cit.aet.artemis.modeling.api.ModelingDemoApi;
import de.tum.cit.aet.artemis.programming.api.ProgrammingDemoApi;
import de.tum.cit.aet.artemis.quiz.api.QuizDemoApi;
import de.tum.cit.aet.artemis.text.api.TextDemoApi;

/**
 * Seeds the exercises of the demo course, see {@link DemoDataSeedingService}.
 */
@Service
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class DemoExerciseSeedingService {

    private final Optional<TextDemoApi> textDemoApi;

    private final Optional<ModelingDemoApi> modelingDemoApi;

    private final Optional<FileUploadDemoApi> fileUploadDemoApi;

    private final QuizDemoApi quizDemoApi;

    private final ProgrammingDemoApi programmingDemoApi;

    private final AssessmentDemoApi assessmentDemoApi;

    private final Optional<ExamDemoApi> examDemoApi;

    public DemoExerciseSeedingService(Optional<TextDemoApi> textDemoApi, Optional<ModelingDemoApi> modelingDemoApi, Optional<FileUploadDemoApi> fileUploadDemoApi,
            QuizDemoApi quizDemoApi, ProgrammingDemoApi programmingDemoApi, AssessmentDemoApi assessmentDemoApi, Optional<ExamDemoApi> examDemoApi) {
        this.textDemoApi = textDemoApi;
        this.modelingDemoApi = modelingDemoApi;
        this.fileUploadDemoApi = fileUploadDemoApi;
        this.quizDemoApi = quizDemoApi;
        this.programmingDemoApi = programmingDemoApi;
        this.assessmentDemoApi = assessmentDemoApi;
        this.examDemoApi = examDemoApi;
    }

    /**
     * Seeds the exercises of the demo course and groups them by the topic of the course they belong to, so that lectures and competencies can refer to them. The exercises of the
     * test exam are not among them, because they belong to the exam instead of the course.
     *
     * @param course the demo course.
     * @param users  the demo users.
     * @return the demo exercises by topic, whether they already existed or were created by this call.
     */
    DemoExercises seed(Course course, DemoUsers users) {
        List<Exercise> architecture = new ArrayList<>();
        List<Exercise> algorithms = new ArrayList<>();
        List<Exercise> modeling = new ArrayList<>();

        // Before any exercise creates results, so that the results count towards the scores of the students.
        DemoAreas.seed("participant scores", assessmentDemoApi::activateParticipantScores);
        DemoAreas.seed("text exercises", () -> textDemoApi.ifPresent(api -> architecture.addAll(api.createDemo(course, users.students(), users.tutor()))));
        DemoAreas.seed("modeling exercises", () -> modelingDemoApi.ifPresent(api -> modeling.addAll(api.createDemo(course, users.students()))));
        DemoAreas.seed("file upload exercises", () -> fileUploadDemoApi.ifPresent(api -> algorithms.add(api.createDemo(course))));
        DemoAreas.seed("quiz exercises", () -> algorithms.addAll(quizDemoApi.createDemo(course, users.students())));
        DemoAreas.seed("programming exercises", () -> programmingDemoApi.createDemo(course).ifPresent(algorithms::add));
        DemoAreas.seed("exam", () -> examDemoApi.ifPresent(api -> api.createDemo(course, examExerciseCreators())));

        return new DemoExercises(architecture, algorithms, modeling);
    }

    /**
     * The creators of the exercises of the test exam by the title of their exercise group: one exercise per topic of the course, as far as its module is enabled.
     */
    private SequencedMap<String, Function<ExerciseGroup, ? extends Exercise>> examExerciseCreators() {
        SequencedMap<String, Function<ExerciseGroup, ? extends Exercise>> creators = new LinkedHashMap<>();
        textDemoApi.ifPresent(api -> creators.put("Software Architecture", api::createDemoExamExercise));
        modelingDemoApi.ifPresent(api -> creators.put("Object-Oriented Modeling", api::createDemoExamExercise));
        creators.put("Algorithms and Complexity", quizDemoApi::createDemoExamExercise);
        return creators;
    }

    /**
     * The exercises of the demo course, grouped by the topic of the course they belong to.
     *
     * @param architecture the exercises about software architecture.
     * @param algorithms   the exercises about algorithms and their complexity.
     * @param modeling     the exercises about object-oriented modeling.
     */
    record DemoExercises(List<Exercise> architecture, List<Exercise> algorithms, List<Exercise> modeling) {
    }
}
