package de.tum.cit.aet.artemis.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.api.AccountDemoApi;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.ConductAgreementService;
import de.tum.cit.aet.artemis.assessment.api.AssessmentDemoApi;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyLectureUnitLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.communication.api.CommunicationDemoApi;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.course.api.CourseDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.demo.service.DemoCourseContentSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoDataSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoExerciseSeedingService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.modeling.api.ModelingDemoApi;
import de.tum.cit.aet.artemis.programming.api.ProgrammingDemoApi;
import de.tum.cit.aet.artemis.quiz.api.QuizDemoApi;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.api.TextDemoApi;

/**
 * Tests the demo course seeding routine of the {@code demo} profile.
 * <p>
 * The test context activates the {@code demo} profile, so the seeding service and the demo APIs of the modules exist as beans. The startup event that triggers seeding is never
 * published in tests, so the tests invoke the listener directly. That exercises the real APIs against the real database.
 * <p>
 * Seeding deliberately uses fixed identifiers, so these tests write a course named {@code demo} and the demo users into the shared test database instead of prefixed test data.
 * That is safe precisely because seeding is idempotent, which is also why every test can seed first and still be correct regardless of which demo test ran before it. The methods
 * must not run
 * in parallel though, hence {@link ExecutionMode#SAME_THREAD}.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DemoDataSeedingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private DemoDataSeedingService demoDataSeedingService;

    @Autowired
    private AccountDemoApi accountDemoApi;

    @Autowired
    private CourseDemoApi courseDemoApi;

    @Autowired
    private QuizDemoApi quizDemoApi;

    @Autowired
    private ModelingDemoApi modelingDemoApi;

    @Autowired
    private ProgrammingDemoApi programmingDemoApi;

    @Autowired
    private AssessmentDemoApi assessmentDemoApi;

    @Autowired
    private CommunicationDemoApi communicationDemoApi;

    @Autowired
    private UserCourseRoleRepository userCourseRoleRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private CompetencyLectureUnitLinkRepository competencyLectureUnitLinkRepository;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private ConductAgreementService conductAgreementService;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private ResourceLoaderService resourceLoaderService;

    @Test
    void seedsUsersWithTheirCourseRoles() {
        seed();

        Course course = demoCourse().orElseThrow();
        List<User> users = demoLogins().map(login -> userTestRepository.findOneByLogin(login).orElseThrow()).toList();
        assertThat(users).as("every demo user can log in and has agreed to the code of conduct").allSatisfy(user -> {
            assertThat(user.getActivated()).as("%s is activated", user.getLogin()).isTrue();
            assertThat(conductAgreementService.fetchUserAgreesToCodeOfConductInCourse(user, course)).as("%s agreed to the code of conduct", user.getLogin()).isTrue();
        });

        assertThat(users.stream().filter(user -> hasRole(user, course, CourseRole.STUDENT)).map(User::getLogin)).as("the demo student and their classmates are students")
                .containsExactlyInAnyOrderElementsOf(Stream.concat(Stream.of(AccountDemoApi.DEMO_STUDENT_LOGIN), peerLogins()).toList());
        assertThat(hasRole(user(AccountDemoApi.DEMO_TUTOR_LOGIN), course, CourseRole.TEACHING_ASSISTANT)).as("demo tutor is a tutor").isTrue();
        assertThat(hasRole(user(AccountDemoApi.DEMO_EDITOR_LOGIN), course, CourseRole.EDITOR)).as("demo editor is an editor").isTrue();
        assertThat(hasRole(user(AccountDemoApi.DEMO_INSTRUCTOR_LOGIN), course, CourseRole.INSTRUCTOR)).as("demo instructor is an instructor").isTrue();
    }

    @Test
    void seedsCourseReadyToBeExplored() throws IOException {
        seed();

        Course course = demoCourse().orElseThrow();
        assertThat(courseRepository.ensureDefaultConfigurations(course.getId())).as("demo course owns all of its default settings rows").isZero();
        assertThat(course.getSemester()).as("demo course has a semester in the format the client expects").matches("SS\\d{2}|WS\\d{2}/\\d{2}");
        assertThat(course.isOnboardingDone()).as("the demo instructor lands on the course instead of the setup wizard").isTrue();
        assertThat(course.getTimeZone()).as("tutorial groups need the time zone of their course").isEqualTo("Europe/Berlin");
        assertThat(course.getPresentationScore()).as("no presentations are seeded, so the course does not ask for any").isZero();
        assertThat(course.getCourseInformationSharingMessagingCodeOfConduct()).as("the demo course has the code of conduct of a course created through the UI")
                .isEqualTo(resourceLoaderService.getResource(Path.of("templates", "codeofconduct", "README.md")).getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void seedsExerciseCategoriesThatTheClientCanShow() {
        seed();

        Set<Exercise> exercises = exerciseRepository.findByCourseIdWithCategories(demoCourse().orElseThrow().getId());

        // The client parses every category as JSON with its name and color, and silently drops categories in any other format.
        JsonMapper jsonMapper = new JsonMapper();
        assertThat(exercises).as("every demo exercise has a category").allSatisfy(exercise -> assertThat(exercise.getCategories()).isNotEmpty().allSatisfy(category -> {
            JsonNode parsedCategory = jsonMapper.readTree(category);
            assertThat(parsedCategory.path("category").asString()).as("name of the category of %s", exercise.getTitle()).isNotBlank();
            assertThat(parsedCategory.path("color").asString()).as("color of the category of %s", exercise.getTitle()).matches("#[0-9a-f]{6}");
        }));
    }

    @Test
    void seedsOneOngoingExerciseOfEveryType() {
        seed();

        Set<Exercise> exercises = demoExercises(demoCourse().orElseThrow().getId());
        // The whole point of the ongoing demo exercises: a student opening the demo course has to be able to participate in them right away. The other demo exercises show past
        // activity, like submissions waiting for their assessment, so they are closed on purpose.
        Set<Exercise> ongoingExercises = exercises.stream().filter(exercise -> exercise.getDueDate() == null || exercise.getDueDate().isAfter(ZonedDateTime.now()))
                .collect(Collectors.toSet());

        assertThat(ongoingExercises).as("one ongoing exercise of every type that this context can seed").hasSize(4).extracting(Exercise::getClass).map(Class::getSimpleName)
                .containsExactlyInAnyOrder("TextExercise", "ModelingExercise", "FileUploadExercise", "QuizExercise");
        assertThat(exercises).isNotEmpty().allSatisfy(exercise -> {
            assertThat(exercise.isVisibleToStudents()).as("%s is released", exercise.getTitle()).isTrue();
            assertThat(exercise.getProblemStatement()).as("%s explains what students have to do", exercise.getTitle()).isNotBlank();
        });
    }

    @Test
    void seedsQuizThatStudentsCanStartThemselves() {
        seed();

        // The demo course also has a quiz that has ended, so this picks the one that is still open.
        QuizExercise quizExercise = (QuizExercise) demoExercises(demoCourse().orElseThrow().getId()).stream()
                .filter(exercise -> exercise instanceof QuizExercise && exercise.getDueDate() != null && exercise.getDueDate().isAfter(ZonedDateTime.now())).findFirst()
                .orElseThrow();
        QuizExercise withQuestions = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExercise.getId());

        // Individual mode lets every student start their own batch, so the quiz stays participatable for the lifetime of the demo instance.
        assertThat(withQuestions.getQuizMode()).as("demo quiz can be started by each student individually").isEqualTo(QuizMode.INDIVIDUAL);
        assertThat(withQuestions.getDuration()).as("demo quiz has a working time").isPositive();
        assertThat(withQuestions.getQuizQuestions()).as("demo quiz has questions").isNotEmpty();
        assertThat(withQuestions.isValid()).as("demo quiz is valid and can therefore be started").isTrue();
        assertThat(withQuestions.getMaxPoints()).as("achievable points are the sum of the question points").isEqualTo(withQuestions.getOverallQuizPoints());
    }

    @Test
    void seedingTwiceCreatesNothingNew() {
        seed();
        DemoDataSnapshot afterFirstRun = snapshotDemoData();

        seed();

        assertThat(snapshotDemoData()).as("seeding an already seeded database must neither create nor replace anything").isEqualTo(afterFirstRun);
    }

    @Test
    void seedsWithoutOptionalModules() {
        seed();
        DemoDataSnapshot beforeRun = snapshotDemoData();
        deleteGradingScale();

        DemoDataSeedingService withoutOptionalModules = new DemoDataSeedingService(accountDemoApi, courseDemoApi,
                new DemoExerciseSeedingService(Optional.empty(), Optional.empty(), Optional.empty(), quizDemoApi, programmingDemoApi, assessmentDemoApi, Optional.empty()),
                new DemoCourseContentSeedingService(Optional.empty(), Optional.empty(), assessmentDemoApi, Optional.empty(), communicationDemoApi));
        DemoSeeding.seed(withoutOptionalModules);

        assertThat(snapshotDemoData()).as("disabled modules must not change existing demo data").isEqualTo(beforeRun);
        assertThat(gradingScaleRepository.findByCourseId(demoCourse().orElseThrow().getId())).as("the areas of the enabled modules are still seeded").isPresent();
    }

    @Test
    void continuesWithRemainingAreasWhenOneFails() {
        seed();
        DemoDataSnapshot beforeRun = snapshotDemoData();
        deleteGradingScale();
        TextDemoApi failingTextDemoApi = mock(TextDemoApi.class);
        when(failingTextDemoApi.createDemo(any(), any(), any())).thenThrow(new IllegalStateException("simulated failure of the text exercises"));

        DemoDataSeedingService withFailingArea = new DemoDataSeedingService(
                accountDemoApi, courseDemoApi, new DemoExerciseSeedingService(Optional.of(failingTextDemoApi), Optional.of(modelingDemoApi), Optional.empty(), quizDemoApi,
                        programmingDemoApi, assessmentDemoApi, Optional.empty()),
                new DemoCourseContentSeedingService(Optional.empty(), Optional.empty(), assessmentDemoApi, Optional.empty(), communicationDemoApi));
        List<String> errors = DemoSeeding.seedAndCollectErrors(withFailingArea);

        assertThat(errors).as("the failing area is logged instead of escaping into the startup").containsExactly("Could not seed the demo text exercises");
        assertThat(gradingScaleRepository.findByCourseId(demoCourse().orElseThrow().getId())).as("the areas after the failing one are still seeded").isPresent();
        assertThat(snapshotDemoData()).as("the remaining areas leave the existing demo data alone").isEqualTo(beforeRun);
    }

    @Test
    void seedsNothingElseWithoutItsUsers() {
        seed();
        DemoDataSnapshot beforeRun = snapshotDemoData();
        deleteGradingScale();
        AccountDemoApi failingAccountDemoApi = mock(AccountDemoApi.class);
        when(failingAccountDemoApi.createDemoUsers()).thenThrow(new IllegalStateException("simulated failure of the users"));

        List<String> errors = DemoSeeding.seedAndCollectErrors(new DemoDataSeedingService(failingAccountDemoApi, courseDemoApi,
                new DemoExerciseSeedingService(Optional.empty(), Optional.empty(), Optional.empty(), quizDemoApi, programmingDemoApi, assessmentDemoApi, Optional.empty()),
                new DemoCourseContentSeedingService(Optional.empty(), Optional.empty(), assessmentDemoApi, Optional.empty(), communicationDemoApi)));

        assertThat(errors).as("the demo content belongs to the demo users, so nothing else is seeded without them").containsExactly("Could not seed the demo users",
                "Skipping the remaining demo data, because it belongs to the demo course and its users");
        assertThat(gradingScaleRepository.findByCourseId(demoCourse().orElseThrow().getId())).as("no area runs without the users").isEmpty();
        assertThat(snapshotDemoData()).isEqualTo(beforeRun);
        seed();
    }

    private void deleteGradingScale() {
        gradingScaleRepository.findByCourseId(demoCourse().orElseThrow().getId()).ifPresent(gradingScaleRepository::delete);
    }

    private void seed() {
        DemoSeeding.seed(demoDataSeedingService);
    }

    /**
     * The exercises of the demo course that this test context can seed.
     * <p>
     * The demo programming exercise is not among them: creating its repositories and build plans needs a version control and a continuous integration system, and this
     * context activates neither {@code localvc} nor {@code localci}, so {@link ProgrammingDemoApi#createDemo} skips itself.
     */
    private Set<Exercise> demoExercises(long courseId) {
        return exerciseRepository.findAllExercisesByCourseId(courseId);
    }

    private static Stream<String> demoLogins() {
        return Stream.concat(Stream.of(AccountDemoApi.DEMO_STUDENT_LOGIN, AccountDemoApi.DEMO_TUTOR_LOGIN, AccountDemoApi.DEMO_EDITOR_LOGIN, AccountDemoApi.DEMO_INSTRUCTOR_LOGIN),
                peerLogins());
    }

    private static Stream<String> peerLogins() {
        return IntStream.rangeClosed(1, 10).mapToObj(number -> AccountDemoApi.DEMO_PEER_LOGIN_PREFIX + number);
    }

    private User user(String login) {
        return userTestRepository.findOneByLogin(login).orElseThrow();
    }

    private boolean hasRole(User user, Course course, CourseRole role) {
        return userCourseRoleRepository.existsByUser_IdAndCourse_IdAndRole(user.getId(), course.getId(), role);
    }

    private Optional<Course> demoCourse() {
        return courseRepository.findAllByShortName(CourseDemoApi.DEMO_COURSE_SHORT_NAME).stream().findFirst();
    }

    private Set<Lecture> demoLectures(long courseId) {
        return lectureRepository.findAllByCourseId(courseId);
    }

    private List<LectureUnit> demoLectureUnits(long courseId) {
        return demoLectures(courseId).stream().flatMap(lecture -> lectureRepository.findByIdWithLectureUnitsElseThrow(lecture.getId()).getLectureUnits().stream()).toList();
    }

    private Set<Long> linkedLectureUnitIds(Set<Competency> competencies) {
        if (competencies.isEmpty()) {
            return Set.of();
        }
        return competencyLectureUnitLinkRepository.findLectureUnitIdsByCompetencyIds(competencies.stream().map(Competency::getId).collect(Collectors.toSet()));
    }

    /**
     * Captures the identities of the demo data instead of naming it, so that the idempotency assertions detect replaced records and keep working as the seeded content grows.
     */
    private DemoDataSnapshot snapshotDemoData() {
        Set<Long> userIds = demoLogins().flatMap(login -> userTestRepository.findOneByLogin(login).stream()).map(User::getId).collect(Collectors.toSet());
        Set<Long> courseIds = courseRepository.findAllByShortName(CourseDemoApi.DEMO_COURSE_SHORT_NAME).stream().map(Course::getId).collect(Collectors.toSet());
        Optional<Course> course = demoCourse();
        if (course.isEmpty()) {
            return new DemoDataSnapshot(courseIds, userIds, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        }
        long courseId = course.get().getId();
        Set<Competency> competencies = competencyRepository.findAllByCourseId(courseId);
        return new DemoDataSnapshot(courseIds, userIds, demoLectures(courseId).stream().map(Lecture::getId).collect(Collectors.toSet()),
                demoLectureUnits(courseId).stream().map(LectureUnit::getId).collect(Collectors.toSet()), competencies.stream().map(Competency::getId).collect(Collectors.toSet()),
                linkedLectureUnitIds(competencies), demoExercises(courseId).stream().map(Exercise::getId).collect(Collectors.toSet()));
    }

    private record DemoDataSnapshot(Set<Long> courseIds, Set<Long> userIds, Set<Long> lectureIds, Set<Long> lectureUnitIds, Set<Long> competencyIds, Set<Long> linkedLectureUnitIds,
            Set<Long> exerciseIds) {
    }
}
