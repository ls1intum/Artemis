package de.tum.cit.aet.artemis.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.account.api.AccountDemoApi;
import de.tum.cit.aet.artemis.assessment.api.AssessmentDemoApi;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.course.api.CourseDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseAvailableTabsDTO;
import de.tum.cit.aet.artemis.demo.service.DemoCourseContentSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoDataSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoExerciseSeedingService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.ExamForOverviewDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamUpdateDTO;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.repository.PlagiarismDetectionConfigRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.fileupload.api.FileUploadDemoApi;
import de.tum.cit.aet.artemis.modeling.api.ModelingDemoApi;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.api.ProgrammingDemoApi;
import de.tum.cit.aet.artemis.quiz.api.QuizDemoApi;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.api.TextDemoApi;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Tests the test exam the {@code demo} profile seeds into the demo course.
 * <p>
 * Like {@link DemoDataSeedingIntegrationTest}, every test seeds first and stays correct regardless of what ran before it, because seeding is idempotent and the demo course
 * persists in the shared test database. The methods must not run in parallel, hence {@link ExecutionMode#SAME_THREAD}.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DemoExamSeedingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    // The title identifies the demo exam on every startup, so it is spelled out here: renaming it would create the exam a second time on existing demo instances.
    private static final String DEMO_EXAM_TITLE = "Practice Exam: Software Engineering Fundamentals";

    @Autowired
    private DemoDataSeedingService demoDataSeedingService;

    @Autowired
    private DemoCourseContentSeedingService demoCourseContentSeedingService;

    @Autowired
    private AccountDemoApi accountDemoApi;

    @Autowired
    private CourseDemoApi courseDemoApi;

    @Autowired
    private TextDemoApi textDemoApi;

    @Autowired
    private ModelingDemoApi modelingDemoApi;

    @Autowired
    private FileUploadDemoApi fileUploadDemoApi;

    @Autowired
    private QuizDemoApi quizDemoApi;

    @Autowired
    private ProgrammingDemoApi programmingDemoApi;

    @Autowired
    private AssessmentDemoApi assessmentDemoApi;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ExerciseVersionRepository exerciseVersionRepository;

    @Autowired
    private TeamAssignmentConfigRepository teamAssignmentConfigRepository;

    @Autowired
    private PlagiarismDetectionConfigRepository plagiarismDetectionConfigRepository;

    @Test
    void seedsATestExamThatStaysOpenForAYear() throws Exception {
        seed();
        Exam exam = demoExamWithExercises();
        ZonedDateTime now = ZonedDateTime.now();

        assertThat(exam.isTestExam()).as("students start the exam themselves, as often as they like").isTrue();
        assertThat(exam.isVisibleToStudents()).as("the exam is visible").isTrue();
        assertThat(exam.isStarted()).as("the exam has started").isTrue();
        assertThat(exam.getEndDate().toInstant()).as("the exam ends a year after seeding").isCloseTo(now.plusYears(1).toInstant(), within(1, ChronoUnit.DAYS));
        assertThat(exam.getWorkingTime()).as("every attempt has a working time of 60 minutes").isEqualTo(60 * 60);
        assertThat(exam.getStartText()).as("the exam explains itself on its cover").isNotBlank();
        assertThat(channelRepository.findChannelByExamId(exam.getId())).as("the exam has its channel").isNotNull();

        assertThat(exam.getExerciseGroups()).as("one mandatory exercise group per topic of the course, each with one exercise")
                .extracting(ExerciseGroup::getTitle, ExerciseGroup::getIsMandatory, group -> group.getExercises().size())
                .containsExactly(tuple("Software Architecture", true, 1), tuple("Object-Oriented Modeling", true, 1), tuple("Algorithms and Complexity", true, 1));
        List<Exercise> exercises = exercisesOf(exam);
        assertThat(exercises).extracting(Exercise::getClass).containsExactly(TextExercise.class, ModelingExercise.class, QuizExercise.class);
        assertThat(exercises).allSatisfy(exercise -> {
            assertThat(exercise.isCourseExercise()).as("%s belongs to the exam instead of the course", exercise.getTitle()).isFalse();
            assertThat(exercise.getReleaseDate()).as("%s is open while the exam is", exercise.getTitle()).isNull();
            assertThat(exercise.getDueDate()).as("%s is open while the exam is", exercise.getTitle()).isNull();
            assertThat(exercise.getMaxPoints()).as("%s is worth points", exercise.getTitle()).isPositive();
            assertThat(exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId())).as("%s has its version", exercise.getTitle()).isPresent();
            assertThat(teamAssignmentConfigRepository.existsByExerciseId(exercise.getId())).as("%s has its team settings", exercise.getTitle()).isTrue();
            assertThat(plagiarismDetectionConfigRepository.existsByExerciseId(exercise.getId())).as("%s has its plagiarism settings", exercise.getTitle()).isTrue();
        });

        // What the exam checklist expects of an exam that is ready to be taken.
        assertThat(exam.getNumberOfExercisesInExam()).as("every student exam contains one exercise per group").isEqualTo(exam.getExerciseGroups().size());
        assertThat((double) exam.getExamMaxPoints()).as("the points of the exam are the points of its exercises")
                .isEqualTo(exercises.stream().mapToDouble(Exercise::getMaxPoints).sum());

        // Saving the exam unchanged in the exam editor runs the validation of ExamResource, which the seeded exam has to pass like any exam created through the client.
        userUtilService.changeUser(AccountDemoApi.DEMO_INSTRUCTOR_LOGIN);
        request.put("/api/exam/courses/" + exam.getCourse().getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.OK);
    }

    @Test
    void demoStudentCanStartTheTestExam() throws Exception {
        seed();
        long courseId = demoCourse().getId();
        Exam exam = demoExamWithExercises();
        String examUrl = "/api/exam/courses/" + courseId + "/exams/" + exam.getId();
        userUtilService.changeUser(AccountDemoApi.DEMO_STUDENT_LOGIN);

        CourseAvailableTabsDTO tabs = request.get("/api/course/courses/" + courseId + "/available-tabs", HttpStatus.OK, CourseAvailableTabsDTO.class);
        assertThat(tabs.exams()).as("the course shows its exams to the demo student").isTrue();
        Set<ExamForOverviewDTO> exams = request.getSet("/api/exam/courses/" + courseId + "/exams-for-overview", HttpStatus.OK, ExamForOverviewDTO.class);
        assertThat(exams).as("the demo student finds the test exam among the exams of the course").extracting(ExamForOverviewDTO::id, ExamForOverviewDTO::testExam)
                .contains(tuple(exam.getId(), true));

        // The requests of the exam page of the client: opening the exam generates the attempt of the student, starting it sets up their participations.
        StudentExam attempt = request.get(examUrl + "/own-student-exam", HttpStatus.OK, StudentExam.class);
        assertThat(attempt.isSubmitted()).as("the attempt is open").isFalse();
        StudentExam startedAttempt = request.get(examUrl + "/student-exams/" + attempt.getId() + "/conduction", HttpStatus.OK, StudentExam.class);

        assertThat(startedAttempt.isStarted()).as("the attempt has started").isTrue();
        assertThat(startedAttempt.getWorkingTime()).as("the attempt has the working time of the exam").isEqualTo(exam.getWorkingTime());
        assertThat(startedAttempt.getExercises()).as("the attempt contains the exercise of every group, in the order of the groups").extracting(DomainObject::getId)
                .containsExactlyElementsOf(exercisesOf(exam).stream().map(DomainObject::getId).toList());
        assertThat(startedAttempt.getExercises()).as("the demo student can work on every exercise right away")
                .allSatisfy(exercise -> assertThat(exercise.getStudentParticipations()).as("%s has a participation", exercise.getTitle()).isNotEmpty()
                        .allSatisfy(participation -> assertThat(participation.getSubmissions()).as("%s has a submission to work in", exercise.getTitle()).isNotEmpty()));
    }

    @Test
    void seedingTwiceCreatesNothingNew() {
        seed();
        ExamIdentity afterFirstRun = examIdentity();

        seed();

        assertThat(examIdentity()).as("seeding again must neither create nor replace the exam, its exercise groups or its exercises").isEqualTo(afterFirstRun);
    }

    @Test
    void recreatesTheDeletedExamOnlyWithTheExamModule() throws Exception {
        seed();
        Exam exam = demoExamWithExercises();
        userUtilService.changeUser(AccountDemoApi.DEMO_INSTRUCTOR_LOGIN);
        request.delete("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId(), HttpStatus.OK);

        DemoDataSeedingService withoutExamModule = new DemoDataSeedingService(accountDemoApi, courseDemoApi, new DemoExerciseSeedingService(Optional.of(textDemoApi),
                Optional.of(modelingDemoApi), Optional.of(fileUploadDemoApi), quizDemoApi, programmingDemoApi, assessmentDemoApi, Optional.empty()),
                demoCourseContentSeedingService);
        assertThatCode(() -> withoutExamModule.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent())).as("seeding must work when the exam module is disabled")
                .doesNotThrowAnyException();
        assertThat(demoExam()).as("a disabled exam module creates no exam").isEmpty();

        seed();

        Exam recreatedExam = demoExamWithExercises();
        assertThat(recreatedExam.getId()).as("the deleted exam is recreated").isNotEqualTo(exam.getId());
        assertThat(exercisesOf(recreatedExam)).as("with its exercises").hasSameSizeAs(exercisesOf(exam));
    }

    private void seed() {
        demoDataSeedingService.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent());
    }

    private Course demoCourse() {
        return courseRepository.findAllByShortName(CourseDemoApi.DEMO_COURSE_SHORT_NAME).getFirst();
    }

    private Optional<Exam> demoExam() {
        return examRepository.findByCourseId(demoCourse().getId()).stream().filter(exam -> DEMO_EXAM_TITLE.equals(exam.getTitle())).findFirst();
    }

    private Exam demoExamWithExercises() {
        return examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(demoExam().orElseThrow().getId());
    }

    /**
     * The exercises of the exam in the order of their exercise groups, which hold one exercise each.
     */
    private static List<Exercise> exercisesOf(Exam exam) {
        return exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).toList();
    }

    /**
     * Captures the identities of the demo exam and of what it consists of, so that the idempotency assertion detects replaced records.
     */
    private ExamIdentity examIdentity() {
        Exam exam = demoExamWithExercises();
        return new ExamIdentity(exam.getId(), exam.getExerciseGroups().stream().map(DomainObject::getId).toList(), exercisesOf(exam).stream().map(DomainObject::getId).toList());
    }

    private record ExamIdentity(long examId, List<Long> exerciseGroupIds, List<Long> exerciseIds) {
    }
}
