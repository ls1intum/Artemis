package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseAvailableTabsDTO;
import de.tum.cit.aet.artemis.course.dto.CourseExercisesForOverviewDTO;
import de.tum.cit.aet.artemis.course.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.CourseForOverviewDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.dto.ExamForOverviewDTO;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationOverviewDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.dto.LectureForOverviewDTO;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

/**
 * Measures what a student actually pays for when entering a course, per endpoint.
 * <p>
 * This is a regression guard for the course-overview load split: the point of splitting the (deprecated) for-dashboard
 * call into a lean course load plus per-tab loads is that entering a course on a tab must not pay for the content of
 * every other tab. The assertions below are deliberately loose upper bounds — the exact numbers move with unrelated
 * changes — but they fail loudly if an endpoint starts loading an order of magnitude more than it needs.
 * <p>
 * The logged table is the measurement referenced in the pull request. Query counts are exact (Hibernate JDBC prepared
 * statement counts, measured with a warm cache — the unread-notification count behind for-overview is {@code @Cacheable},
 * so a cold first request issues one more query than reported); timings come from Testcontainers on a developer machine
 * and are only meaningful relative to each other, not as production latencies.
 */
class CourseOverviewLoadProfileTest extends AbstractSpringIntegrationIndependentTest {

    private static final Logger log = LoggerFactory.getLogger(CourseOverviewLoadProfileTest.class);

    private static final String TEST_PREFIX = "courseloadprofile";

    /** Size of the "realistic course" being profiled. */
    private static final int CONTENT_PER_TYPE = 20;

    /**
     * Generous ceiling above the measured exercise overview response. This is intentionally not the exact baseline:
     * identifiers and legitimate small DTO additions may change the size slightly, while accidentally serializing an
     * entity graph or another large field must fail the profile loudly.
     */
    private static final int MAX_EXERCISE_OVERVIEW_PAYLOAD_BYTES = 20_000;

    /**
     * Projection queries for the exercise-only fixture must remain bounded as its 20 exercise graphs grow. The
     * request itself deterministically issues 8; the ceiling leaves headroom above that for the small residual
     * variance of {@code participantScoreScheduleService}'s own asynchronous settling (competency-progress
     * follow-up work that can still be in flight for the last processed result even once {@code isIdle()} reports
     * true), which {@link #shouldNotHydrateTheExerciseEntityGraph()} cannot fully eliminate without changing that
     * shared production service.
     */
    private static final int MAX_EXERCISE_OVERVIEW_QUERIES = 12;

    /** A modestly sized problem statement; real programming exercises are commonly several times this. */
    private static final String PROBLEM_STATEMENT = """
            # Task
            Implement the following methods and make all tests pass. Read the description carefully before you start.

            ## Background
            This exercise practises the concepts from the lecture. You may use the standard library, but no external
            dependencies. Pay attention to the edge cases listed below, they are all covered by the test suite.

            ## Requirements
            1. Implement `solve(input)` so that it returns the expected output for every input in the specification.
            2. Handle the empty input, a single element, and inputs containing duplicates.
            3. Keep the asymptotic runtime within the bound stated in the lecture slides.
            4. Do not modify the provided test files.

            ## Hints
            - Start with the simplest case and extend from there.
            - The provided sample tests are not exhaustive; the grading run adds more.
            """;

    /** Measured repetitions per endpoint, reported as a median. */
    private static final int MEASURED_RUNS = 5;

    /**
     * Full passes over every endpoint before measuring anything. A per-endpoint warm-up is not enough: the endpoint
     * measured first would carry the JVM's cold-start cost and the last would look artificially fast, which is exactly
     * the kind of ordering artefact that makes a load profile misleading.
     */
    private static final int WARM_UP_PASSES = 3;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private TutorialGroupUtilService tutorialGroupUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository exerciseRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationTestRepository;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private SubmissionTestRepository submissionTestRepository;

    @Autowired
    private ResultTestRepository resultTestRepository;

    private Course course;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        for (int i = 0; i < CONTENT_PER_TYPE; i++) {
            // A realistic mix: programming exercises carry considerably more than text exercises, and seeding only text
            // exercises understates the payload. Every exercise also gets a problem statement, which real ones have.
            Exercise exercise;
            if (i % 2 == 0) {
                exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
                exercise.setMaxPoints(10.0);
                exercise.setBonusPoints(0.0);
                exercise.setReleaseDate(ZonedDateTime.now().minusDays(2));
                exercise.setDueDate(ZonedDateTime.now().plusDays(2));
                ((ProgrammingExercise) exercise).setAllowOnlineEditor(true);
                ((ProgrammingExercise) exercise).setAllowOfflineIde(true);
            }
            else {
                exercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().plusDays(2),
                        ZonedDateTime.now().plusDays(4));
            }
            exercise.setProblemStatement(PROBLEM_STATEMENT);
            exercise = exerciseRepository.save(exercise);
            // A student who has actually worked in the course: without participations the exercise payload is a
            // fraction of its real size. Only the text exercises get one — a programming participation needs template
            // repository infrastructure that this profile does not otherwise require.
            if (exercise instanceof TextExercise) {
                participationUtilService.createParticipationSubmissionAndResult(exercise.getId(), student, 10.0, 0.0, 80, true);
            }
            else if (i == 0) {
                // One programming exercise is worked on too, so the payload contract can be asserted against a result
                // that actually carries the build outcome and the test counts the exercise card renders
                seedProgrammingSubmission((ProgrammingExercise) exercise, student);
            }
            Lecture lecture = lectureUtilService.createLecture(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1));
            lectureUtilService.createAttachmentVideoUnit(lecture, false);
            competencyUtilService.createCompetency(course);
            tutorialGroupUtilService.createAndSaveTutorialGroup(course.getId(), "Group " + i, "info", 10, false, "Garching", "English",
                    userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"), Set.of());
            Faq faq = new Faq();
            faq.setQuestionTitle("Question " + i);
            faq.setQuestionAnswer("Answer " + i);
            faq.setFaqState(FaqState.ACCEPTED);
            faq.setCourse(course);
            faqRepository.save(faq);
        }
        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 1, 1);

        // Result creation schedules asynchronous participant-score updates. Drain them before profiling so their
        // repository work is not attributed to whichever overview endpoint happens to run first.
        await().atMost(60, TimeUnit.SECONDS).until(() -> participantScoreScheduleService.isIdle());

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    /**
     * Gives the student a programming participation with one submission and one automatic result.
     *
     * Written out here rather than taken from a util service because the payload assertions depend on the exact build
     * outcome and test counts, and because the shared helpers provision a local VC repository this profile does not
     * otherwise need.
     */
    private void seedProgrammingSubmission(ProgrammingExercise exercise, User student) {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setParticipant(student);
        participation.setExercise(exercise);
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setInitializationDate(ZonedDateTime.now().minusDays(1));
        participation = studentParticipationTestRepository.save(participation);

        var submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        submission.setSubmitted(true);
        submission.setSubmissionDate(ZonedDateTime.now().minusHours(2));
        submission.setType(SubmissionType.MANUAL);
        submission.setBuildFailed(false);
        submission = submissionTestRepository.save(submission);

        var result = new Result();
        result.setSubmission(submission);
        result.setExerciseId(exercise.getId());
        result.setRated(true);
        result.setScore(80.0);
        result.setSuccessful(true);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now().minusHours(1));
        result.setTestCaseCount(10);
        result.setPassedTestCaseCount(8);
        result.setCodeIssueCount(3);
        resultTestRepository.save(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void profileCourseOverviewEndpoints() throws Exception {
        long courseId = course.getId();
        List<Endpoint> endpoints = List.of(
                new Endpoint("guard + sidebar", "GET /courses/{id}/available-tabs",
                        () -> request.get("/api/course/courses/" + courseId + "/available-tabs", HttpStatus.OK, CourseAvailableTabsDTO.class)),
                new Endpoint("container", "GET /courses/{id}/for-overview",
                        () -> request.get("/api/course/courses/" + courseId + "/for-overview", HttpStatus.OK, CourseForOverviewDTO.class)),
                new Endpoint("exercises + statistics tab", "GET /courses/{id}/exercises-for-overview",
                        () -> request.get("/api/course/courses/" + courseId + "/exercises-for-overview", HttpStatus.OK, CourseExercisesForOverviewDTO.class)),
                new Endpoint("lectures tab", "GET /courses/{id}/lectures-for-overview",
                        () -> request.getSet("/api/lecture/courses/" + courseId + "/lectures-for-overview", HttpStatus.OK, LectureForOverviewDTO.class)),
                new Endpoint("exams tab", "GET /courses/{id}/exams-for-overview",
                        () -> request.getSet("/api/exam/courses/" + courseId + "/exams-for-overview", HttpStatus.OK, ExamForOverviewDTO.class)),
                new Endpoint("DEPRECATED (native clients)", "GET /courses/{id}/for-dashboard",
                        () -> request.get("/api/course/courses/" + courseId + "/for-dashboard", HttpStatus.OK, CourseForDashboardDTO.class)));

        for (int pass = 0; pass < WARM_UP_PASSES; pass++) {
            for (Endpoint endpoint : endpoints) {
                endpoint.call().run();
            }
        }

        List<Measurement> measurements = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            measurements.add(measure(endpoint));
        }

        // Measure again with the order reversed. If a number tracks the endpoint it is real; if it tracks the position
        // in the sequence it is a measurement artefact, and reporting it would be misleading.
        List<Endpoint> reversed = new ArrayList<>(endpoints);
        java.util.Collections.reverse(reversed);
        List<Measurement> reverseMeasurements = new ArrayList<>();
        for (Endpoint endpoint : reversed) {
            reverseMeasurements.add(measure(endpoint));
        }
        java.util.Collections.reverse(reverseMeasurements);

        logTable(measurements);
        log.info("Reverse-order pass (ordering-artefact check):");
        logTable(reverseMeasurements);

        // The whole point of the split: every per-tab call must stay far below the all-in-one call it replaced.
        long forDashboardQueries = measurements.getLast().queries();
        for (Measurement measurement : measurements.subList(0, measurements.size() - 1)) {
            assertThat(measurement.queries()).as("%s must issue fewer queries than the for-dashboard call it replaced", measurement.endpoint()).isLessThan(forDashboardQueries);
        }
        // Entering a course costs available-tabs + for-overview; that pair must stay cheap regardless of course size.
        long courseEntryQueries = measurements.get(0).queries() + measurements.get(1).queries();
        assertThat(courseEntryQueries).as("entering a course must not scale with its content").isLessThan(forDashboardQueries);
    }

    /**
     * Compares what one course visit costs on develop against what it costs after the split.
     *
     * Both patterns are replayed in the same JVM against the same seeded course, so the comparison carries none of the
     * noise a cross-branch measurement would (different machines, data, warm-up). That is sound because
     * {@code for-dashboard} is untouched by this change — measuring it here measures develop's behaviour exactly.
     *
     * On develop a course visit is a single for-dashboard call: it loads every tab's content up front, and switching
     * tabs afterwards is free because the client decides from the cached course. After the split, entering a course
     * loads only the course and its available tabs, and each tab loads its own content the first time it is opened.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void compareCourseVisitAgainstDevelop() throws Exception {
        long courseId = course.getId();
        Endpoint availableTabs = new Endpoint("", "available-tabs",
                () -> request.get("/api/course/courses/" + courseId + "/available-tabs", HttpStatus.OK, CourseAvailableTabsDTO.class));
        Endpoint forOverview = new Endpoint("", "for-overview", () -> request.get("/api/course/courses/" + courseId + "/for-overview", HttpStatus.OK, CourseForOverviewDTO.class));
        Endpoint exercises = new Endpoint("", "exercises-for-overview",
                () -> request.get("/api/course/courses/" + courseId + "/exercises-for-overview", HttpStatus.OK, CourseExercisesForOverviewDTO.class));
        Endpoint lectures = new Endpoint("", "lectures-for-overview",
                () -> request.getSet("/api/lecture/courses/" + courseId + "/lectures-for-overview", HttpStatus.OK, LectureForOverviewDTO.class));
        Endpoint forDashboard = new Endpoint("", "for-dashboard",
                () -> request.get("/api/course/courses/" + courseId + "/for-dashboard", HttpStatus.OK, CourseForDashboardDTO.class));

        // develop always pays the same price regardless of which tabs the student opens: everything is loaded up front.
        List<Endpoint> developVisit = List.of(forDashboard);

        List<Scenario> scenarios = List.of(
                // The exercises tab is NOT guarded (see courses.route.ts), so the guard never runs on the first three
                // scenarios: the available-tabs call they pay for belongs to the sidebar, not to the guard.
                new Scenario("Lands on exercises, then lectures, then communication", developVisit, List.of(availableTabs, forOverview, exercises, lectures)),
                new Scenario("Lands on exercises only", developVisit, List.of(availableTabs, forOverview, exercises)),
                new Scenario("Goes straight to communication, never opens exercises", developVisit, List.of(availableTabs, forOverview)),
                // Deep link into a guarded tab: the guard runs before the container exists, but shares its available-tabs
                // response with the sidebar, so it adds no extra server call.
                new Scenario("Deep link into lectures (guard runs first)", developVisit, List.of(availableTabs, forOverview, lectures)));

        for (int pass = 0; pass < WARM_UP_PASSES; pass++) {
            for (Scenario scenario : scenarios) {
                replay(scenario.develop());
                replay(scenario.afterSplit());
            }
        }

        StringBuilder table = new StringBuilder(String.format(Locale.ROOT, "%nOne course visit: develop vs this PR (same course, same JVM)%n%n"));
        table.append("| Student's visit | develop queries | PR queries | develop time | PR time |\n");
        table.append("|---|---:|---:|---:|---:|\n");
        for (Scenario scenario : scenarios) {
            PatternCost developCost = measurePattern(scenario.develop());
            PatternCost splitCost = measurePattern(scenario.afterSplit());
            table.append(String.format(Locale.ROOT, "| %s | %d | %d | %.1f ms | %.1f ms |%n", scenario.name(), developCost.queries(), splitCost.queries(),
                    developCost.medianMicros() / 1000.0, splitCost.medianMicros() / 1000.0));
        }
        log.info(table.toString());

        // The claim this change actually makes is about entering a course, not about a visit that opens every tab:
        // splitting one call into several adds a per-call overhead (each endpoint re-resolves the user, the course and
        // the authorisation), so a student who opens everything issues more queries in total. What must hold is that
        // getting into the course, and a visit that never opens the exercises tab, both got cheaper.
        PatternCost developEntry = measurePattern(List.of(forDashboard));
        PatternCost splitEntry = measurePattern(List.of(availableTabs, forOverview));
        log.info("Course entry only: develop {} queries / {} ms  vs  PR {} queries / {} ms", developEntry.queries(), developEntry.medianMicros() / 1000.0, splitEntry.queries(),
                splitEntry.medianMicros() / 1000.0);
        assertThat(splitEntry.queries()).as("entering a course must be cheaper than the for-dashboard call it replaces").isLessThan(developEntry.queries());

        // Attribute the cost of a "lands on exercises" visit, the one that got more expensive. The guard is not involved:
        // the exercises route is unguarded, so every query below belongs to the container, the sidebar or the content.
        PatternCost container = measurePattern(List.of(forOverview));
        PatternCost sidebar = measurePattern(List.of(availableTabs));
        PatternCost content = measurePattern(List.of(exercises));
        StringBuilder attribution = new StringBuilder(
                String.format(Locale.ROOT, "%nWhere a 'lands on exercises' visit spends its queries (develop total: %d)%n%n", developEntry.queries()));
        attribution.append("| Concern | Call | Queries | Time | On develop |\n");
        attribution.append("|---|---|---:|---:|---|\n");
        attribution.append(
                String.format(Locale.ROOT, "| Course record | `for-overview` | %d | %.1f ms | part of for-dashboard |%n", container.queries(), container.medianMicros() / 1000.0));
        attribution.append(String.format(Locale.ROOT, "| Sidebar (shared with the guard when a guarded tab is opened) | `available-tabs` | %d | %.1f ms | "
                + "free, derived client-side from the for-dashboard payload |%n", sidebar.queries(), sidebar.medianMicros() / 1000.0));
        attribution.append(String.format(Locale.ROOT, "| Exercise list content | `exercises-for-overview` | %d | %.1f ms | part of for-dashboard |%n", content.queries(),
                content.medianMicros() / 1000.0));
        log.info(attribution.toString());
    }

    /**
     * What a student actually pays for, action by action, in a realistically sized course.
     *
     * This is the table quoted in the pull request: how many REST calls each action costs, how many database queries
     * those calls issue, and how long they take. Actions that cost nothing are the point of the change — the per-visit
     * state in {@code CourseAvailableTabsService} and {@code CourseOverviewExercisesService} means a tab is only paid
     * for the first time it is opened, and revisiting it within the same course visit is free.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void profileStudentActions() throws Exception {
        long courseId = course.getId();
        Endpoint availableTabs = new Endpoint("", "available-tabs",
                () -> request.get("/api/course/courses/" + courseId + "/available-tabs", HttpStatus.OK, CourseAvailableTabsDTO.class));
        Endpoint forOverview = new Endpoint("", "for-overview", () -> request.get("/api/course/courses/" + courseId + "/for-overview", HttpStatus.OK, CourseForOverviewDTO.class));
        Endpoint exercises = new Endpoint("", "exercises-for-overview",
                () -> request.get("/api/course/courses/" + courseId + "/exercises-for-overview", HttpStatus.OK, CourseExercisesForOverviewDTO.class));
        Endpoint lectures = new Endpoint("", "lectures-for-overview",
                () -> request.getSet("/api/lecture/courses/" + courseId + "/lectures-for-overview", HttpStatus.OK, LectureForOverviewDTO.class));
        Endpoint exams = new Endpoint("", "exams-for-overview",
                () -> request.getSet("/api/exam/courses/" + courseId + "/exams-for-overview", HttpStatus.OK, ExamForOverviewDTO.class));
        Endpoint forDashboard = new Endpoint("", "for-dashboard",
                () -> request.get("/api/course/courses/" + courseId + "/for-dashboard", HttpStatus.OK, CourseForDashboardDTO.class));

        // Counted here is only what the course overview itself loads. Tabs that fetch their own content through endpoints
        // this change does not touch (conversations, the FAQ list, competencies, tutorial groups) still do so exactly as
        // before, so those calls are unchanged and not attributed to the overview.
        List<Action> actions = List.of(new Action("Enter the course (lands on the exercises tab)", List.of(availableTabs, forOverview, exercises)),
                new Action("Open the lectures tab", List.of(lectures)), new Action("Open the exams tab", List.of(exams)),
                new Action("Open the statistics tab (shares the exercises tab's load)", List.of()), new Action("Re-open any tab already visited in this course visit", List.of()),
                new Action("Open communication / FAQ / competencies / tutorial groups (each loads its own content, unchanged here)", List.of()));

        for (int pass = 0; pass < WARM_UP_PASSES; pass++) {
            for (Action action : actions) {
                replay(action.calls());
            }
            replay(List.of(forDashboard));
        }

        StringBuilder table = new StringBuilder(String.format(Locale.ROOT,
                "%nWhat a student pays per action (%d lectures / %d exercises with participations / %d competencies / %d FAQs / %d tutorial groups / 1 exam)%n%n", CONTENT_PER_TYPE,
                CONTENT_PER_TYPE, CONTENT_PER_TYPE, CONTENT_PER_TYPE, CONTENT_PER_TYPE));
        table.append("| Student action | REST calls from the course overview | DB queries | Time |\n");
        table.append("|---|---:|---:|---:|\n");
        for (Action action : actions) {
            if (action.calls().isEmpty()) {
                table.append(String.format(Locale.ROOT, "| %s | 0 | 0 | – |%n", action.name()));
                continue;
            }
            PatternCost cost = measurePattern(action.calls());
            table.append(String.format(Locale.ROOT, "| %s | %d | %d | %.1f ms |%n", action.name(), action.calls().size(), cost.queries(), cost.medianMicros() / 1000.0));
        }
        PatternCost dashboard = measurePattern(List.of(forDashboard));
        table.append(String.format(Locale.ROOT, "| _(develop: entering the course loaded all of the above)_ | 1 | %d | %.1f ms |%n", dashboard.queries(),
                dashboard.medianMicros() / 1000.0));
        log.info(table.toString());
    }

    /**
     * Guards the size of what the course overview puts on the wire.
     *
     * The exercise payload dominates it, and the single largest item in that payload used to be the requesting user's
     * own account serialised once per exercise, through each participation. This asserts it stays gone.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotRepeatTheRequestingUserOnEveryParticipation() throws Exception {
        var response = request.performMvcRequest(MockMvcRequestBuilders.get("/api/course/courses/" + course.getId() + "/exercises-for-overview")).andReturn().getResponse();
        String body = response.getContentAsString();
        int payloadSizeBytes = response.getContentAsByteArray().length;

        assertThat(payloadSizeBytes).as("the exercise overview payload must stay within its performance budget").isLessThanOrEqualTo(MAX_EXERCISE_OVERVIEW_PAYLOAD_BYTES);

        // The colon matters: "student" alone also matches "studentParticipations" and "studentAssignedTeamIdComputed"
        assertThat(body).as("the participations must not carry the student, which the client already knows").doesNotContain("\"student\":");
        assertThat(body).as("nor the derived participant fields that come with it").doesNotContain("\"participantName\":");
        // Everything the overview renders must be here...
        assertThat(body).as("the exercise fields the overview renders").contains("\"title\":", "\"dueDate\":", "\"maxPoints\":", "\"difficulty\":", "\"categories\":",
                "\"includedInOverallScore\":", "\"assessmentDueDate\":", "\"studentParticipations\":", "\"allowFeedbackRequests\":", "\"allowOnlineEditor\":",
                "\"allowOfflineIde\":", "\"staticCodeAnalysisEnabled\":");
        // The result string is built from these: without them every programming result reads as "build successful, no
        // tests" and a failed build reads as a successful one
        assertThat(body).as("the result fields the exercise card's result string is built from").contains("\"testCaseCount\":", "\"passedTestCaseCount\":", "\"codeIssueCount\":",
                "\"buildFailed\":");
        // The client discriminates submissions on this, and it is derived from the exercise rather than read off the
        // submission row. Only the programming submission is visible here: the text exercises' assessment due dates are
        // still in the future, so the visibility rules withhold their submissions.
        assertThat(body).as("a submission must carry the discriminator its exercise implies").contains("\"submissionExerciseType\":\"programming\"");
        // ...and the long tail it never reads must not be, especially the programming configuration
        assertThat(body).as("fields no overview consumer reads").doesNotContain("\"projectKey\":", "\"packageName\":", "\"programmingLanguage\":", "\"projectType\":",
                "\"shortName\":", "\"buildAndTestStudentSubmissionsAfterDueDate\":", "\"allowOnlineIde\":", "\"showTestNamesToStudents\":", "\"testCasesChanged\":",
                "\"secondCorrectionEnabled\":", "\"gradingInstructionFeedbackUsed\":");
        // Results carry no feedbacks: only the scores export and the assessment views need them, and they load their own
        assertThat(body).as("results must not carry their feedbacks").doesNotContain("\"feedbacks\":");
        log.info("exercises-for-overview response size: {} bytes for {} exercises", payloadSizeBytes, CONTENT_PER_TYPE);
    }

    /**
     * Guards the database-to-server side of the optimization. Constructor projections must not silently regress to
     * fetching the exercise graph and pruning it after Hibernate has already materialized it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotHydrateTheExerciseEntityGraph() throws Exception {
        // Its per-minute catch-up cron cannot be mocked away like the other two scanners: setUp()'s own idle-wait
        // relies on its real debounced processing of the results just created. Stopping it here, once that
        // processing has already settled, keeps its fixed-delay initial scan from firing mid-measurement instead.
        participantScoreScheduleService.shutdown();
        statistics.clear();

        // Count the request's queries with the thread-scoped HibernateQueryInterceptor (via assertThatDb), not the
        // global Hibernate statistics: the Weaviate outbox dispatcher's periodic drain runs on a scheduler thread and
        // its query would otherwise inflate the global count and flake this assertion. The entity-load checks below stay
        // on the global statistics — they track specific entity types the drain never touches, so they are unaffected.
        assertThatDb(() -> request.get("/api/course/courses/" + course.getId() + "/exercises-for-overview", HttpStatus.OK, CourseExercisesForOverviewDTO.class))
                .as("the projection-backed exercise overview query budget").hasBeenCalledAtMostTimes(MAX_EXERCISE_OVERVIEW_QUERIES);

        assertEntityWasNotLoaded(Exercise.class);
        assertEntityWasNotLoaded(TextExercise.class);
        assertEntityWasNotLoaded(ProgrammingExercise.class);
        assertEntityWasNotLoaded(ExerciseVariantGroup.class);
        assertEntityWasNotLoaded(Team.class);
        assertEntityWasNotLoaded(StudentParticipation.class);
        assertEntityWasNotLoaded(TextSubmission.class);
        assertEntityWasNotLoaded(Result.class);
        assertEntityWasNotLoaded(Feedback.class);
        assertEntityWasNotLoaded(GradingScale.class);
        assertEntityWasNotLoaded(PlagiarismCase.class);
    }

    private void assertEntityWasNotLoaded(Class<?> entityType) {
        assertThat(statistics.getEntityStatistics(entityType.getName()).getLoadCount()).as("%s must stay projection-only", entityType.getSimpleName()).isZero();
    }

    private void replay(List<Endpoint> pattern) throws Exception {
        for (Endpoint endpoint : pattern) {
            endpoint.call().run();
        }
    }

    private PatternCost measurePattern(List<Endpoint> pattern) throws Exception {
        List<Long> durationsMicros = new ArrayList<>();
        long queries = 0;
        for (int i = 0; i < MEASURED_RUNS; i++) {
            statistics.clear();
            long start = System.nanoTime();
            replay(pattern);
            durationsMicros.add((System.nanoTime() - start) / 1_000);
            queries = statistics.getPrepareStatementCount();
        }
        durationsMicros.sort(Long::compareTo);
        return new PatternCost(queries, durationsMicros.get(durationsMicros.size() / 2));
    }

    private Measurement measure(Endpoint endpoint) throws Exception {
        List<Long> durationsMicros = new ArrayList<>();
        long queries = 0;
        for (int i = 0; i < MEASURED_RUNS; i++) {
            statistics.clear();
            long start = System.nanoTime();
            endpoint.call().run();
            durationsMicros.add((System.nanoTime() - start) / 1_000);
            queries = statistics.getPrepareStatementCount();
        }
        durationsMicros.sort(Long::compareTo);
        return new Measurement(endpoint.consumer(), endpoint.path(), queries, durationsMicros.get(durationsMicros.size() / 2));
    }

    private void logTable(List<Measurement> measurements) {
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT,
                "%nCourse overview load profile (%d lectures / %d exercises, each with a participation, "
                        + "submission and result / %d competencies / %d FAQs / %d tutorial groups / 1 visible exam)%n%n",
                CONTENT_PER_TYPE, CONTENT_PER_TYPE, CONTENT_PER_TYPE, CONTENT_PER_TYPE, CONTENT_PER_TYPE));
        table.append("| Consumer | Endpoint | DB queries | Median time |\n");
        table.append("|---|---|---:|---:|\n");
        for (Measurement measurement : measurements) {
            table.append(String.format(Locale.ROOT, "| %s | `%s` | %d | %.1f ms |%n", measurement.consumer(), measurement.endpoint(), measurement.queries(),
                    measurement.medianMicros() / 1000.0));
        }
        log.info(table.toString());
    }

    private record Endpoint(String consumer, String path, ThrowingRunnable call) {
    }

    private record Scenario(String name, List<Endpoint> develop, List<Endpoint> afterSplit) {
    }

    private record Action(String name, List<Endpoint> calls) {
    }

    private record PatternCost(long queries, long medianMicros) {
    }

    private record Measurement(String consumer, String endpoint, long queries, long medianMicros) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;
    }

    /**
     * Both scoring paths must apply a plagiarism deduction attached to the student's team.
     *
     * They did not: the query behind for-dashboard matched only {@code plagiarismCase.student}, while its own sibling
     * used for exam bonus scoring already joined the team members. A student therefore saw a different total on the web
     * than on a native client for the same exercise in the same second.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void bothScoringPathsShouldApplyATeamPlagiarismDeduction() throws Exception {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        TextExercise teamExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1),
                ZonedDateTime.now().minusHours(1));
        teamExercise.setMode(ExerciseMode.TEAM);
        teamExercise.setMaxPoints(10.0);
        teamExercise = exerciseRepository.save(teamExercise);
        Team team = teamUtilService.createTeam(Set.of(student), tutor, teamExercise, TEST_PREFIX + "plagteam");

        var teamParticipation = participationUtilService.addTeamParticipationForExercise(teamExercise, team.getId());
        participationUtilService.createSubmissionAndResult(teamParticipation, 80, true);

        PlagiarismCase teamCase = new PlagiarismCase();
        teamCase.setExercise(teamExercise);
        teamCase.setTeam(team);
        teamCase.setVerdict(PlagiarismVerdict.POINT_DEDUCTION);
        teamCase.setVerdictPointDeduction(50);
        plagiarismCaseRepository.save(teamCase);

        await().atMost(60, TimeUnit.SECONDS).until(() -> participantScoreScheduleService.isIdle());

        var fromDashboard = request.get("/api/course/courses/" + course.getId() + "/for-dashboard", HttpStatus.OK, CourseForDashboardDTO.class);
        var fromOverview = request.get("/api/course/courses/" + course.getId() + "/exercises-for-overview", HttpStatus.OK, CourseExercisesForOverviewDTO.class);

        assertThat(fromOverview.textScores().studentScores().absoluteScore()).as("the deduction must apply on the overview")
                .isEqualTo(fromDashboard.textScores().studentScores().absoluteScore());
        assertThat(fromOverview.totalScores().studentScores().absoluteScore()).as("and the totals must agree")
                .isEqualTo(fromDashboard.totalScores().studentScores().absoluteScore());
    }

    /**
     * A student who starts an exercise but never submits has a participation with no submissions at all. The row
     * projection reaches its submission columns through a LEFT JOIN, so every one of them is null for such a
     * participation — including the one the query selects the submission's concrete type from.
     * <p>
     * Regression test for a 500 on the exercises tab: selecting {@code TYPE(submission)} over that outer join made
     * Hibernate try to map a null discriminator to an entity and fail with "Could not resolve discriminator value".
     * It only showed up for users who actually had such a participation, so every seeded-and-submitted fixture passed.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldLoadTheOverviewWhenAnExerciseWasStartedButNeverSubmitted() throws Exception {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        TextExercise startedExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().plusDays(2),
                ZonedDateTime.now().plusDays(4));

        var participation = new StudentParticipation();
        participation.setParticipant(student);
        participation.setExercise(startedExercise);
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setInitializationDate(ZonedDateTime.now().minusHours(3));
        participation = studentParticipationTestRepository.save(participation);
        long participationId = participation.getId();

        var overview = request.get("/api/course/courses/" + course.getId() + "/exercises-for-overview", HttpStatus.OK, CourseExercisesForOverviewDTO.class);

        var startedParticipations = overview.exercises().stream().filter(exercise -> exercise.id().equals(startedExercise.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("the started exercise must be part of the overview")).studentParticipations();
        assertThat(startedParticipations).as("the participation must be reported so the card shows the exercise as started").extracting(ParticipationOverviewDTO::id)
                .containsExactly(participationId);
        assertThat(startedParticipations.iterator().next().submissions()).as("and it must carry no submission").isNullOrEmpty();

        // The other half of the change: the submission type is now derived from the exercise, so an actual submission
        // still has to come out as its own kind. Assessment already released, otherwise the submission stays hidden.
        TextExercise assessedExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(4), ZonedDateTime.now().minusDays(3),
                ZonedDateTime.now().minusDays(2));
        participationUtilService.createParticipationSubmissionAndResult(assessedExercise.getId(), student, 10.0, 0.0, 80, true);
        await().atMost(60, TimeUnit.SECONDS).until(() -> participantScoreScheduleService.isIdle());

        var withSubmission = request.get("/api/course/courses/" + course.getId() + "/exercises-for-overview", HttpStatus.OK, CourseExercisesForOverviewDTO.class);

        var assessedSubmissions = withSubmission.exercises().stream().filter(exercise -> exercise.id().equals(assessedExercise.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("the assessed exercise must be part of the overview")).studentParticipations().iterator().next().submissions();
        assertThat(assessedSubmissions).as("the assessed submission must be visible").hasSize(1);
        assertThat(assessedSubmissions.iterator().next().submissionExerciseType()).as("and it must carry the discriminator its exercise implies").isEqualTo("text");
    }

    /**
     * The same outer-join hazard, reached through the test-run path: the overview always asks for test runs, so an
     * instructor's untouched test run is enough to break the tab for them even when no student ever started anything.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldLoadTheOverviewWithATestRunThatHasNoSubmission() throws Exception {
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        TextExercise exercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().plusDays(2),
                ZonedDateTime.now().plusDays(4));

        var testRun = new StudentParticipation();
        testRun.setParticipant(instructor);
        testRun.setExercise(exercise);
        testRun.setTestRun(true);
        testRun.setInitializationState(InitializationState.INITIALIZED);
        testRun.setInitializationDate(ZonedDateTime.now().minusHours(2));
        studentParticipationTestRepository.save(testRun);

        var overview = request.get("/api/course/courses/" + course.getId() + "/exercises-for-overview", HttpStatus.OK, CourseExercisesForOverviewDTO.class);

        assertThat(overview.exercises()).as("the overview must still load for the instructor").isNotEmpty();
    }

}
