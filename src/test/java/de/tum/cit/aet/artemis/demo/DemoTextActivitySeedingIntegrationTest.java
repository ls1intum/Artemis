package de.tum.cit.aet.artemis.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.account.api.AccountDemoApi;
import de.tum.cit.aet.artemis.account.api.AccountDemoApi.DemoUsers;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.api.AssessmentDemoApi;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradeType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.ParticipantScore;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.atlas.api.AtlasDemoApi;
import de.tum.cit.aet.artemis.communication.api.CommunicationDemoApi;
import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.dto.StatsForDashboardDTO;
import de.tum.cit.aet.artemis.course.api.CourseDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.demo.service.DemoCourseContentSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoDataSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoExerciseSeedingService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.fileupload.api.FileUploadDemoApi;
import de.tum.cit.aet.artemis.lecture.api.LectureDemoApi;
import de.tum.cit.aet.artemis.modeling.api.ModelingDemoApi;
import de.tum.cit.aet.artemis.programming.api.ProgrammingDemoApi;
import de.tum.cit.aet.artemis.quiz.api.QuizDemoApi;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.api.TextDemoApi;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.TextParticipationDTO;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupDemoApi;

/**
 * Tests the text exercises the {@code demo} profile seeds, what the demo users did in them, and the grading scale of the demo course.
 * <p>
 * Like {@link DemoDataSeedingIntegrationTest}, every test seeds first and stays correct regardless of what ran before it, because seeding is idempotent and the demo course
 * persists in the shared test database. The methods must not run in parallel, hence {@link ExecutionMode#SAME_THREAD}.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DemoTextActivitySeedingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private DemoDataSeedingService demoDataSeedingService;

    @Autowired
    private AccountDemoApi accountDemoApi;

    @Autowired
    private CourseDemoApi courseDemoApi;

    @Autowired
    private TextDemoApi textDemoApi;

    @Autowired
    private AssessmentDemoApi assessmentDemoApi;

    @Autowired
    private CommunicationDemoApi communicationDemoApi;

    @Autowired
    private ModelingDemoApi modelingDemoApi;

    @Autowired
    private FileUploadDemoApi fileUploadDemoApi;

    @Autowired
    private QuizDemoApi quizDemoApi;

    @Autowired
    private ProgrammingDemoApi programmingDemoApi;

    @Autowired
    private LectureDemoApi lectureDemoApi;

    @Autowired
    private AtlasDemoApi atlasDemoApi;

    @Autowired
    private TutorialGroupDemoApi tutorialGroupDemoApi;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Test
    void everyStudentSeesTheAssessmentOfTheirFinishedEssay() throws Exception {
        seed();
        TextExercise finishedEssay = finishedEssay();
        assertThat(finishedEssay.getDueDate()).as("the essay is over").isBefore(ZonedDateTime.now());
        assertThat(finishedEssay.getAssessmentDueDate()).as("its results are released").isBefore(ZonedDateTime.now());

        Set<Double> scores = new HashSet<>();
        for (User student : demoUsers().students()) {
            long participationId = participationOf(finishedEssay, student).getId();
            userUtilService.changeUser(student.getLogin());
            TextParticipationDTO participation = request.get("/api/text/participations/" + participationId + "/text-editor", HttpStatus.OK, TextParticipationDTO.class);

            assertThat(participation.submissions()).as("%s submitted one essay", student.getLogin()).singleElement().satisfies(submission -> {
                assertThat(submission.submitted()).isTrue();
                assertThat(submission.text()).isNotBlank();
                assertThat(submission.submissionDate().toInstant()).as("%s submitted in time", student.getLogin()).isBeforeOrEqualTo(finishedEssay.getDueDate().toInstant());
                assertThat(submission.results()).as("%s sees the assessment of their essay", student.getLogin()).singleElement().satisfies(result -> {
                    assertThat(result.rated()).as("the result counts towards the course score").isTrue();
                    assertThat(result.completionDate()).isNotNull();
                    assertThat(result.feedbacks()).as("the tutor explains the score").hasSizeBetween(2, 3).allSatisfy(feedback -> assertThat(feedback.detailText()).isNotBlank());
                    double points = result.feedbacks().stream().mapToDouble(FeedbackDTO::credits).sum();
                    assertThat(result.score()).as("the feedback adds up to the score").isCloseTo(100 * points / finishedEssay.getMaxPoints(), within(0.1));
                    scores.add(result.score());
                });
            });
        }
        assertThat(scores).as("the essays differ in quality, and so do their scores").hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void tutorsSeeEveryEssayAssessedAndOneOpenComplaint() throws Exception {
        seed();
        TextExercise finishedEssay = finishedEssay();
        DemoUsers users = demoUsers();

        userUtilService.changeUser(AccountDemoApi.DEMO_TUTOR_LOGIN);
        StatsForDashboardDTO stats = request.get("/api/exercise/exercises/" + finishedEssay.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfSubmissions().inTime()).as("every student submitted in time").isEqualTo(users.students().size());
        assertThat(stats.getTotalNumberOfAssessments()).as("every essay is assessed").isEqualTo(users.students().size());
        assertThat(stats.getNumberOfComplaints()).as("one student complained").isOne();
        assertThat(stats.getNumberOfOpenComplaints()).as("nobody answered the complaint yet").isOne();

        assertThat(complaintRepository.findAllByExerciseIdIn(Set.of(finishedEssay.getId()))).singleElement().satisfies(complaint -> {
            assertThat(complaint.getComplaintType()).isEqualTo(ComplaintType.COMPLAINT);
            assertThat(complaint.getStudent().getLogin()).as("a classmate of the demo student complained").startsWith(AccountDemoApi.DEMO_PEER_LOGIN_PREFIX);
            assertThat(complaint.getComplaintText()).isNotBlank();
            assertThat(complaint.isAccepted()).as("the complaint is neither accepted nor rejected").isNull();
        });

        Result demoStudentResult = latestResult(participationOf(finishedEssay, users.student()));
        assertThat(complaintRepository.findByResultId(demoStudentResult.getId())).as("visitors can complain about the result of the demo student themselves").isEmpty();
        ZonedDateTime complaintPeriodStart = Stream.of(demoStudentResult.getCompletionDate(), finishedEssay.getDueDate(), finishedEssay.getAssessmentDueDate())
                .max(Comparator.naturalOrder()).orElseThrow();
        assertThat(complaintPeriodStart.plusDays(demoCourse().getMaxComplaintTimeDays())).as("the complaint period of the demo student is still open").isAfter(ZonedDateTime.now());
    }

    @Test
    void keepsTheOngoingEssayOpen() {
        seed();

        TextExercise ongoingEssay = demoEssays().getFirst();
        assertThat(ongoingEssay.isVisibleToStudents()).as("the ongoing essay is released").isTrue();
        assertThat(ongoingEssay.getDueDate()).as("the ongoing essay is still open for submissions").isAfter(ZonedDateTime.now());
    }

    @Test
    void seedsTheGradingScaleTheClientProposes() {
        seed();

        GradingScale gradingScale = gradingScaleRepository.findByCourseId(demoCourse().getId()).orElseThrow();
        assertThat(gradingScale.getGradeType()).isEqualTo(GradeType.GRADE);
        assertThat(gradingScale.getGradeSteps().stream().sorted(Comparator.comparingDouble(GradeStep::getLowerBoundPercentage)))
                .extracting(GradeStep::getGradeName, GradeStep::getLowerBoundPercentage, GradeStep::isLowerBoundInclusive, GradeStep::getUpperBoundPercentage,
                        GradeStep::isUpperBoundInclusive, GradeStep::getIsPassingGrade)
                .containsExactly(tuple("5.0", 0.0, true, 40.0, false, false), tuple("4.7", 40.0, true, 45.0, false, false), tuple("4.3", 45.0, true, 50.0, false, false),
                        tuple("4.0", 50.0, true, 55.0, false, true), tuple("3.7", 55.0, true, 60.0, false, true), tuple("3.3", 60.0, true, 65.0, false, true),
                        tuple("3.0", 65.0, true, 70.0, false, true), tuple("2.7", 70.0, true, 75.0, false, true), tuple("2.3", 75.0, true, 80.0, false, true),
                        tuple("2.0", 80.0, true, 85.0, false, true), tuple("1.7", 85.0, true, 90.0, false, true), tuple("1.3", 90.0, true, 95.0, false, true),
                        tuple("1.0", 95.0, true, 100.0, true, true));
    }

    @Test
    void seedingTwiceCreatesNothingNew() {
        seed();
        long courseId = demoCourse().getId();
        TextActivity textActivity = textActivity(courseId);
        GradingScaleIdentity gradingScale = gradingScaleIdentity(courseId);

        seed();

        assertThat(textActivity(courseId)).as("seeding again must neither create nor replace text exercises or what happened in them").isEqualTo(textActivity);
        assertThat(gradingScaleIdentity(courseId)).as("seeding again must neither create nor replace the grading scale").isEqualTo(gradingScale);
    }

    @Test
    void recreatesTheDeletedFinishedEssayWithItsActivity() throws Exception {
        seed();
        List<TextExercise> essays = demoEssays();
        // Lets the pending participant score updates finish, then stops the updates, which is their state at startup until the service activates itself.
        await().atMost(Duration.ofSeconds(30)).until(participantScoreScheduleService::isIdle);
        participantScoreScheduleService.shutdown();
        userUtilService.changeUser(AccountDemoApi.DEMO_INSTRUCTOR_LOGIN);
        request.delete("/api/text/text-exercises/" + essays.getLast().getId(), HttpStatus.OK);

        seed();

        List<TextExercise> reseededEssays = demoEssays();
        assertThat(reseededEssays.getFirst().getId()).as("the ongoing essay is left alone").isEqualTo(essays.getFirst().getId());
        TextExercise recreatedEssay = reseededEssays.getLast();
        assertThat(recreatedEssay.getId()).as("the deleted essay is recreated").isNotEqualTo(essays.getLast().getId());

        Set<StudentParticipation> participations = studentParticipationRepository
                .findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseIdIgnoreTestRuns(recreatedEssay.getId());
        assertThat(participations).as("every student takes part again").extracting(participation -> participation.getStudent().orElseThrow().getLogin())
                .containsExactlyInAnyOrderElementsOf(demoUsers().students().stream().map(User::getLogin).toList());
        assertThat(participations).as("every essay is submitted and assessed again").allSatisfy(participation -> {
            assertThat(participation.getSubmissions()).as("the essay is submitted into the submission that starting the exercise created, like the client does").hasSize(1);
            Result result = latestResult(participation);
            assertThat(result.isRated()).isTrue();
            assertThat(result.getCompletionDate()).isNotNull();
            assertThat(result.getAssessor().getLogin()).isEqualTo(AccountDemoApi.DEMO_TUTOR_LOGIN);
        });
        assertThat(complaintRepository.findAllByExerciseIdIn(Set.of(recreatedEssay.getId()))).as("the complaint is filed again").hasSize(1);

        List<Tuple> expectedScores = participations.stream().map(participation -> tuple(participation.getStudent().orElseThrow().getId(), latestResult(participation).getScore()))
                .toList();
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(participantScoreRepository.findAllByExercise(recreatedEssay))
                        .as("the results count towards the scores of the students, although the participant score updates had not activated themselves yet")
                        .extracting(score -> score.getParticipant().getId(), ParticipantScore::getLastRatedScore).containsExactlyInAnyOrderElementsOf(expectedScores));
    }

    @Test
    void seedsTheGradingScaleWithoutTheTextModule() {
        seed();
        long courseId = demoCourse().getId();
        TextActivity textActivity = textActivity(courseId);
        gradingScaleRepository.delete(gradingScaleRepository.findByCourseId(courseId).orElseThrow());

        DemoDataSeedingService withoutTextModule = new DemoDataSeedingService(accountDemoApi, courseDemoApi,
                new DemoExerciseSeedingService(Optional.empty(), Optional.of(modelingDemoApi), Optional.of(fileUploadDemoApi), quizDemoApi, programmingDemoApi, assessmentDemoApi),
                new DemoCourseContentSeedingService(Optional.of(lectureDemoApi), Optional.of(atlasDemoApi), assessmentDemoApi, Optional.of(tutorialGroupDemoApi),
                        communicationDemoApi));
        assertThatCode(() -> withoutTextModule.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent())).as("seeding must work when the text module is disabled")
                .doesNotThrowAnyException();

        assertThat(gradingScaleRepository.findByCourseId(courseId)).as("the missing grading scale is recreated without the text module").isPresent();
        assertThat(textActivity(courseId)).as("a disabled text module leaves the existing text exercises alone").isEqualTo(textActivity);
    }

    private void seed() {
        demoDataSeedingService.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent());
    }

    private DemoUsers demoUsers() {
        return accountDemoApi.createDemoUsers();
    }

    private Course demoCourse() {
        return courseRepository.findAllByShortName(CourseDemoApi.DEMO_COURSE_SHORT_NAME).getFirst();
    }

    /**
     * The ongoing and the finished demo essay, which already exist because every test seeds first.
     */
    private List<TextExercise> demoEssays() {
        DemoUsers users = demoUsers();
        return textDemoApi.createDemo(demoCourse(), users.students(), users.tutor());
    }

    private TextExercise finishedEssay() {
        return demoEssays().getLast();
    }

    private StudentParticipation participationOf(TextExercise essay, User student) {
        return studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseIdIgnoreTestRuns(essay.getId()).stream()
                .filter(participation -> participation.isOwnedBy(student)).findFirst().orElseThrow();
    }

    private static Result latestResult(StudentParticipation participation) {
        return participation.getSubmissions().stream().flatMap(submission -> Stream.ofNullable(submission.getLatestResult())).findFirst().orElseThrow();
    }

    /**
     * Captures the identities of the text exercises of the demo course and of what happened in them, so that the idempotency assertions detect replaced records.
     */
    private TextActivity textActivity(long courseId) {
        Set<Long> exerciseIds = textExerciseRepository.findByCourseIdWithCategories(courseId).stream().map(DomainObject::getId).collect(Collectors.toSet());
        Set<StudentParticipation> participations = exerciseIds.stream()
                .flatMap(exerciseId -> studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseIdIgnoreTestRuns(exerciseId).stream())
                .collect(Collectors.toSet());
        Set<Submission> submissions = participations.stream().flatMap(participation -> participation.getSubmissions().stream()).collect(Collectors.toSet());
        return new TextActivity(exerciseIds, ids(participations.stream()), ids(submissions.stream()),
                ids(submissions.stream().flatMap(submission -> submission.getResults().stream())),
                ids(exerciseIds.isEmpty() ? Stream.<Complaint>empty() : complaintRepository.findAllByExerciseIdIn(exerciseIds).stream()));
    }

    private GradingScaleIdentity gradingScaleIdentity(long courseId) {
        GradingScale gradingScale = gradingScaleRepository.findByCourseId(courseId).orElseThrow();
        return new GradingScaleIdentity(gradingScale.getId(), ids(gradingScale.getGradeSteps().stream()));
    }

    private static Set<Long> ids(Stream<? extends DomainObject> entities) {
        return entities.map(DomainObject::getId).collect(Collectors.toSet());
    }

    private record TextActivity(Set<Long> exerciseIds, Set<Long> participationIds, Set<Long> submissionIds, Set<Long> resultIds, Set<Long> complaintIds) {
    }

    private record GradingScaleIdentity(long id, Set<Long> gradeStepIds) {
    }
}
