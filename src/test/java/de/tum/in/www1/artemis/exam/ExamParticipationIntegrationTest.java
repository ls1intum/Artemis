package de.tum.in.www1.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withPrecision;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.assessment.GradingScaleUtilService;
import de.tum.in.www1.artemis.bonus.BonusFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.BonusStrategy;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GradeType;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseTestService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.BonusRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExamUserRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.repository.ParticipantScoreRepository;
import de.tum.in.www1.artemis.repository.ParticipationTestRepository;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.service.QuizSubmissionService;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.exam.StudentExamService;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreScheduleService;
import de.tum.in.www1.artemis.team.TeamUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.ExamPrepareExercisesTestUtil;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.web.rest.dto.DueDateStat;
import de.tum.in.www1.artemis.web.rest.dto.ExamChecklistDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExamScoresDTO;
import de.tum.in.www1.artemis.web.rest.dto.StatsForDashboardDTO;

class ExamParticipationIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "examparticipationtest";

    private static final Logger log = LoggerFactory.getLogger(ExamParticipationIntegrationTest.class);

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private QuizSubmissionRepository quizSubmissionRepository;

    @Autowired
    private QuizSubmissionService quizSubmissionService;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private ExamService examService;

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ParticipationTestRepository participationTestRepository;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private BonusRepository bonusRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private GradingScaleUtilService gradingScaleUtilService;

    private Course course1;

    private static final int NUMBER_OF_STUDENTS = 3;

    private static final int NUMBER_OF_TUTORS = 2;

    private final List<LocalRepository> studentRepos = new ArrayList<>();

    private User student1;

    private User instructor;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);

        course1 = courseUtilService.addEmptyCourse();
        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        gitlabRequestMockProvider.enableMockingOfRequests();

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
        participantScoreScheduleService.activate();
    }

    @AfterEach
    void tearDown() throws Exception {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        if (programmingExerciseTestService.exerciseRepo != null) {
            programmingExerciseTestService.tearDown();
        }

        for (var repo : studentRepos) {
            repo.resetLocalRepo();
        }

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;
        participantScoreScheduleService.shutdown();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemovingAllStudents_AfterParticipatingInExam() throws Exception {
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 3);

        // Generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(3);
        assertThat(exam.getExamUsers()).hasSize(3);

        int numberOfGeneratedParticipations = ExamPrepareExercisesTestUtil.prepareExerciseStart(request, exam, course1);
        assertThat(numberOfGeneratedParticipations).isEqualTo(12);

        verify(gitService, times(examUtilService.getNumberOfProgrammingExercises(exam.getId()))).combineAllCommitsOfRepositoryIntoOne(any());
        // Fetch student exams
        List<StudentExam> studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).hasSize(3);
        List<StudentParticipation> participationList = new ArrayList<>();
        Exercise[] exercises = examRepository.findAllExercisesByExamId(exam.getId()).toArray(Exercise[]::new);
        for (Exercise value : exercises) {
            participationList.addAll(studentParticipationRepository.findByExerciseId(value.getId()));
        }
        assertThat(participationList).hasSize(12);

        // TODO there should be some participation but no submissions unfortunately
        // remove all students
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students", HttpStatus.OK);

        // Get the exam with all registered users
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withStudents", "true");
        Exam storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(storedExam.getExamUsers()).isEmpty();

        // Fetch student exams
        studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).isEmpty();

        // Fetch participations
        exercises = examRepository.findAllExercisesByExamId(exam.getId()).toArray(Exercise[]::new);
        participationList = new ArrayList<>();
        for (Exercise exercise : exercises) {
            participationList.addAll(studentParticipationRepository.findByExerciseId(exercise.getId()));
        }
        assertThat(participationList).hasSize(12);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemovingAllStudentsAndParticipations() throws Exception {
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 3);

        // Generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(3);
        assertThat(exam.getExamUsers()).hasSize(3);

        int numberOfGeneratedParticipations = ExamPrepareExercisesTestUtil.prepareExerciseStart(request, exam, course1);
        verify(gitService, times(examUtilService.getNumberOfProgrammingExercises(exam.getId()))).combineAllCommitsOfRepositoryIntoOne(any());
        assertThat(numberOfGeneratedParticipations).isEqualTo(12);
        // Fetch student exams
        List<StudentExam> studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).hasSize(3);
        List<StudentParticipation> participationList = new ArrayList<>();
        Exercise[] exercises = examRepository.findAllExercisesByExamId(exam.getId()).toArray(Exercise[]::new);
        for (Exercise value : exercises) {
            participationList.addAll(studentParticipationRepository.findByExerciseId(value.getId()));
        }
        assertThat(participationList).hasSize(12);

        // TODO there should be some participation but no submissions unfortunately
        // remove all students
        var paramsParticipations = new LinkedMultiValueMap<String, String>();
        paramsParticipations.add("withParticipationsAndSubmission", "true");
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students", HttpStatus.OK, paramsParticipations);

        // Get the exam with all registered users
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withStudents", "true");
        Exam storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(storedExam.getExamUsers()).isEmpty();

        // Fetch student exams
        studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).isEmpty();

        // Fetch participations
        exercises = examRepository.findAllExercisesByExamId(exam.getId()).toArray(Exercise[]::new);
        participationList = new ArrayList<>();
        for (Exercise exercise : exercises) {
            participationList.addAll(studentParticipationRepository.findByExerciseId(exercise.getId()));
        }
        assertThat(participationList).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteStudent_AfterParticipatingInExam() throws Exception {
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());
        // Create an exam with registered students
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 3);
        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");

        // Remove student1 from the exam
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.OK);

        // Get the exam with all registered users
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withStudents", "true");
        Exam storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);

        // Ensure that student1 was removed from the exam
        var examUser = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student1.getId());
        assertThat(examUser).isEmpty();
        assertThat(storedExam.getExamUsers()).hasSize(2);

        // Create individual student exams
        List<StudentExam> generatedStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(generatedStudentExams).hasSize(storedExam.getExamUsers().size());

        // Start the exam to create participations
        ExamPrepareExercisesTestUtil.prepareExerciseStart(request, exam, course1);

        verify(gitService, times(examUtilService.getNumberOfProgrammingExercises(exam.getId()))).combineAllCommitsOfRepositoryIntoOne(any());
        // Get the student exam of student2
        Optional<StudentExam> optionalStudent1Exam = generatedStudentExams.stream().filter(studentExam -> studentExam.getUser().equals(student2)).findFirst();
        assertThat(optionalStudent1Exam.orElseThrow()).isNotNull();
        var studentExam2 = optionalStudent1Exam.get();

        // explicitly set the user again to prevent issues in the following server call due to the use of SecurityUtils.setAuthorizationObject();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // Remove student2 from the exam
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/" + TEST_PREFIX + "student2", HttpStatus.OK);

        // Get the exam with all registered users
        params = new LinkedMultiValueMap<>();
        params.add("withStudents", "true");
        storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);

        // Ensure that student2 was removed from the exam
        var examUser2 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student2.getId());
        assertThat(examUser2).isEmpty();
        assertThat(storedExam.getExamUsers()).hasSize(1);

        // Ensure that the student exam of student2 was deleted
        List<StudentExam> studentExams = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExams).hasSameSizeAs(storedExam.getExamUsers()).doesNotContain(studentExam2);

        // Ensure that the participations were not deleted
        List<StudentParticipation> participationsStudent2 = studentParticipationRepository
                .findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(student2.getId(), studentExam2.getExercises());
        assertThat(participationsStudent2).hasSize(studentExam2.getExercises().size());

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsCleanupOldParticipations() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, NUMBER_OF_STUDENTS);

        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.OK);

        List<Participation> studentParticipations = participationTestRepository.findByExercise_ExerciseGroup_Exam_Id(exam.getId());
        assertThat(studentParticipations).isEmpty();

        // invoke start exercises
        studentExamService.startExercises(exam.getId()).join();

        studentParticipations = participationTestRepository.findByExercise_ExerciseGroup_Exam_Id(exam.getId());
        assertThat(studentParticipations).hasSize(12);

        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.OK);

        studentParticipations = participationTestRepository.findByExercise_ExerciseGroup_Exam_Id(exam.getId());
        assertThat(studentParticipations).isEmpty();

        // invoke start exercises
        studentExamService.startExercises(exam.getId()).join();

        studentParticipations = participationTestRepository.findByExercise_ExerciseGroup_Exam_Id(exam.getId());
        assertThat(studentParticipations).hasSize(12);

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteStudentWithParticipationsAndSubmissions() throws Exception {
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());
        // Create an exam with registered students
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 3);

        // Create individual student exams
        List<StudentExam> generatedStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);

        // Get the student exam of student1
        Optional<StudentExam> optionalStudent1Exam = generatedStudentExams.stream().filter(studentExam -> studentExam.getUser().equals(student1)).findFirst();
        assertThat(optionalStudent1Exam.orElseThrow()).isNotNull();
        var studentExam1 = optionalStudent1Exam.get();

        // Start the exam to create participations
        ExamPrepareExercisesTestUtil.prepareExerciseStart(request, exam, course1);
        verify(gitService, times(examUtilService.getNumberOfProgrammingExercises(exam.getId()))).combineAllCommitsOfRepositoryIntoOne(any());
        List<StudentParticipation> participationsStudent1 = studentParticipationRepository
                .findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(student1.getId(), studentExam1.getExercises());
        assertThat(participationsStudent1).hasSize(studentExam1.getExercises().size());

        // explicitly set the user again to prevent issues in the following server call due to the use of SecurityUtils.setAuthorizationObject();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // Remove student1 from the exam and his participations
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withParticipationsAndSubmission", "true");
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.OK, params);

        // Get the exam with all registered users
        params = new LinkedMultiValueMap<>();
        params.add("withStudents", "true");
        Exam storedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);

        // Ensure that student1 was removed from the exam
        var examUser1 = examUserRepository.findByExamIdAndUserId(storedExam.getId(), student1.getId());
        assertThat(examUser1).isEmpty();
        assertThat(storedExam.getExamUsers()).hasSize(2);

        // Ensure that the student exam of student1 was deleted
        List<StudentExam> studentExams = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExams).hasSameSizeAs(storedExam.getExamUsers()).doesNotContain(studentExam1);

        // Ensure that the participations of student1 were deleted
        participationsStudent1 = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(student1.getId(),
                studentExam1.getExercises());
        assertThat(participationsStudent1).isEmpty();

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(ints = { 0, 1, 2 })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStatsForExamAssessmentDashboard(int numberOfCorrectionRounds) throws Exception {
        log.debug("testGetStatsForExamAssessmentDashboard: step 1 done");
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());

        User examTutor1 = userRepo.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
        User examTutor2 = userRepo.findOneByLogin(TEST_PREFIX + "tutor2").orElseThrow();

        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(5);
        var examEndDate = ZonedDateTime.now().plusMinutes(20);
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam.setNumberOfCorrectionRoundsInExam(numberOfCorrectionRounds);
        exam = examRepository.save(exam);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false);

        log.debug("testGetStatsForExamAssessmentDashboard: step 2 done");

        var stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfSubmissions()).isInstanceOf(DueDateStat.class);
        assertThat(stats.getTutorLeaderboardEntries()).isInstanceOf(List.class);
        if (numberOfCorrectionRounds != 0) {
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()).isInstanceOf(DueDateStat[].class);
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isZero();
        }
        else {
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()).isNull();
        }
        assertThat(stats.getNumberOfAssessmentLocks()).isZero();
        assertThat(stats.getNumberOfSubmissions().inTime()).isZero();
        if (numberOfCorrectionRounds > 0) {
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isZero();
        }
        else {
            assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()).isNull();
        }
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isZero();

        if (numberOfCorrectionRounds == 0) {
            // We do not need any more assertions, as numberOfCorrectionRounds is only 0 for test exams (no manual assessment)
            return;
        }

        var lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).isEmpty();

        log.debug("testGetStatsForExamAssessmentDashboard: step 3 done");

        // register users. Instructors are ignored from scores as they are exclusive for test run exercises
        Set<User> registeredStudents = getRegisteredStudentsForExam();
        for (var student : registeredStudents) {
            var registeredExamUser = new ExamUser();
            registeredExamUser.setExam(exam);
            registeredExamUser.setUser(student);
            exam.addExamUser(registeredExamUser);
        }
        exam.setNumberOfExercisesInExam(exam.getExerciseGroups().size());
        exam.setRandomizeExerciseOrder(false);
        exam = examRepository.save(exam);
        exam = examRepository.findWithExamUsersAndExerciseGroupsAndExercisesById(exam.getId()).orElseThrow();

        log.debug("testGetStatsForExamAssessmentDashboard: step 4 done");

        // generate individual student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);
        int noGeneratedParticipations = ExamPrepareExercisesTestUtil.prepareExerciseStart(request, exam, course);
        verify(gitService, times(examUtilService.getNumberOfProgrammingExercises(exam.getId()))).combineAllCommitsOfRepositoryIntoOne(any());
        // set start and submitted date as results are created below
        studentExams.forEach(studentExam -> {
            studentExam.setStartedAndStartDate(ZonedDateTime.now().minusMinutes(2));
            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now().minusMinutes(1));
        });
        studentExamRepository.saveAll(studentExams);

        log.debug("testGetStatsForExamAssessmentDashboard: step 5 done");

        // Fetch the created participations and assign them to the exercises
        int participationCounter = 0;
        List<Exercise> exercisesInExam = exam.getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream).toList();
        for (var exercise : exercisesInExam) {
            List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdAndTestRunWithEagerLegalSubmissionsResult(exercise.getId(), false);
            exercise.setStudentParticipations(new HashSet<>(participations));
            participationCounter += exercise.getStudentParticipations().size();
        }
        assertThat(noGeneratedParticipations).isEqualTo(participationCounter);

        log.debug("testGetStatsForExamAssessmentDashboard: step 6 done");

        // Assign submissions to the participations
        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                assertThat(participation.getSubmissions()).hasSize(1);
                Submission submission = participation.getSubmissions().iterator().next();
                submission.submitted(true);
                submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(6));
                submissionRepository.save(submission);
            }
        }

        log.debug("testGetStatsForExamAssessmentDashboard: step 7 done");

        // check the stats again - check the count of submitted submissions
        stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfAssessmentLocks()).isZero();
        // 85 = (17 users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(studentExams.size() * 5L);
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isZero();
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isZero();

        // Score used for all exercise results
        Double resultScore = 75.0;

        log.debug("testGetStatsForExamAssessmentDashboard: step 7 done");

        // Lock all submissions
        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                Submission submission;
                assertThat(participation.getSubmissions()).hasSize(1);
                submission = participation.getSubmissions().iterator().next();
                // Create results
                var result = new Result().score(resultScore);
                if (exercise instanceof QuizExercise) {
                    result.completionDate(ZonedDateTime.now().minusMinutes(4));
                    result.setRated(true);
                }
                result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
                result.setParticipation(participation);
                result.setAssessor(examTutor1);
                result = resultRepository.save(result);
                result.setSubmission(submission);
                submission.addResult(result);
                submissionRepository.save(submission);
            }
        }
        log.debug("testGetStatsForExamAssessmentDashboard: step 8 done");

        // check the stats again
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);

        assertThat(stats.getNumberOfAssessmentLocks()).isEqualTo(studentExams.size() * 5L);
        // (studentExams.size() users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(studentExams.size() * 5L);
        // the studentExams.size() quiz submissions are already assessed
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(studentExams.size());
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isEqualTo(studentExams.size() * 5L);

        log.debug("testGetStatsForExamAssessmentDashboard: step 9 done");

        // test the query needed for assessment information
        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        exam.getExerciseGroups().forEach(group -> {
            var locks = group.getExercises().stream().map(
                    exercise -> resultRepository.countNumberOfLockedAssessmentsByOtherTutorsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRounds, examTutor2)[0]
                            .inTime())
                    .reduce(Long::sum).orElseThrow();
            if (group.getExercises().stream().anyMatch(exercise -> !(exercise instanceof QuizExercise))) {
                assertThat(locks).isEqualTo(studentExams.size());
            }
        });

        log.debug("testGetStatsForExamAssessmentDashboard: step 10 done");

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).hasSize(studentExams.size() * 5);

        log.debug("testGetStatsForExamAssessmentDashboard: step 11 done");

        // Finish assessment of all submissions
        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                Submission submission;
                assertThat(participation.getSubmissions()).hasSize(1);
                submission = participation.getSubmissions().iterator().next();
                var result = submission.getLatestResult().completionDate(ZonedDateTime.now().minusMinutes(5));
                result.setRated(true);
                resultRepository.save(result);
            }
        }

        log.debug("testGetStatsForExamAssessmentDashboard: step 12 done");

        // check the stats again
        stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfAssessmentLocks()).isZero();
        // 75 = (15 users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(studentExams.size() * 5L);
        // 75 + the 19 quiz submissions
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(studentExams.size() * 5L + studentExams.size());
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isZero();

        log.debug("testGetStatsForExamAssessmentDashboard: step 13 done");

        lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).isEmpty();
        if (numberOfCorrectionRounds == 2) {
            lockAndAssessForSecondCorrection(exam, course, studentExams, exercisesInExam, numberOfCorrectionRounds);
        }

        log.debug("testGetStatsForExamAssessmentDashboard: step 14 done");
    }

    private void lockAndAssessForSecondCorrection(Exam exam, Course course, List<StudentExam> studentExams, List<Exercise> exercisesInExam, int numberOfCorrectionRounds)
            throws Exception {
        // Lock all submissions
        User examInstructor = userRepo.findOneByLogin(TEST_PREFIX + "instructor1").orElseThrow();
        User examTutor2 = userRepo.findOneByLogin(TEST_PREFIX + "tutor2").orElseThrow();

        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                assertThat(participation.getSubmissions()).hasSize(1);
                Submission submission = participation.getSubmissions().iterator().next();
                // Create results
                var result = new Result().score(50D).rated(true);
                if (exercise instanceof QuizExercise) {
                    result.completionDate(ZonedDateTime.now().minusMinutes(3));
                }
                result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
                result.setParticipation(participation);
                result.setAssessor(examInstructor);
                result = resultRepository.save(result);
                result.setSubmission(submission);
                submission.addResult(result);
                submissionRepository.save(submission);
            }
        }
        // check the stats again
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfAssessmentLocks()).isEqualTo(studentExams.size() * 5L);
        // 75 = (15 users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(studentExams.size() * 5L);
        // the 15 quiz submissions are already assessed - and all are assessed in the first correctionRound
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(studentExams.size() * 6L);
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[1].inTime()).isEqualTo(studentExams.size());
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isEqualTo(studentExams.size() * 5L);

        // test the query needed for assessment information
        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        exam.getExerciseGroups().forEach(group -> {
            var locksRound1 = group.getExercises().stream().map(
                    exercise -> resultRepository.countNumberOfLockedAssessmentsByOtherTutorsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRounds, examTutor2)[0]
                            .inTime())
                    .reduce(Long::sum).orElseThrow();
            if (group.getExercises().stream().anyMatch(exercise -> !(exercise instanceof QuizExercise))) {
                assertThat(locksRound1).isZero();
            }

            var locksRound2 = group.getExercises().stream().map(
                    exercise -> resultRepository.countNumberOfLockedAssessmentsByOtherTutorsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRounds, examTutor2)[1]
                            .inTime())
                    .reduce(Long::sum).orElseThrow();
            if (group.getExercises().stream().anyMatch(exercise -> !(exercise instanceof QuizExercise))) {
                assertThat(locksRound2).isEqualTo(studentExams.size());
            }
        });

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).hasSize(studentExams.size() * 5);

        // Finish assessment of all submissions
        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                Submission submission;
                assertThat(participation.getSubmissions()).hasSize(1);
                submission = participation.getSubmissions().iterator().next();
                var result = submission.getLatestResult().completionDate(ZonedDateTime.now().minusMinutes(5));
                result.setRated(true);
                resultRepository.save(result);
            }
        }

        // check the stats again
        stats = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/stats-for-exam-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
        assertThat(stats.getNumberOfAssessmentLocks()).isZero();
        // 75 = (15 users * 5 exercises); quiz submissions are not counted
        assertThat(stats.getNumberOfSubmissions().inTime()).isEqualTo(studentExams.size() * 5L);
        // 75 + the 15 quiz submissions
        assertThat(stats.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(studentExams.size() * 6L);
        assertThat(stats.getNumberOfComplaints()).isZero();
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isZero();

        lockedSubmissions = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/lockedSubmissions", HttpStatus.OK, List.class);
        assertThat(lockedSubmissions).isEmpty();
    }

    // TODO enable again (Issue - https://github.com/ls1intum/Artemis/issues/8297)
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @CsvSource({ "false, false", "true, false", "false, true", "true, true" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamScore(boolean withCourseBonus, boolean withSecondCorrectionAndStarted) throws Exception {
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        gitlabRequestMockProvider.enableMockingOfRequests();
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);

        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());

        var visibleDate = ZonedDateTime.now().minusMinutes(5);
        var startDate = ZonedDateTime.now().plusMinutes(5);
        var endDate = ZonedDateTime.now().plusMinutes(20);

        // register users. Instructors are ignored from scores as they are exclusive for test run exercises
        Set<User> registeredStudents = getRegisteredStudentsForExam();

        var studentExams = programmingExerciseTestService.prepareStudentExamsForConduction(TEST_PREFIX, visibleDate, startDate, endDate, registeredStudents, studentRepos);
        Exam exam = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(studentExams.get(0).getExam().getId());
        Course course = exam.getCourse();

        Integer noGeneratedParticipations = registeredStudents.size() * exam.getExerciseGroups().size();

        verify(gitService, times(examUtilService.getNumberOfProgrammingExercises(exam.getId()))).combineAllCommitsOfRepositoryIntoOne(any());
        // explicitly set the user again to prevent issues in the following server call due to the use of SecurityUtils.setAuthorizationObject();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // instructor exam checklist checks
        ExamChecklistDTO examChecklistDTO = examService.getStatsForChecklist(exam, true);
        assertThat(examChecklistDTO).isNotNull();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isEqualTo(exam.getExamUsers().size());
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isTrue();
        assertThat(examChecklistDTO.getNumberOfTotalParticipationsForAssessment()).isZero();

        // check that an adapted version is computed for tutors
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        examChecklistDTO = examService.getStatsForChecklist(exam, false);
        assertThat(examChecklistDTO).isNotNull();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isNull();
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isFalse();
        assertThat(examChecklistDTO.getNumberOfTotalParticipationsForAssessment()).isZero();

        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // set start and submitted date as results are created below
        var savedStudentExams = studentExamRepository.findByExamId(exam.getId());
        savedStudentExams.forEach(studentExam -> {
            studentExam.setStartedAndStartDate(ZonedDateTime.now().minusMinutes(2));
            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now().minusMinutes(1));
        });
        studentExamRepository.saveAll(savedStudentExams);

        // Fetch the created participations and assign them to the exercises
        int participationCounter = 0;
        List<Exercise> exercisesInExam = exam.getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream).toList();
        for (var exercise : exercisesInExam) {
            List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdAndTestRunWithEagerLegalSubmissionsResult(exercise.getId(), false);
            exercise.setStudentParticipations(new HashSet<>(participations));
            participationCounter += exercise.getStudentParticipations().size();
        }
        assertThat(noGeneratedParticipations).isEqualTo(participationCounter);

        if (withSecondCorrectionAndStarted) {
            exercisesInExam.forEach(exercise -> exercise.setSecondCorrectionEnabled(true));
            exerciseRepo.saveAll(exercisesInExam);
        }

        // Scores used for all exercise results
        double correctionResultScore = 60D;
        double resultScore = 75D;

        // Assign results to participations and submissions
        for (var exercise : exercisesInExam) {
            for (var participation : exercise.getStudentParticipations()) {
                Submission submission;
                // Programming exercises don't have a submission yet
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(participation.getSubmissions()).isEmpty();
                    submission = new ProgrammingSubmission();
                    submission.setParticipation(participation);
                    submission = submissionRepository.save(submission);
                }
                else {
                    // There should only be one submission for text, quiz, modeling and file upload
                    assertThat(participation.getSubmissions()).hasSize(1);
                    submission = participation.getSubmissions().iterator().next();
                }

                // make sure to create submitted answers
                if (exercise instanceof QuizExercise quizExercise) {
                    var quizQuestions = quizExerciseRepository.findByIdWithQuestionsElseThrow(exercise.getId()).getQuizQuestions();
                    for (var quizQuestion : quizQuestions) {
                        var submittedAnswer = QuizExerciseFactory.generateSubmittedAnswerFor(quizQuestion, true);
                        var quizSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(submission.getId());
                        quizSubmission.addSubmittedAnswers(submittedAnswer);
                        quizSubmissionService.saveSubmissionForExamMode(quizExercise, quizSubmission, participation.getStudent().orElseThrow());
                    }
                }

                // Create results
                if (withSecondCorrectionAndStarted) {
                    var firstResult = new Result().score(correctionResultScore).rated(true).completionDate(ZonedDateTime.now().minusMinutes(5));
                    firstResult.setParticipation(participation);
                    firstResult.setAssessor(instructor);
                    firstResult = resultRepository.save(firstResult);
                    firstResult.setSubmission(submission);
                    submission.addResult(firstResult);
                }

                var finalResult = new Result().score(resultScore).rated(true).completionDate(ZonedDateTime.now().minusMinutes(5));
                finalResult.setParticipation(participation);
                finalResult.setAssessor(instructor);
                finalResult = resultRepository.save(finalResult);
                finalResult.setSubmission(submission);
                submission.addResult(finalResult);

                submission.submitted(true);
                submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(6));
                submissionRepository.save(submission);
            }
        }
        // explicitly set the user again to prevent issues in the following server call due to the use of SecurityUtils.setAuthorizationObject();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        final var exerciseWithNoUsers = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        exerciseRepo.save(exerciseWithNoUsers);

        GradingScale gradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 60, 25, 15, 50 },
                Optional.of(new String[] { "5.0", "3.0", "1.0", "1.0" }), true, 1);
        gradingScale.setExam(exam);
        gradingScale = gradingScaleRepository.save(gradingScale);

        waitForParticipantScores();

        if (withCourseBonus) {
            configureCourseAsBonusWithIndividualAndTeamResults(course, gradingScale);
        }

        await().timeout(Duration.ofMinutes(1)).until(() -> {
            for (Exercise exercise : exercisesInExam) {
                if (participantScoreRepository.findAllByExercise(exercise).size() != exercise.getStudentParticipations().size()) {
                    return false;
                }
            }
            return true;
        });

        var examScores = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/scores", HttpStatus.OK, ExamScoresDTO.class);

        // Compare generated results to data in ExamScoresDTO
        // Compare top-level DTO properties
        assertThat(examScores.maxPoints()).isEqualTo(exam.getExamMaxPoints());

        assertThat(examScores.hasSecondCorrectionAndStarted()).isEqualTo(withSecondCorrectionAndStarted);

        // For calculation assume that all exercises within an exerciseGroups have the same max points
        double calculatedAverageScore = 0.0;
        for (var exerciseGroup : exam.getExerciseGroups()) {
            var exercise = exerciseGroup.getExercises().stream().findAny().orElseThrow();
            if (exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)) {
                continue;
            }
            calculatedAverageScore += Math.round(exercise.getMaxPoints() * resultScore / 100.00 * 10) / 10.0;
        }

        assertThat(examScores.averagePointsAchieved()).isEqualTo(calculatedAverageScore);
        assertThat(examScores.title()).isEqualTo(exam.getTitle());
        assertThat(examScores.examId()).isEqualTo(exam.getId());

        // Ensure that all exerciseGroups of the exam are present in the DTO
        Set<Long> exerciseGroupIdsInDTO = examScores.exerciseGroups().stream().map(ExamScoresDTO.ExerciseGroup::id).collect(Collectors.toSet());
        Set<Long> exerciseGroupIdsInExam = exam.getExerciseGroups().stream().map(ExerciseGroup::getId).collect(Collectors.toSet());
        assertThat(exerciseGroupIdsInExam).isEqualTo(exerciseGroupIdsInDTO);

        // Compare exerciseGroups in DTO to exam exerciseGroups
        // Tolerated absolute difference for floating-point number comparisons
        double epsilon = 0000.1;
        for (var exerciseGroupDTO : examScores.exerciseGroups()) {
            // Find the original exerciseGroup of the exam using the id in ExerciseGroupId
            ExerciseGroup originalExerciseGroup = exam.getExerciseGroups().stream().filter(exerciseGroup -> exerciseGroup.getId().equals(exerciseGroupDTO.id())).findFirst()
                    .orElseThrow();

            // Assume that all exercises in a group have the same max score
            Double groupMaxScoreFromExam = originalExerciseGroup.getExercises().stream().findAny().orElseThrow().getMaxPoints();
            assertThat(exerciseGroupDTO.maxPoints()).isEqualTo(originalExerciseGroup.getExercises().stream().findAny().orElseThrow().getMaxPoints());
            assertThat(groupMaxScoreFromExam).isEqualTo(exerciseGroupDTO.maxPoints(), withPrecision(epsilon));

            // epsilon
            // Compare exercise information
            long noOfExerciseGroupParticipations = 0;
            for (var originalExercise : originalExerciseGroup.getExercises()) {
                // Find the corresponding ExerciseInfo object
                var exerciseDTO = exerciseGroupDTO.containedExercises().stream().filter(exerciseInfo -> exerciseInfo.exerciseId().equals(originalExercise.getId())).findFirst()
                        .orElseThrow();
                // Check the exercise title
                assertThat(originalExercise.getTitle()).isEqualTo(exerciseDTO.title());
                // Check the max points of the exercise
                assertThat(originalExercise.getMaxPoints()).isEqualTo(exerciseDTO.maxPoints());
                // Check the number of exercise participants and update the group participant counter
                var noOfExerciseParticipations = originalExercise.getStudentParticipations().size();
                noOfExerciseGroupParticipations += noOfExerciseParticipations;
                assertThat(Long.valueOf(originalExercise.getStudentParticipations().size())).isEqualTo(exerciseDTO.numberOfParticipants());
            }
            assertThat(noOfExerciseGroupParticipations).isEqualTo(exerciseGroupDTO.numberOfParticipants());
        }

        // Ensure that all registered students have a StudentResult
        Set<Long> studentIdsWithStudentResults = examScores.studentResults().stream().map(ExamScoresDTO.StudentResult::userId).collect(Collectors.toSet());
        Set<User> registeredUsers = exam.getRegisteredUsers();
        Set<Long> registeredUsersIds = registeredUsers.stream().map(User::getId).collect(Collectors.toSet());
        assertThat(studentIdsWithStudentResults).isEqualTo(registeredUsersIds);

        // Compare StudentResult with the generated results
        for (var studentResult : examScores.studentResults()) {
            // Find the original user using the id in StudentResult
            User originalUser = userRepo.findByIdElseThrow(studentResult.userId());
            StudentExam studentExamOfUser = studentExams.stream().filter(studentExam -> studentExam.getUser().equals(originalUser)).findFirst().orElseThrow();

            assertThat(studentResult.name()).isEqualTo(originalUser.getName());
            assertThat(studentResult.email()).isEqualTo(originalUser.getEmail());
            assertThat(studentResult.login()).isEqualTo(originalUser.getLogin());
            assertThat(studentResult.registrationNumber()).isEqualTo(originalUser.getRegistrationNumber());

            // Calculate overall points achieved

            var calculatedOverallPoints = calculateOverallPoints(resultScore, studentExamOfUser);

            assertThat(studentResult.overallPointsAchieved()).isEqualTo(calculatedOverallPoints, withPrecision(epsilon));

            double expectedPointsAchievedInFirstCorrection = withSecondCorrectionAndStarted ? calculateOverallPoints(correctionResultScore, studentExamOfUser) : 0.0;
            assertThat(studentResult.overallPointsAchievedInFirstCorrection()).isEqualTo(expectedPointsAchievedInFirstCorrection, withPrecision(epsilon));

            // Calculate overall score achieved
            var calculatedOverallScore = calculatedOverallPoints / examScores.maxPoints() * 100;
            assertThat(studentResult.overallScoreAchieved()).isEqualTo(calculatedOverallScore, withPrecision(epsilon));

            assertThat(studentResult.overallGrade()).isNotNull();
            assertThat(studentResult.hasPassed()).isNotNull();
            assertThat(studentResult.mostSeverePlagiarismVerdict()).isNull();
            if (withCourseBonus) {
                String studentLogin = studentResult.login();
                assertThat(studentResult.gradeWithBonus().bonusStrategy()).isEqualTo(BonusStrategy.GRADES_CONTINUOUS);
                switch (studentLogin) {
                    case TEST_PREFIX + "student1" -> {
                        assertThat(studentResult.gradeWithBonus().mostSeverePlagiarismVerdict()).isNull();
                        assertThat(studentResult.gradeWithBonus().studentPointsOfBonusSource()).isEqualTo(10.0);
                        assertThat(studentResult.gradeWithBonus().bonusGrade()).isEqualTo("0.0");
                        assertThat(studentResult.gradeWithBonus().finalGrade()).isEqualTo("1.0");
                    }
                    case TEST_PREFIX + "student2" -> {
                        assertThat(studentResult.gradeWithBonus().mostSeverePlagiarismVerdict()).isEqualTo(PlagiarismVerdict.POINT_DEDUCTION);
                        assertThat(studentResult.gradeWithBonus().studentPointsOfBonusSource()).isEqualTo(10.5); // 10.5 = 8 + 5 * 50% plagiarism point deduction.
                        assertThat(studentResult.gradeWithBonus().finalGrade()).isEqualTo("1.0");
                    }
                    case TEST_PREFIX + "student3" -> {
                        assertThat(studentResult.gradeWithBonus().mostSeverePlagiarismVerdict()).isEqualTo(PlagiarismVerdict.PLAGIARISM);
                        assertThat(studentResult.gradeWithBonus().studentPointsOfBonusSource()).isZero();
                        assertThat(studentResult.gradeWithBonus().bonusGrade()).isEqualTo(GradingScale.DEFAULT_PLAGIARISM_GRADE);
                        assertThat(studentResult.gradeWithBonus().finalGrade()).isEqualTo("1.0");
                    }
                    default -> {
                    }
                }
            }
            else {
                assertThat(studentResult.gradeWithBonus()).isNull();
            }

            // Ensure that the exercise ids of the student exam are the same as the exercise ids in the students exercise results
            Set<Long> exerciseIdsOfStudentResult = studentResult.exerciseGroupIdToExerciseResult().values().stream().map(ExamScoresDTO.ExerciseResult::exerciseId)
                    .collect(Collectors.toSet());
            Set<Long> exerciseIdsInStudentExam = studentExamOfUser.getExercises().stream().map(DomainObject::getId).collect(Collectors.toSet());
            assertThat(exerciseIdsOfStudentResult).isEqualTo(exerciseIdsInStudentExam);
            for (Map.Entry<Long, ExamScoresDTO.ExerciseResult> entry : studentResult.exerciseGroupIdToExerciseResult().entrySet()) {
                var exerciseResult = entry.getValue();

                // Find the original exercise using the id in ExerciseResult
                Exercise originalExercise = studentExamOfUser.getExercises().stream().filter(exercise -> exercise.getId().equals(exerciseResult.exerciseId())).findFirst()
                        .orElseThrow();

                // Check that the key is associated with the exerciseGroup which actually contains the exercise in the exerciseResult
                assertThat(originalExercise.getExerciseGroup().getId()).isEqualTo(entry.getKey());

                assertThat(exerciseResult.title()).isEqualTo(originalExercise.getTitle());
                assertThat(exerciseResult.maxScore()).isEqualTo(originalExercise.getMaxPoints());
                assertThat(exerciseResult.achievedScore()).isEqualTo(resultScore);
                if (originalExercise instanceof QuizExercise) {
                    assertThat(exerciseResult.hasNonEmptySubmission()).isTrue();
                }
                else {
                    assertThat(exerciseResult.hasNonEmptySubmission()).isFalse();
                }
                // TODO: create a test where hasNonEmptySubmission() is false for a quiz
                assertThat(exerciseResult.achievedPoints()).isEqualTo(originalExercise.getMaxPoints() * resultScore / 100, withPrecision(epsilon));
            }
        }

        // change back to instructor user
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        var expectedTotalExamAssessmentsFinishedByCorrectionRound = new Long[] { noGeneratedParticipations.longValue(), noGeneratedParticipations.longValue() };
        if (!withSecondCorrectionAndStarted) {
            // The second correction has not started in this case.
            expectedTotalExamAssessmentsFinishedByCorrectionRound[1] = 0L;
        }

        // check if stats are set correctly for the instructor
        examChecklistDTO = examService.getStatsForChecklist(exam, true);
        assertThat(examChecklistDTO).isNotNull();
        var size = examScores.studentResults().size();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isEqualTo(size);
        assertThat(examChecklistDTO.getNumberOfExamsSubmitted()).isEqualTo(size);
        assertThat(examChecklistDTO.getNumberOfExamsStarted()).isEqualTo(size);
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isTrue();
        assertThat(examChecklistDTO.getNumberOfTotalParticipationsForAssessment()).isEqualTo(size * 6L);
        assertThat(examChecklistDTO.getNumberOfTestRuns()).isZero();
        assertThat(examChecklistDTO.getNumberOfTotalExamAssessmentsFinishedByCorrectionRound()).hasSize(2).containsExactly(expectedTotalExamAssessmentsFinishedByCorrectionRound);

        // change to a tutor
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        // check that a modified version is returned
        // check if stats are set correctly for the instructor
        examChecklistDTO = examService.getStatsForChecklist(exam, false);
        assertThat(examChecklistDTO).isNotNull();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isNull();
        assertThat(examChecklistDTO.getNumberOfExamsSubmitted()).isNull();
        assertThat(examChecklistDTO.getNumberOfExamsStarted()).isNull();
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isFalse();
        assertThat(examChecklistDTO.getNumberOfTotalParticipationsForAssessment()).isEqualTo(size * 6L);
        assertThat(examChecklistDTO.getNumberOfTestRuns()).isNull();
        assertThat(examChecklistDTO.getNumberOfTotalExamAssessmentsFinishedByCorrectionRound()).hasSize(2).containsExactly(expectedTotalExamAssessmentsFinishedByCorrectionRound);

        jenkinsRequestMockProvider.reset();

        final ProgrammingExercise programmingExercise = (ProgrammingExercise) exam.getExerciseGroups().get(6).getExercises().iterator().next();

        var usersOfExam = exam.getRegisteredUsers();
        mockDeleteProgrammingExercise(programmingExercise, usersOfExam);

        await().until(() -> participantScoreScheduleService.isIdle());

        // change back to instructor user
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // Make sure delete also works if so many objects have been created before
        waitForParticipantScores();
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
        assertThat(examRepository.findById(exam.getId())).isEmpty();
    }

    private void configureCourseAsBonusWithIndividualAndTeamResults(Course course, GradingScale bonusToGradingScale) {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        Long individualTextExerciseId = textExercise.getId();
        textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);

        Exercise teamExercise = textExerciseUtilService.createTeamTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        User tutor1 = userRepo.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
        Long teamTextExerciseId = teamExercise.getId();
        Long team1Id = teamUtilService.createTeam(Set.of(student1), tutor1, teamExercise, TEST_PREFIX + "team1").getId();
        User student2 = userRepo.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();
        User student3 = userRepo.findOneByLogin(TEST_PREFIX + "student3").orElseThrow();
        User tutor2 = userRepo.findOneByLogin(TEST_PREFIX + "tutor2").orElseThrow();
        Long team2Id = teamUtilService.createTeam(Set.of(student2, student3), tutor2, teamExercise, TEST_PREFIX + "team2").getId();

        participationUtilService.createParticipationSubmissionAndResult(individualTextExerciseId, student1, 10.0, 10.0, 50, true);

        Team team1 = teamRepository.findById(team1Id).orElseThrow();
        var result = participationUtilService.createParticipationSubmissionAndResult(teamTextExerciseId, team1, 10.0, 10.0, 40, true);
        // Creating a second results for team1 to test handling multiple results.
        participationUtilService.createSubmissionAndResult((StudentParticipation) result.getParticipation(), 50, true);

        var student2Result = participationUtilService.createParticipationSubmissionAndResult(individualTextExerciseId, student2, 10.0, 10.0, 50, true);

        var student3Result = participationUtilService.createParticipationSubmissionAndResult(individualTextExerciseId, student3, 10.0, 10.0, 30, true);

        Team team2 = teamRepository.findById(team2Id).orElseThrow();
        participationUtilService.createParticipationSubmissionAndResult(teamTextExerciseId, team2, 10.0, 10.0, 80, true);

        // Adding plagiarism cases
        var bonusPlagiarismCase = new PlagiarismCase();
        bonusPlagiarismCase.setStudent(student3);
        bonusPlagiarismCase.setExercise(student3Result.getParticipation().getExercise());
        bonusPlagiarismCase.setVerdict(PlagiarismVerdict.PLAGIARISM);
        plagiarismCaseRepository.save(bonusPlagiarismCase);

        var bonusPlagiarismCase2 = new PlagiarismCase();
        bonusPlagiarismCase2.setStudent(student2);
        bonusPlagiarismCase2.setExercise(student2Result.getParticipation().getExercise());
        bonusPlagiarismCase2.setVerdict(PlagiarismVerdict.POINT_DEDUCTION);
        bonusPlagiarismCase2.setVerdictPointDeduction(50);
        plagiarismCaseRepository.save(bonusPlagiarismCase2);

        BonusStrategy bonusStrategy = BonusStrategy.GRADES_CONTINUOUS;
        bonusToGradingScale.setBonusStrategy(bonusStrategy);
        gradingScaleRepository.save(bonusToGradingScale);

        GradingScale sourceGradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 60, 40, 50 }, Optional.of(new String[] { "0", "0.3", "0.6" }),
                true, 1);
        sourceGradingScale.setGradeType(GradeType.BONUS);
        sourceGradingScale.setCourse(course);
        gradingScaleRepository.save(sourceGradingScale);

        var bonus = BonusFactory.generateBonus(bonusStrategy, -1.0, sourceGradingScale.getId(), bonusToGradingScale.getId());
        bonusRepository.save(bonus);

        course.setMaxPoints(100);
        course.setPresentationScore(null);
        courseRepo.save(course);

    }

    private void waitForParticipantScores() {
        participantScoreScheduleService.executeScheduledTasks();
        await().until(() -> participantScoreScheduleService.isIdle());
    }

    private double calculateOverallPoints(Double correctionResultScore, StudentExam studentExamOfUser) {
        return studentExamOfUser.getExercises().stream().filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED))
                .map(Exercise::getMaxPoints).reduce(0.0, (total, maxScore) -> (Math.round((total + maxScore * correctionResultScore / 100) * 10) / 10.0));
    }

    private Set<User> getRegisteredStudentsForExam() {
        var registeredStudents = new HashSet<User>();
        for (int i = 1; i <= NUMBER_OF_STUDENTS; i++) {
            registeredStudents.add(userUtilService.getUserByLogin(TEST_PREFIX + "student" + i));
        }
        for (int i = 1; i <= NUMBER_OF_TUTORS; i++) {
            registeredStudents.add(userUtilService.getUserByLogin(TEST_PREFIX + "tutor" + i));
        }

        return registeredStudents;
    }
}
