package de.tum.cit.aet.artemis.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.account.api.AccountDemoApi;
import de.tum.cit.aet.artemis.assessment.api.AssessmentDemoApi;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.api.CourseDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseAvailableTabsService;
import de.tum.cit.aet.artemis.demo.service.DemoCourseContentSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoDataSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoExerciseSeedingService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.service.ModelingSubmissionService;
import de.tum.cit.aet.artemis.programming.api.ProgrammingDemoApi;
import de.tum.cit.aet.artemis.quiz.api.QuizDemoApi;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizStatisticsService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests the past activity that the {@code demo} profile seeds into the modeling and quiz exercises of the demo course: a modeling exercise whose submissions wait for their
 * assessment, and a quiz that every demo student took before it ended and was evaluated.
 * <p>
 * Like {@link DemoDataSeedingIntegrationTest}, the tests seed the shared test database and rely on seeding being idempotent, which is why every test seeds first and the tests
 * must not run in parallel.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DemoModelingAndQuizActivitySeedingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    // The titles identify the demo exercises on every startup, so they are spelled out here: renaming one would create the exercise a second time on existing demo instances.
    private static final String ONGOING_MODELING_TITLE = "Class Diagram: Library Management System";

    private static final String IN_ASSESSMENT_MODELING_TITLE = "Class Diagram: Online Shop";

    private static final String ONGOING_QUIZ_TITLE = "Quiz: Java Collections and Complexity";

    private static final String ENDED_QUIZ_TITLE = "Quiz: Sorting and Searching";

    @Autowired
    private DemoDataSeedingService demoDataSeedingService;

    @Autowired
    private AccountDemoApi accountDemoApi;

    @Autowired
    private CourseDemoApi courseDemoApi;

    @Autowired
    private QuizDemoApi quizDemoApi;

    @Autowired
    private ProgrammingDemoApi programmingDemoApi;

    @Autowired
    private AssessmentDemoApi assessmentDemoApi;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ExerciseVersionRepository exerciseVersionRepository;

    @Autowired
    private ModelingSubmissionService modelingSubmissionService;

    @Autowired
    private QuizStatisticsService quizStatisticsService;

    @Autowired
    private CourseAvailableTabsService courseAvailableTabsService;

    @Autowired
    private ExerciseDeletionService exerciseDeletionService;

    @Test
    void seedsModelingSubmissionsWaitingForAssessment() {
        seed();

        ModelingExercise exercise = (ModelingExercise) demoExercise(IN_ASSESSMENT_MODELING_TITLE);
        ZonedDateTime now = ZonedDateTime.now();
        assertThat(exercise.getDueDate()).as("the exercise is closed for submissions").isBefore(now);
        assertThat(exercise.getAssessmentDueDate()).as("the tutors have about a year to assess the submissions").isAfter(now.plusMonths(11));

        List<StudentParticipation> participations = participations(exercise);
        assertThat(participations).extracting(StudentParticipation::getParticipantIdentifier).as("every demo student took part")
                .containsExactlyInAnyOrderElementsOf(studentLogins());
        assertThat(participations).allSatisfy(participation -> assertThat(participation.getSubmissions()).as("%s submitted once", participation.getParticipantIdentifier())
                .singleElement(InstanceOfAssertFactories.type(ModelingSubmission.class)).satisfies(submission -> {
                    assertThat(submission.isSubmitted()).as("the submission is handed in").isTrue();
                    assertThat(submission.getSubmissionDate()).as("the submission was handed in on time").isBefore(exercise.getDueDate());
                    assertThat(submission.getResults()).as("nobody has assessed the submission yet").isEmpty();
                    assertIsClassDiagram(submission);
                }));

        // What a tutor gets when they start the next assessment on the assessment dashboard of the exercise.
        assertThat(modelingSubmissionService.findRandomSubmissionWithoutExistingAssessment(false, 0, exercise, false)).as("the submissions are in the assessment queue")
                .isPresent();
    }

    @Test
    void seedsEndedQuizThatStudentsCanPractice() {
        seed();

        QuizExercise quiz = quizExerciseRepository.findByIdWithQuestionsElseThrow(demoExercise(ENDED_QUIZ_TITLE).getId());
        // The condition under which QuizSubmissionResource accepts submissions for practice.
        assertThat(quiz.isCourseExercise() && quiz.isQuizEnded()).as("the quiz has ended and is open for practice").isTrue();
        assertThat(quiz.isValid()).as("the quiz is valid").isTrue();
        assertThat(quiz.getQuizQuestions()).as("the quiz asks multiple choice, single choice and short answer questions")
                .anyMatch(question -> question instanceof MultipleChoiceQuestion multipleChoice && !multipleChoice.isSingleChoice())
                .anyMatch(question -> question instanceof MultipleChoiceQuestion multipleChoice && multipleChoice.isSingleChoice()).anyMatch(ShortAnswerQuestion.class::isInstance);

        List<StudentParticipation> participations = participations(quiz);
        assertThat(participations).extracting(StudentParticipation::getParticipantIdentifier).as("every demo student took the quiz")
                .containsExactlyInAnyOrderElementsOf(studentLogins());
        List<Submission> submissions = participations.stream().flatMap(participation -> participation.getSubmissions().stream()).toList();
        assertThat(submissions).as("every demo student handed in their answers once").hasSize(studentLogins().size())
                .allSatisfy(submission -> assertThat(submission.isSubmitted()).isTrue());
        List<Result> results = submissions.stream().flatMap(submission -> submission.getResults().stream()).toList();
        assertThat(results).as("the evaluation rated every submission").hasSize(studentLogins().size()).allSatisfy(result -> {
            assertThat(result.isRated()).isTrue();
            assertThat(result.getScore()).isNotNull();
            assertThat(result.getCompletionDate()).isNotNull();
        });
        assertThat(results).extracting(Result::getScore).as("some students answered everything right, others only part of it").contains(100.0)
                .anyMatch(score -> score > 0 && score < 100);

        assertThat(quizStatisticsService.getOverview(quiz).participantsRated()).as("the statistics count every demo student").isEqualTo(studentLogins().size());

        Course course = demoCourse();
        var demoStudent = userTestRepository.findOneByLogin(AccountDemoApi.DEMO_STUDENT_LOGIN).orElseThrow();
        assertThat(courseAvailableTabsService.getAvailableTabs(course, demoStudent).training()).as("the questions of the ended quiz fill the Training tab of the course").isTrue();
    }

    @Test
    void keepsOngoingExercisesOpen() {
        seed();

        ZonedDateTime now = ZonedDateTime.now();
        assertThat(demoExercise(ONGOING_MODELING_TITLE).getDueDate()).as("the ongoing modeling exercise still accepts submissions").isAfter(now);
        assertThat(((QuizExercise) demoExercise(ONGOING_QUIZ_TITLE)).isQuizEnded()).as("the ongoing quiz can still be taken").isFalse();
        assertThat(Stream.of(ONGOING_MODELING_TITLE, IN_ASSESSMENT_MODELING_TITLE, ONGOING_QUIZ_TITLE, ENDED_QUIZ_TITLE).map(this::demoExercise))
                .as("every exercise has the version that creating it in the exercise editor records")
                .allSatisfy(exercise -> assertThat(exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId())).isPresent());
    }

    @Test
    void seedingTwiceCreatesNothingNew() {
        seed();
        Map<String, ExerciseActivity> afterFirstRun = snapshotActivity();

        seed();

        assertThat(snapshotActivity()).as("seeding an already seeded course must neither create nor replace any exercise or activity").isEqualTo(afterFirstRun);
    }

    @Test
    void recreatesDeletedQuizWithoutModelingModule() {
        seed();
        Map<String, ExerciseActivity> seeded = snapshotActivity();
        SecurityUtils.runAs(AccountDemoApi.DEMO_INSTRUCTOR_LOGIN, () -> exerciseDeletionService.delete(seeded.get(ENDED_QUIZ_TITLE).exerciseId(), false));

        DemoDataSeedingService withoutModeling = new DemoDataSeedingService(accountDemoApi, courseDemoApi,
                new DemoExerciseSeedingService(Optional.empty(), Optional.empty(), Optional.empty(), quizDemoApi, programmingDemoApi, assessmentDemoApi),
                new DemoCourseContentSeedingService(Optional.empty(), Optional.empty(), assessmentDemoApi));
        assertThatCode(() -> withoutModeling.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent())).as("seeding must work when the modeling module is disabled")
                .doesNotThrowAnyException();

        Map<String, ExerciseActivity> reseeded = snapshotActivity();
        ExerciseActivity recreatedQuiz = reseeded.get(ENDED_QUIZ_TITLE);
        assertThat(recreatedQuiz.exerciseId()).as("the deleted quiz is recreated").isNotEqualTo(seeded.get(ENDED_QUIZ_TITLE).exerciseId());
        assertThat(recreatedQuiz.resultIds()).as("the demo students take the recreated quiz again, and it is evaluated again").hasSize(studentLogins().size());
        Map<String, ExerciseActivity> untouched = new HashMap<>(seeded);
        untouched.remove(ENDED_QUIZ_TITLE);
        assertThat(reseeded).as("the other exercises and their activity are left alone").containsAllEntriesOf(untouched);
    }

    private void seed() {
        demoDataSeedingService.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent());
    }

    private Course demoCourse() {
        return courseRepository.findAllByShortName(CourseDemoApi.DEMO_COURSE_SHORT_NAME).stream().findFirst().orElseThrow();
    }

    private Exercise demoExercise(String title) {
        return exerciseRepository.findAllExercisesByCourseId(demoCourse().getId()).stream().filter(exercise -> title.equals(exercise.getTitle())).findFirst().orElseThrow();
    }

    private List<StudentParticipation> participations(Exercise exercise) {
        return studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsByExerciseId(exercise.getId());
    }

    private static List<String> studentLogins() {
        return Stream.concat(Stream.of(AccountDemoApi.DEMO_STUDENT_LOGIN), IntStream.rangeClosed(1, 10).mapToObj(number -> AccountDemoApi.DEMO_PEER_LOGIN_PREFIX + number))
                .toList();
    }

    private static void assertIsClassDiagram(ModelingSubmission submission) {
        assertThat(submission.isEmpty()).as("the submission contains a diagram").isFalse();
        JsonNode diagram = JsonObjectMapper.get().readTree(submission.getModel());
        assertThat(diagram.get("version").asString()).as("the diagram is in the format the modeling editor of the client saves").startsWith("4.");
        assertThat(diagram.get("type").asString()).isEqualTo("ClassDiagram");
        Set<String> classIds = diagram.get("nodes").valueStream().filter(node -> "class".equals(node.get("type").asString())).map(node -> node.get("id").asString())
                .collect(Collectors.toSet());
        assertThat(classIds).as("the diagram has classes").isNotEmpty();
        assertThat(diagram.get("edges").valueStream().toList()).as("every relationship of the diagram connects two of its classes").isNotEmpty()
                .allSatisfy(edge -> assertThat(classIds).contains(edge.get("source").asString(), edge.get("target").asString()));
    }

    /**
     * Captures the identities of the demo exercises of these areas and of everything the demo students created in them, so that the idempotency assertions also detect replaced
     * records.
     */
    private Map<String, ExerciseActivity> snapshotActivity() {
        return Stream.of(ONGOING_MODELING_TITLE, IN_ASSESSMENT_MODELING_TITLE, ONGOING_QUIZ_TITLE, ENDED_QUIZ_TITLE)
                .collect(Collectors.toMap(Function.identity(), title -> activityOf(demoExercise(title))));
    }

    private ExerciseActivity activityOf(Exercise exercise) {
        List<StudentParticipation> participations = participations(exercise);
        List<Submission> submissions = participations.stream().flatMap(participation -> participation.getSubmissions().stream()).toList();
        List<Result> results = submissions.stream().flatMap(submission -> submission.getResults().stream()).toList();
        return new ExerciseActivity(exercise.getId(), ids(participations), ids(submissions), ids(results));
    }

    private static Set<Long> ids(Collection<? extends DomainObject> entities) {
        return entities.stream().map(DomainObject::getId).collect(Collectors.toSet());
    }

    private record ExerciseActivity(long exerciseId, Set<Long> participationIds, Set<Long> submissionIds, Set<Long> resultIds) {
    }
}
