package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.assessment.web.ResultResource;
import de.tum.cit.aet.artemis.buildagent.util.BuildJobUtilService;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationDueDateUpdateDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationScoreSearchDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationSearchDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ParticipationServiceTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "participationservice";

    // Fixed instead of relative to now, so a failure reproduces with the same dates.
    private static final ZonedDateTime FIXED_EXERCISE_DUE_DATE = ZonedDateTime.parse("2200-01-10T12:00:00Z");

    private static final long PARTICIPATION_ID_THAT_DOES_NOT_EXIST = Long.MAX_VALUE;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ResultService resultService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private BuildJobUtilService buildJobUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    private ProgrammingExercise programmingExercise;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 1);
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        this.programmingExercise = ExerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        // TODO: is this actually needed?
        closeable = MockitoAnnotations.openMocks(this);
        jenkinsRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        jenkinsRequestMockProvider.reset();
        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * Test for methods of {@link ParticipationService} used by {@link ResultResource#createResultForExternalSubmission(Long, String, Result)}.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateParticipationForExternalSubmission() throws Exception {
        Optional<User> student = userRepository.findOneWithAuthoritiesByLogin(TEST_PREFIX + "student1");
        participationUtilService.mockCreationOfExerciseParticipation(false, null, programmingExercise, uriService, versionControlService, continuousIntegrationService);

        StudentParticipation participation = participationService.createParticipationWithEmptySubmissionIfNotExisting(programmingExercise, student.orElseThrow(),
                SubmissionType.EXTERNAL);
        assertThat(participation).isNotNull();
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getStudent()).contains(student.get());
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) participation.findLatestSubmission().orElseThrow();
        assertThat(programmingSubmission.getType()).isEqualTo(SubmissionType.EXTERNAL);
        assertThat(programmingSubmission.getResults()).isNullOrEmpty(); // results are not added in the invoked method above
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildJobsForResultsOfParticipation() throws Exception {
        User student = userRepository.findOneWithAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
        StudentParticipation participation = setupParticipation(programmingExercise, student, SubmissionType.EXTERNAL);

        Map<Long, String> resultBuildJobMap = resultService.getLogsAvailabilityForResults(participation.getId());
        assertThat(resultBuildJobMap).hasSize(1);
        assertThat(participation).isNotNull();
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getStudent()).contains(student);
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) participation.findLatestSubmission().orElseThrow();
        assertThat(programmingSubmission.getType()).isEqualTo(SubmissionType.EXTERNAL);
        assertThat(programmingSubmission.getResults()).hasSize(1);
    }

    @NonNull
    private StudentParticipation setupParticipation(ProgrammingExercise programmingExercise, User student, SubmissionType external) throws URISyntaxException {
        participationUtilService.mockCreationOfExerciseParticipation(false, null, programmingExercise, uriService, versionControlService, continuousIntegrationService);
        StudentParticipation participation = participationService.createParticipationWithEmptySubmissionIfNotExisting(programmingExercise, student, external);
        Submission submission = participation.getSubmissions().iterator().next();
        Result result = participationUtilService.addResultToSubmission(participation, submission);
        buildJobUtilService.addBuildJobForParticipationId(participation.getId(), programmingExercise.getId(), result);
        return participation;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildJobsForResultsOfExamParticipation() throws Exception {
        User student = userRepository.findOneWithAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
        ProgrammingExercise examExercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneProgrammingExercise(TEST_PREFIX);
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(examExercise);
        StudentParticipation participation = setupParticipation(examExercise, student, SubmissionType.INSTRUCTOR);

        Map<Long, String> resultBuildJobMap = resultService.getLogsAvailabilityForResults(participation.getId());
        assertThat(resultBuildJobMap).hasSize(1);
        assertThat(participation).isNotNull();
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getStudent()).contains(student);
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) participation.findLatestSubmission().orElseThrow();
        assertThat(programmingSubmission.getType()).isEqualTo(SubmissionType.INSTRUCTOR);
        assertThat(programmingSubmission.getResults()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void canStartExerciseWithPracticeParticipationAfterDueDateChange() throws URISyntaxException {
        Participant participant = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        participationUtilService.mockCreationOfExerciseParticipation(false, null, programmingExercise, uriService, versionControlService, continuousIntegrationService);

        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        exerciseUtilService.updateExerciseDueDate(programmingExercise.getId(), ZonedDateTime.now().minusHours(1));
        StudentParticipation practiceParticipation = participationService.startPracticeMode(programmingExercise, participant, Optional.empty(), false);

        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        exerciseUtilService.updateExerciseDueDate(programmingExercise.getId(), ZonedDateTime.now().plusHours(1));
        StudentParticipation studentParticipationReceived = participationService.startExercise(programmingExercise, participant, true);

        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        assertThat(studentParticipationReceived.getId()).isNotEqualTo(practiceParticipation.getId());
        assertThat(programmingExercise.getStudentParticipations()).hasSize(2);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(value = ExerciseType.class, names = { "PROGRAMMING", "TEXT" })
    void testStartExercise_newParticipation(ExerciseType exerciseType) {
        Course course;
        if (exerciseType == ExerciseType.PROGRAMMING) {
            course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
            setUpProgrammingExerciseMocks();
        }
        else {
            course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        }
        Exercise exercise = course.getExercises().iterator().next();
        Participant participant = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        StudentParticipation studentParticipationReceived = participationService.startExercise(exercise, participant, true);

        assertThat(studentParticipationReceived.getExercise()).isEqualTo(exercise);
        assertThat(studentParticipationReceived.getStudent()).isPresent();
        assertThat(studentParticipationReceived.getStudent().get()).isEqualTo(participant);
        // Acceptance range, initializationDate is to be set to now()
        assertThat(studentParticipationReceived.getInitializationDate()).isAfterOrEqualTo(ZonedDateTime.now().minusSeconds(10));
        assertThat(studentParticipationReceived.getInitializationDate()).isBeforeOrEqualTo(ZonedDateTime.now().plusSeconds(10));
        assertThat(studentParticipationReceived.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
    }

    private void setUpProgrammingExerciseMocks() {
        doReturn("fake-build-plan-id").when(continuousIntegrationService).copyBuildPlan(any(), anyString(), any(), anyString(), anyString(), anyBoolean());
        doNothing().when(continuousIntegrationService).configureBuildPlan(any(ProgrammingExerciseParticipation.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @ValueSource(booleans = { true, false })
    void testStartPracticeMode(boolean useGradedParticipation) throws URISyntaxException {
        exerciseUtilService.updateExerciseDueDate(programmingExercise.getId(), ZonedDateTime.now().minusMinutes(2));
        Participant participant = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Result gradedResult = participationUtilService.addProgrammingParticipationWithResultForExercise(programmingExercise, TEST_PREFIX + "student1");

        participationUtilService.mockCreationOfExerciseParticipation(useGradedParticipation, gradedResult, programmingExercise, uriService, versionControlService,
                continuousIntegrationService);

        StudentParticipation studentParticipationReceived = participationService.startPracticeMode(programmingExercise, participant,
                Optional.of((StudentParticipation) gradedResult.getSubmission().getParticipation()), useGradedParticipation);

        assertThat(studentParticipationReceived.isPracticeMode()).isTrue();
        assertThat(studentParticipationReceived.getExercise()).isEqualTo(programmingExercise);
        assertThat(studentParticipationReceived.getStudent()).isPresent();
        assertThat(studentParticipationReceived.getStudent().get()).isEqualTo(participant);
        // Acceptance range, initializationDate is to be set to now()
        assertThat(studentParticipationReceived.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDates_returnsOnlyTheParticipationsWhoseDueDateActuallyChanged() {
        ZonedDateTime exerciseDueDate = FIXED_EXERCISE_DUE_DATE;
        programmingExercise.setDueDate(exerciseDueDate);
        programmingExerciseRepository.save(programmingExercise);

        var unchanged = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var moved = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");

        // The first keeps the due date it already has, the second is pushed back by a day.
        var unchangedUpdate = new StudentParticipation();
        unchangedUpdate.setId(unchanged.getId());
        unchangedUpdate.setIndividualDueDate(unchanged.getIndividualDueDate());
        var movedUpdate = new StudentParticipation();
        movedUpdate.setId(moved.getId());
        ZonedDateTime laterDueDate = exerciseDueDate.plusDays(1);
        movedUpdate.setIndividualDueDate(laterDueDate);

        List<StudentParticipation> changed = participationService.updateIndividualDueDates(programmingExercise, List.of(unchangedUpdate, movedUpdate));

        assertThat(changed).as("only the participation whose due date changed is returned").hasSize(1);
        assertThat(changed.getFirst().getId()).isEqualTo(moved.getId());
        assertThat(changed.getFirst().getIndividualDueDate()).as("the new individual due date is applied").isNotNull();
        assertThat(changed.getFirst().getIndividualDueDate().toInstant()).isEqualTo(laterDueDate.toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDates_clearsDatesThatWouldFallBeforeTheExerciseDueDate() {
        ZonedDateTime exerciseDueDate = FIXED_EXERCISE_DUE_DATE;
        programmingExercise.setDueDate(exerciseDueDate);
        programmingExerciseRepository.save(programmingExercise);

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participation.setIndividualDueDate(exerciseDueDate.plusDays(3));
        studentParticipationRepository.save(participation);

        // An individual due date before the exercise due date is not allowed and has to be cleared instead of stored.
        var tooEarly = new StudentParticipation();
        tooEarly.setId(participation.getId());
        tooEarly.setIndividualDueDate(exerciseDueDate.minusDays(1));

        List<StudentParticipation> changed = participationService.updateIndividualDueDates(programmingExercise, List.of(tooEarly));

        assertThat(changed).as("clearing the individual due date is a change").hasSize(1);
        assertThat(changed.getFirst().getIndividualDueDate()).as("an individual due date before the exercise due date is removed").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDates_clearsEveryDateWhenTheExerciseHasNoDueDate() {
        programmingExercise.setDueDate(null);
        programmingExerciseRepository.save(programmingExercise);

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participation.setIndividualDueDate(FIXED_EXERCISE_DUE_DATE.plusDays(5));
        studentParticipationRepository.save(participation);

        var update = new StudentParticipation();
        update.setId(participation.getId());
        update.setIndividualDueDate(FIXED_EXERCISE_DUE_DATE.plusDays(7));

        List<StudentParticipation> changed = participationService.updateIndividualDueDates(programmingExercise, List.of(update));

        assertThat(changed).as("an exercise without a due date cannot carry individual due dates").hasSize(1);
        assertThat(changed.getFirst().getIndividualDueDate()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDates_ignoresParticipationsThatDoNotExist() {
        programmingExercise.setDueDate(FIXED_EXERCISE_DUE_DATE);
        programmingExerciseRepository.save(programmingExercise);

        var unknown = new StudentParticipation();
        unknown.setId(Long.MAX_VALUE);
        unknown.setIndividualDueDate(FIXED_EXERCISE_DUE_DATE.plusDays(3));

        List<StudentParticipation> changed = participationService.updateIndividualDueDates(programmingExercise, List.of(unknown));

        assertThat(changed).as("an unknown participation id is skipped rather than failing the update").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getParticipationNamesForExport_forIndividualExercise_returnsStudentNameAndLogin() {
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        User student = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");

        var names = participationService.getParticipationNamesForExport(programmingExercise);

        assertThat(names).as("one entry per participation").hasSize(1);
        assertThat(names.getFirst().participantName()).as("the student name is exported").isEqualTo(student.getName());
        assertThat(names.getFirst().participantIdentifier()).as("the student login identifies the participation").isEqualTo(student.getLogin());
        assertThat(names.getFirst().teamStudentNames()).as("an individual participation has no team members").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void findParticipationsForExercise_describesAnIndividualParticipationWithItsStudentAndSubmissionCount() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participationUtilService.addSubmission(participation, new ProgrammingSubmission());
        participationUtilService.addSubmission(participation, new ProgrammingSubmission());
        User student = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");

        var page = participationService.findParticipationsForExercise(programmingExercise, new ParticipationSearchDTO(0, 20, SortingOrder.ASCENDING, "id", "", "ALL"));

        assertThat(page.getContent()).as("the participation is listed").hasSize(1);
        var dto = page.getContent().getFirst();
        assertThat(dto.participationId()).isEqualTo(participation.getId());
        assertThat(dto.participantName()).as("an individual participation is described by its student").isEqualTo(student.getName());
        assertThat(dto.participantIdentifier()).isEqualTo(student.getLogin());
        assertThat(dto.studentId()).isEqualTo(student.getId());
        assertThat(dto.studentLogin()).isEqualTo(student.getLogin());
        assertThat(dto.submissionCount()).as("both submissions are counted").isEqualTo(2);
        assertThat(dto.teamId()).as("an individual participation has no team").isNull();
        assertThat(dto.teamStudents()).isNull();
        assertThat(dto.repositoryUri()).as("the repository of the participation is reported").isNotBlank();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void findParticipationScoresForExercise_reportsTheScoreOfTheLatestResult() {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var submission = participationUtilService.addSubmission(participation, new ProgrammingSubmission());
        // A submission can be assessed more than once, for instance after a complaint. The scores view has to show what counts now, not what counted first.
        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, FIXED_EXERCISE_DUE_DATE.minusMinutes(10), submission, false, true, 40.0);
        Result latestResult = participationUtilService.addResultToSubmission(AssessmentType.SEMI_AUTOMATIC, FIXED_EXERCISE_DUE_DATE.minusMinutes(1), submission, true, true, 85.0);
        User student = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");

        var page = participationService.findParticipationScoresForExercise(programmingExercise,
                new ParticipationScoreSearchDTO(0, 20, SortingOrder.ASCENDING, "id", "", "ALL", null, null));

        assertThat(page.getContent()).as("the participation is listed").hasSize(1);
        var dto = page.getContent().getFirst();
        assertThat(dto.participationId()).isEqualTo(participation.getId());
        assertThat(dto.participantName()).as("the score row names the student").isEqualTo(student.getName());
        assertThat(dto.submissionCount()).as("the submission is counted").isEqualTo(1);
        assertThat(dto.score()).as("the score of the newest result is reported, not the one it replaced").isEqualTo(85.0);
        assertThat(dto.resultId()).as("the reported score belongs to the newest result").isEqualTo(latestResult.getId());
        assertThat(dto.successful()).as("the newest result decides whether the participation passed").isTrue();
        assertThat(dto.assessmentType()).as("the assessment type of the newest result is reported").isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(dto.submissionId()).isEqualTo(submission.getId());
    }

    /**
     * Every method that describes a participation has a branch of its own for team exercises, and none of them is
     * reached by an individual participation: a team is named by its short name rather than a login, and its members
     * have to be loaded and reported alongside it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void teamParticipation_isDescribedByItsTeamAndItsMembers() {
        ProgrammingExercise teamExercise = makeExerciseATeamExercise();
        Team team = teamUtilService.createTeam(
                Set.of(userRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1"), userRepository.getUserByLoginElseThrow(TEST_PREFIX + "student2")),
                userRepository.getUserByLoginElseThrow(TEST_PREFIX + "instructor1"), teamExercise, "team1");
        var participation = participationUtilService.addTeamParticipationForExercise(teamExercise, team.getId());
        participationUtilService.addSubmission(participation, new ProgrammingSubmission());

        var managementPage = participationService.findParticipationsForExercise(teamExercise, new ParticipationSearchDTO(0, 20, SortingOrder.ASCENDING, "id", "", "ALL"));

        assertThat(managementPage.getContent()).as("the team participation is listed").hasSize(1);
        var managementDto = managementPage.getContent().getFirst();
        assertThat(managementDto.participantName()).as("a team participation is named after the team").isEqualTo(team.getName());
        assertThat(managementDto.participantIdentifier()).as("the team short name identifies the participation").isEqualTo(team.getShortName());
        assertThat(managementDto.teamId()).isEqualTo(team.getId());
        assertThat(managementDto.teamStudents()).as("the members of the team are reported with name and login").extracting(UserNameAndLoginDTO::login)
                .containsExactlyInAnyOrder(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
        assertThat(managementDto.studentId()).as("a team participation has no single student").isNull();
        assertThat(managementDto.studentLogin()).isNull();

        var scorePage = participationService.findParticipationScoresForExercise(teamExercise,
                new ParticipationScoreSearchDTO(0, 20, SortingOrder.ASCENDING, "id", "", "ALL", null, null));
        assertThat(scorePage.getContent()).hasSize(1);
        assertThat(scorePage.getContent().getFirst().participantName()).as("the score row names the team").isEqualTo(team.getName());

        var exportNames = participationService.getParticipationNamesForExport(teamExercise);
        assertThat(exportNames).hasSize(1);
        assertThat(exportNames.getFirst().participantIdentifier()).as("the export identifies the participation by the team short name").isEqualTo(team.getShortName());
        assertThat(exportNames.getFirst().teamStudentNames()).as("the export names the members of the team")
                .contains(userRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1").getName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void initializeTeamParticipations_loadsTheMembersOfEveryTeamInOneGo() {
        ProgrammingExercise teamExercise = makeExerciseATeamExercise();
        Team team = teamUtilService.createTeam(Set.of(userRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1")),
                userRepository.getUserByLoginElseThrow(TEST_PREFIX + "instructor1"), teamExercise, "team1");
        participationUtilService.addTeamParticipationForExercise(teamExercise, team.getId());
        // Read the participation without its team students, which is the state the callers of this method are in.
        List<StudentParticipation> participations = new ArrayList<>(studentParticipationRepository.findByExerciseId(teamExercise.getId()));

        participationService.initializeTeamParticipations(participations);

        assertThat(participations).hasSize(1);
        assertThat(((Team) participations.getFirst().getParticipant()).getStudents()).as("the members of the team are loaded").extracting(User::getLogin)
                .containsExactly(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void initializeTeamParticipations_withoutATeamParticipation_queriesNothing() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        // The method exists to load the students of many teams in one query. For a list without a single team there is nothing to load, and it must not go to the database
        // at all - a query per participation here is what it was written to avoid.
        assertThatDb(() -> {
            participationService.initializeTeamParticipations(List.of(participation));
            return participation;
        }).hasBeenCalledTimes(0);

        assertThat(participation.getParticipant()).as("an individual participation is left untouched").isInstanceOf(User.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void findOneByExerciseAndParticipant_findsTheGradedAndThePracticeParticipationSeparately() {
        var gradedParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var practiceParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        practiceParticipation.setPracticeMode(true);
        studentParticipationRepository.save(practiceParticipation);
        User gradedStudent = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
        User practiceStudent = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "student2");

        assertThat(participationService.findOneByExerciseAndStudentLoginAnyState(programmingExercise, TEST_PREFIX + "student1")).as("the participation is found by login")
                .map(StudentParticipation::getId).contains(gradedParticipation.getId());
        assertThat(participationService.findOneGradedByExerciseAndParticipant(programmingExercise, gradedStudent)).as("the graded participation is found")
                .map(StudentParticipation::getId).contains(gradedParticipation.getId());
        assertThat(participationService.findOneGradedByExerciseAndParticipant(programmingExercise, practiceStudent)).as("a practice participation is not a graded one").isEmpty();
        assertThat(participationService.findOnePracticeByExerciseAndParticipant(programmingExercise, practiceStudent)).as("the practice participation is found")
                .map(StudentParticipation::getId).contains(practiceParticipation.getId());
        assertThat(participationService.findOnePracticeByExerciseAndParticipant(programmingExercise, gradedStudent)).as("a graded participation is not a practice one").isEmpty();
        assertThat(participationService.findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyState(programmingExercise, TEST_PREFIX + "student1"))
                .as("the participation is found with its submissions").map(StudentParticipation::getId).contains(gradedParticipation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void findOneByExerciseAndStudentLoginAnyStateWithEagerResultsElseThrow_withoutAParticipation_saysWhichStudentAndExercise() {
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> participationService.findOneByExerciseAndStudentLoginAnyStateWithEagerResultsElseThrow(programmingExercise, TEST_PREFIX + "student3"))
                .withMessageContaining(String.valueOf(programmingExercise.getId())).withMessageContaining(TEST_PREFIX + "student3");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void findExerciseParticipationWithLatestSubmissionAndResultElseThrow_withoutAParticipation_throws() {
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(PARTICIPATION_ID_THAT_DOES_NOT_EXIST));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDatesFromDTOs_appliesTheDatesOfTheGivenParticipations() {
        exerciseUtilService.updateExerciseDueDate(programmingExercise.getId(), FIXED_EXERCISE_DUE_DATE);
        Exercise exercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        ZonedDateTime extendedDueDate = FIXED_EXERCISE_DUE_DATE.plusDays(2);

        List<StudentParticipation> changed = participationService.updateIndividualDueDatesFromDTOs(exercise,
                List.of(new ParticipationDueDateUpdateDTO(participation.getId(), exercise.getId(), extendedDueDate),
                        new ParticipationDueDateUpdateDTO(PARTICIPATION_ID_THAT_DOES_NOT_EXIST, exercise.getId(), extendedDueDate)));

        assertThat(changed).as("only the participation whose due date changed is reported, an unknown id is skipped").hasSize(1);
        assertThat(changed.getFirst().getId()).isEqualTo(participation.getId());
        assertThat(changed.getFirst().getIndividualDueDate()).as("the extension is applied").isEqualTo(extendedDueDate);
    }

    /** Turns the programming exercise of this test into a team exercise, which is a property of the exercise rather than of the participation. */
    private ProgrammingExercise makeExerciseATeamExercise() {
        programmingExercise.setMode(ExerciseMode.TEAM);
        return programmingExerciseRepository.save(programmingExercise);
    }
}
