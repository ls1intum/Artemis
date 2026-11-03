package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.admin.dto.BuildUsageDTO;
import de.tum.cit.aet.artemis.admin.dto.PublicUsageStatisticsDTO;
import de.tum.cit.aet.artemis.admin.dto.StudentSubmissionCountDTO;
import de.tum.cit.aet.artemis.admin.repository.PublicUsageStatisticsRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyUsageStatisticsApi;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ReactionTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.api.ExamUsageStatisticsApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupUsageStatisticsApi;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

/**
 * Other tests share the database and clean up their data at arbitrary moments, so these tests never compare global numbers. They
 * only ask the queries about the courses they create themselves.
 */
class PublicUsageStatisticsTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "publicusage";

    private static final int MIN_STUDENTS = PublicUsageStatisticsService.MIN_STUDENTS_PER_COURSE;

    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Autowired
    private PublicUsageStatisticsRepository publicUsageStatisticsRepository;

    @Autowired
    private PublicUsageStatisticsService publicUsageStatisticsService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private UserTestRepository userTestRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private TutorialGroupUtilService tutorialGroupUtilService;

    @Autowired
    private Optional<TutorialGroupUsageStatisticsApi> tutorialGroupUsageStatisticsApi;

    @Autowired
    private Optional<ExamUsageStatisticsApi> examUsageStatisticsApi;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private Optional<CompetencyUsageStatisticsApi> competencyUsageStatisticsApi;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private BuildJobTestRepository buildJobRepository;

    @Autowired
    private ReactionTestRepository reactionRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private FaqRepository faqRepository;

    private final UsageSemester currentSemester = UsageSemester.containing(ZonedDateTime.now(ZONE).toLocalDate());

    private Set<Long> realCourseIds() {
        return publicUsageStatisticsRepository.findRealCourseIds(currentSemester.start(ZONE), currentSemester.end(ZONE), MIN_STUDENTS);
    }

    private Course createCourseStartingNow(String userPrefix, int students) {
        userUtilService.addUsers(userPrefix, students, 0, 0, 0);
        Course course = courseUtilService.createCourse();
        course.setStartDate(ZonedDateTime.now());
        course.setEndDate(ZonedDateTime.now().plusMonths(3));
        courseUtilService.saveCourse(course);
        userUtilService.enrollPrefixedUsersInCourse(course, userPrefix);
        return course;
    }

    private TextSubmission submission(SubmissionType type) {
        TextSubmission submission = ParticipationFactory.generateTextSubmission("text", Language.ENGLISH, true);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.setType(type);
        return submission;
    }

    private TextExercise submitText(Course course, String login, SubmissionType type) {
        TextExercise exercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2));
        participationUtilService.addSubmission(exercise, submission(type), login);
        return exercise;
    }

    private long submissionsOf(Set<Long> courseIds, String login) {
        Long userId = userTestRepository.findOneByLogin(login).orElseThrow().getId();
        return publicUsageStatisticsRepository.countSubmissionsByStudent(publicUsageStatisticsRepository.findExerciseIdsOfCourses(courseIds)).stream()
                .filter(count -> userId.equals(count.studentId())).mapToLong(StudentSubmissionCountDTO::submissions).sum();
    }

    @Test
    void realCourse_isFoundAndCountedWithStudentsSubmissionsAndExercises() {
        Course course = createCourseStartingNow(TEST_PREFIX + "a", MIN_STUDENTS);
        submitText(course, TEST_PREFIX + "astudent1", SubmissionType.MANUAL);
        submitText(course, TEST_PREFIX + "astudent1", SubmissionType.MANUAL);
        Set<Long> ids = Set.of(course.getId());

        assertThat(realCourseIds()).contains(course.getId());
        assertThat(publicUsageStatisticsRepository.findStudentIds(ids)).hasSize(MIN_STUDENTS);
        assertThat(submissionsOf(ids, TEST_PREFIX + "astudent1")).isEqualTo(2);
        assertThat(publicUsageStatisticsRepository.countExercisesByType(ids)).singleElement().satisfies(entry -> {
            assertThat(entry.exerciseType()).isEqualTo(ExerciseType.TEXT);
            assertThat(entry.value()).isEqualTo(2);
        });
    }

    @Test
    void submissionsCreatedByInstructorsOrTheSystem_doNotCount() {
        Course course = createCourseStartingNow(TEST_PREFIX + "g", MIN_STUDENTS);
        submitText(course, TEST_PREFIX + "gstudent1", SubmissionType.INSTRUCTOR);
        submitText(course, TEST_PREFIX + "gstudent2", SubmissionType.TEST);
        submitText(course, TEST_PREFIX + "gstudent3", SubmissionType.MANUAL);
        Set<Long> ids = Set.of(course.getId());

        assertThat(submissionsOf(ids, TEST_PREFIX + "gstudent1")).isZero();
        assertThat(submissionsOf(ids, TEST_PREFIX + "gstudent2")).isZero();
        assertThat(submissionsOf(ids, TEST_PREFIX + "gstudent3")).isEqualTo(1);
    }

    @Test
    void examExercisesAndTheirSubmissions_belongToTheCourseOfTheExam() {
        Course course = createCourseStartingNow(TEST_PREFIX + "h", MIN_STUDENTS);
        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        TextExercise examExercise = textExerciseUtilService.createTextExerciseForExam(exam.getExerciseGroups().getFirst());
        participationUtilService.addSubmission(examExercise, submission(SubmissionType.MANUAL), TEST_PREFIX + "hstudent1");
        Set<Long> ids = Set.of(course.getId());

        assertThat(publicUsageStatisticsRepository.findExerciseIdsOfCourses(ids)).containsExactly(examExercise.getId());
        assertThat(publicUsageStatisticsRepository.countExercisesByType(ids)).singleElement().satisfies(entry -> assertThat(entry.value()).isEqualTo(1));
        assertThat(submissionsOf(ids, TEST_PREFIX + "hstudent1")).isEqualTo(1);
    }

    @Test
    void courseBelowStudentThreshold_isNotFound() {
        Course course = createCourseStartingNow(TEST_PREFIX + "b", MIN_STUDENTS - 1);

        assertThat(realCourseIds()).doesNotContain(course.getId());
    }

    @Test
    void testCourse_isNotFound() {
        Course course = createCourseStartingNow(TEST_PREFIX + "c", MIN_STUDENTS + 1);
        course.setTestCourse(true);
        courseUtilService.saveCourse(course);

        assertThat(realCourseIds()).doesNotContain(course.getId());
    }

    @Test
    void testUsers_doNotCountAsStudentsAndLeaveTheCourseBelowTheThreshold() {
        Course course = createCourseStartingNow(TEST_PREFIX + "d", MIN_STUDENTS);
        User testUser = userTestRepository.findOneByLogin(TEST_PREFIX + "dstudent1").orElseThrow();
        testUser.setTestUser(true);
        userTestRepository.save(testUser);

        assertThat(publicUsageStatisticsRepository.findStudentIds(Set.of(course.getId()))).hasSize(MIN_STUDENTS - 1).doesNotContain(testUser.getId());
        assertThat(realCourseIds()).doesNotContain(course.getId());
    }

    @Test
    void userInTwoRealCourses_isCountedOnce() {
        Course first = createCourseStartingNow(TEST_PREFIX + "e", MIN_STUDENTS);
        Course second = courseUtilService.createCourse();
        second.setStartDate(ZonedDateTime.now());
        courseUtilService.saveCourse(second);
        userUtilService.enrollPrefixedUsersInCourse(second, TEST_PREFIX + "e");

        assertThat(realCourseIds()).contains(first.getId(), second.getId());
        assertThat(publicUsageStatisticsRepository.findStudentIds(Set.of(first.getId(), second.getId()))).hasSize(MIN_STUDENTS);
    }

    @Test
    void courseStartingInThePreviousSemester_isNotFoundInTheCurrentSemester() {
        Course course = createCourseStartingNow(TEST_PREFIX + "f", MIN_STUDENTS);
        course.setStartDate(currentSemester.start(ZONE).minusDays(1));
        courseUtilService.saveCourse(course);

        assertThat(realCourseIds()).doesNotContain(course.getId());
        assertThat(publicUsageStatisticsRepository.findRealCourseIds(currentSemester.previous().start(ZONE), currentSemester.previous().end(ZONE), MIN_STUDENTS))
                .contains(course.getId());
    }

    private void saveBuildJob(Course course, BuildStatus status, long durationSeconds) {
        BuildJob buildJob = new BuildJob();
        buildJob.setCourseId(course.getId());
        buildJob.setBuildStatus(status);
        ZonedDateTime completion = ZonedDateTime.parse("2025-01-15T12:00:00Z");
        buildJob.setBuildStartDate(completion.minusSeconds(durationSeconds));
        buildJob.setBuildCompletionDate(completion);
        buildJobRepository.save(buildJob);
    }

    @Test
    void finishedBuildJobs_areCountedAndTheAverageDurationUsesTheSuccessfulOnes() {
        Course course = createCourseStartingNow(TEST_PREFIX + "k", MIN_STUDENTS);
        Course courseWithoutBuilds = createCourseStartingNow(TEST_PREFIX + "l", MIN_STUDENTS);
        saveBuildJob(course, BuildStatus.SUCCESSFUL, 10);
        saveBuildJob(course, BuildStatus.SUCCESSFUL, 20);
        saveBuildJob(course, BuildStatus.FAILED, 100);
        saveBuildJob(course, BuildStatus.ERROR, 5);
        saveBuildJob(course, BuildStatus.TIMEOUT, 300);

        BuildUsageDTO usage = publicUsageStatisticsRepository.summarizeFinishedBuildJobs(Set.of(course.getId()));

        assertThat(usage.count()).isEqualTo(5);
        assertThat(usage.averageDurationSeconds()).isCloseTo(15.0, within(1.0));
        BuildUsageDTO noUsage = publicUsageStatisticsRepository.summarizeFinishedBuildJobs(Set.of(courseWithoutBuilds.getId()));
        assertThat(noUsage.count()).isZero();
        assertThat(noUsage.averageDurationSeconds()).isNull();
    }

    @Test
    void buildJobsThatDidNotRun_areNotCounted() {
        Course course = createCourseStartingNow(TEST_PREFIX + "r", MIN_STUDENTS);
        saveBuildJob(course, BuildStatus.QUEUED, 0);
        saveBuildJob(course, BuildStatus.BUILDING, 0);
        saveBuildJob(course, BuildStatus.CANCELLED, 0);
        // a missing job is retried as a new job, so counting both would count the build twice
        saveBuildJob(course, BuildStatus.MISSING, 0);
        saveBuildJob(course, BuildStatus.SUCCESSFUL, 10);

        BuildUsageDTO usage = publicUsageStatisticsRepository.summarizeFinishedBuildJobs(Set.of(course.getId()));

        assertThat(usage.count()).isEqualTo(1);
    }

    @Test
    void postsRepliesAndReactions_areCountedPerCourseWithoutContentOrAuthors() {
        Course course = createCourseStartingNow(TEST_PREFIX + "m", MIN_STUDENTS);
        Course otherCourse = createCourseStartingNow(TEST_PREFIX + "n", MIN_STUDENTS);
        User author = userTestRepository.findOneByLogin(TEST_PREFIX + "mstudent1").orElseThrow();
        Channel channel = conversationUtilService.createCourseWideChannel(course, "usage-statistics");
        Post firstPost = examUtilService.createPost(channel, "first", author);
        examUtilService.createPost(channel, "second", author);
        AnswerPost reply = new AnswerPost();
        reply.setContent("reply");
        reply.setPost(firstPost);
        reply.setAuthor(author);
        reply = answerPostRepository.save(reply);
        saveReaction(author, firstPost, null);
        saveReaction(author, null, reply);
        saveReaction(userTestRepository.findOneByLogin(TEST_PREFIX + "mstudent2").orElseThrow(), null, reply);
        // a reply that Iris drafted and no tutor verified yet is not visible to students, neither are the reactions to it
        AnswerPost unverifiedReply = new AnswerPost();
        unverifiedReply.setContent("draft");
        unverifiedReply.setPost(firstPost);
        unverifiedReply.setAuthor(author);
        unverifiedReply.setVerified(false);
        unverifiedReply = answerPostRepository.save(unverifiedReply);
        saveReaction(author, null, unverifiedReply);
        // the other course has activity of its own that must not leak into the numbers of the first course
        User otherAuthor = userTestRepository.findOneByLogin(TEST_PREFIX + "nstudent1").orElseThrow();
        Post otherPost = examUtilService.createPost(conversationUtilService.createCourseWideChannel(otherCourse, "usage-statistics-other"), "other", otherAuthor);
        saveReaction(otherAuthor, otherPost, null);
        Set<Long> ids = Set.of(course.getId());

        assertThat(publicUsageStatisticsRepository.countPosts(ids)).isEqualTo(2);
        assertThat(publicUsageStatisticsRepository.countAnswerPosts(ids)).isEqualTo(1);
        assertThat(publicUsageStatisticsRepository.countReactionsOnPosts(ids)).isEqualTo(1);
        assertThat(publicUsageStatisticsRepository.countReactionsOnAnswerPosts(ids)).isEqualTo(2);
        Set<Long> otherIds = Set.of(otherCourse.getId());
        assertThat(publicUsageStatisticsRepository.countPosts(otherIds)).isEqualTo(1);
        assertThat(publicUsageStatisticsRepository.countAnswerPosts(otherIds)).isZero();
        assertThat(publicUsageStatisticsRepository.countReactionsOnPosts(otherIds)).isEqualTo(1);
        assertThat(publicUsageStatisticsRepository.countReactionsOnAnswerPosts(otherIds)).isZero();
    }

    private void saveReaction(User user, Post post, AnswerPost answerPost) {
        Reaction reaction = new Reaction();
        reaction.setEmojiId("smiley");
        reaction.setUser(user);
        reaction.setPost(post);
        reaction.setAnswerPost(answerPost);
        reactionRepository.save(reaction);
    }

    @Test
    void onlyAcceptedFaqs_areCounted() {
        Course course = createCourseStartingNow(TEST_PREFIX + "o", MIN_STUDENTS);
        saveFaq(course, FaqState.ACCEPTED);
        saveFaq(course, FaqState.ACCEPTED);
        saveFaq(course, FaqState.PROPOSED);
        saveFaq(course, FaqState.REJECTED);

        assertThat(publicUsageStatisticsRepository.countAcceptedFaqs(Set.of(course.getId()))).isEqualTo(2);
    }

    private void saveFaq(Course course, FaqState state) {
        Faq faq = new Faq();
        faq.setQuestionTitle("question");
        faq.setQuestionAnswer("answer");
        faq.setCourse(course);
        faq.setFaqState(state);
        faqRepository.save(faq);
    }

    @Test
    void tutorialGroups_areCountedPerCourse() {
        assertThat(tutorialGroupUsageStatisticsApi).as("tutorial groups are enabled in the test setup").isPresent();
        Course course = createCourseStartingNow(TEST_PREFIX + "p", MIN_STUDENTS);
        Course otherCourse = createCourseStartingNow(TEST_PREFIX + "q", MIN_STUDENTS);
        User tutor = userTestRepository.findOneByLogin(TEST_PREFIX + "pstudent1").orElseThrow();
        tutorialGroupUtilService.createTutorialGroup(course.getId(), "first", "info", 10, false, "Garching", "German", tutor, Set.of());
        tutorialGroupUtilService.createTutorialGroup(course.getId(), "second", "info", 10, false, "Garching", "German", tutor, Set.of());

        assertThat(tutorialGroupUsageStatisticsApi.orElseThrow().countTutorialGroups(Set.of(course.getId()))).isEqualTo(2);
        assertThat(tutorialGroupUsageStatisticsApi.orElseThrow().countTutorialGroups(Set.of(otherCourse.getId()))).isZero();
    }

    private void saveExam(Course course, String title, boolean testExam) {
        Exam exam = testExam ? examUtilService.addTestExam(course) : examUtilService.addExam(course);
        exam.setTitle(title);
        examRepository.save(exam);
    }

    @Test
    void realExams_areCountedWithoutTestExamsAndWithoutTestOrMockTitles() {
        assertThat(examUsageStatisticsApi).as("exams are enabled in the test setup").isPresent();
        Course course = createCourseStartingNow(TEST_PREFIX + "r", MIN_STUDENTS);
        Course otherCourse = createCourseStartingNow(TEST_PREFIX + "s", MIN_STUDENTS);
        saveExam(course, "Final exam", false);
        saveExam(course, "Retake", false);
        // the word may appear elsewhere in the title of a real exam
        saveExam(course, "Contest of the mocking bird", false);
        saveExam(course, "Final test run", false);
        saveExam(course, "Test exam of the instructors", false);
        saveExam(course, "TESTKLAUSUR", false);
        saveExam(course, "mock exam", false);
        saveExam(course, "Mock: dry run", false);
        saveExam(course, "Practice for students", true);
        saveExam(otherCourse, "Final exam", false);

        assertThat(examUsageStatisticsApi.orElseThrow().countRealExams(Set.of(course.getId()))).isEqualTo(4);
        assertThat(examUsageStatisticsApi.orElseThrow().countRealExams(Set.of(course.getId(), otherCourse.getId()))).isEqualTo(5);
        assertThat(examUsageStatisticsApi.orElseThrow().countRealExams(Set.of(course.getId() + 100_000))).isZero();
    }

    @Test
    void competencies_areCountedPerCourse() {
        assertThat(competencyUsageStatisticsApi).as("atlas is enabled in the test setup").isPresent();
        Course course = createCourseStartingNow(TEST_PREFIX + "t", MIN_STUDENTS);
        Course otherCourse = createCourseStartingNow(TEST_PREFIX + "u", MIN_STUDENTS);
        competencyUtilService.createCompetency(course, "first");
        competencyUtilService.createCompetency(course, "second");
        competencyUtilService.createCompetency(otherCourse, "other");

        assertThat(competencyUsageStatisticsApi.orElseThrow().countCompetencies(Set.of(course.getId()))).isEqualTo(2);
        assertThat(competencyUsageStatisticsApi.orElseThrow().countCompetencies(Set.of(course.getId(), otherCourse.getId()))).isEqualTo(3);
        assertThat(competencyUsageStatisticsApi.orElseThrow().countCompetencies(Set.of(course.getId() + 100_000))).isZero();
    }

    @Test
    void publicEndpoint_isReachableWithoutAuthentication() throws Exception {
        var statistics = request.get("/api/core/public/usage-statistics", HttpStatus.OK, PublicUsageStatisticsDTO.class);

        assertThat(statistics).isNotNull();
        assertThat(statistics.generatedAt()).isNotNull();
    }

    @Test
    void result_isCachedUntilEvicted() {
        var cache = cacheManager.getCache(PublicUsageStatisticsService.CACHE_NAME);
        assertThat(cache).isNotNull();
        cache.clear();
        var first = publicUsageStatisticsService.getUsageStatistics();

        assertThat(publicUsageStatisticsService.getUsageStatistics().generatedAt()).isEqualTo(first.generatedAt());
    }
}
