package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import de.tum.cit.aet.artemis.assessment.domain.ExternalSubmissionIneligibility;
import de.tum.cit.aet.artemis.assessment.dto.ExternalSubmissionCandidateDTO;
import de.tum.cit.aet.artemis.assessment.dto.ExternalSubmissionResultDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingExerciseTestRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Tests the student search of the external submission dialog, and that it applies the same rules as the endpoint that creates the external result.
 */
class ExternalSubmissionCandidatesIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "extsubmissioncandidates";

    private static final String REGISTRATION_NUMBER = "77345991";

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ModelingExerciseTestRepository modelingExerciseRepository;

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Course course;

    private ModelingExercise modelingExercise;

    private ModelingExercise examModelingExercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 4, 1, 1, 1);
        course = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("ClassDiagram", TEST_PREFIX);
        modelingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);
        modelingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        modelingExerciseRepository.save(modelingExercise);

        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam.setStartDate(ZonedDateTime.now().minusHours(3));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        examRepository.save(exam);
        examModelingExercise = new ModelingExercise();
        examModelingExercise.setMaxPoints(100D);
        examModelingExercise.setExerciseGroup(exam.getExerciseGroups().getFirst());
        modelingExerciseRepository.save(examModelingExercise);
    }

    // I1
    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @ValueSource(strings = { "login", "name", "email", "registrationNumber" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchFindsEligibleStudentByEveryColumn(String column) throws Exception {
        userUtilService.setRegistrationNumberOfUserAndSave(TEST_PREFIX + "student1", REGISTRATION_NUMBER);
        String searchTerm = switch (column) {
            case "login" -> TEST_PREFIX + "student1";
            case "name" -> TEST_PREFIX + "student1First";
            case "email" -> TEST_PREFIX + "student1@test";
            default -> REGISTRATION_NUMBER;
        };

        MvcResult response = search(modelingExercise, searchTerm).andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "1")).andReturn();

        assertThat(candidatesOf(response)).containsExactly(new ExternalSubmissionCandidateDTO(TEST_PREFIX + "student1", name(TEST_PREFIX + "student1"), null));
        // only login, name and, for ineligible students, the reason leave the server
        assertThat(response.getResponse().getContentAsString()).doesNotContain("email", "registrationNumber", "\"id\"", "ineligibility");
    }

    // I2
    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @ValueSource(strings = { "tutor1", "editor1", "instructor1", "outsider" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchNeverSuggestsStaffOrUsersOutsideTheCourse(String loginSuffix) throws Exception {
        userUtilService.createAndSaveUser(TEST_PREFIX + "outsider");

        MvcResult response = search(modelingExercise, TEST_PREFIX + loginSuffix).andExpect(status().isOk()).andReturn();

        assertThat(candidatesOf(response)).isEmpty();
    }

    // I3
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchFlagsStudentWithExistingResult() throws Exception {
        addResult(modelingExercise, TEST_PREFIX + "student1");

        List<ExternalSubmissionCandidateDTO> candidates = candidatesOf(search(modelingExercise, TEST_PREFIX + "student").andExpect(status().isOk()).andReturn());

        assertThat(candidates).contains(
                new ExternalSubmissionCandidateDTO(TEST_PREFIX + "student1", name(TEST_PREFIX + "student1"), ExternalSubmissionIneligibility.RESULT_ALREADY_EXISTS),
                new ExternalSubmissionCandidateDTO(TEST_PREFIX + "student2", name(TEST_PREFIX + "student2"), null));
    }

    // I4
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchFlagsExamStudentsWithoutStudentExamOrWithoutTheExercise() throws Exception {
        Exam exam = examRepository.findByIdElseThrow(examModelingExercise.getExerciseGroup().getExam().getId());
        // student1 has no student exam, student2 has one without this exercise, student3 has one with it
        examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "student2");
        StudentExam withExercise = examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "student3");
        examUtilService.addExerciseToStudentExam(withExercise, examModelingExercise);

        List<ExternalSubmissionCandidateDTO> candidates = candidatesOf(search(examModelingExercise, TEST_PREFIX + "student").andExpect(status().isOk()).andReturn());

        assertThat(candidates).contains(
                new ExternalSubmissionCandidateDTO(TEST_PREFIX + "student1", name(TEST_PREFIX + "student1"), ExternalSubmissionIneligibility.NO_STUDENT_EXAM),
                new ExternalSubmissionCandidateDTO(TEST_PREFIX + "student2", name(TEST_PREFIX + "student2"), ExternalSubmissionIneligibility.EXERCISE_NOT_IN_STUDENT_EXAM),
                new ExternalSubmissionCandidateDTO(TEST_PREFIX + "student3", name(TEST_PREFIX + "student3"), null));
    }

    // I5
    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("ineligibleCases")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchAndCreateAgreeOnEveryIneligibleStudent(ExternalSubmissionIneligibility expected) throws Exception {
        String login = TEST_PREFIX + "student1";
        Exercise exercise = switch (expected) {
            case RESULT_ALREADY_EXISTS -> {
                addResult(modelingExercise, login);
                yield modelingExercise;
            }
            case NO_STUDENT_EXAM -> examModelingExercise;
            case EXERCISE_NOT_IN_STUDENT_EXAM -> {
                examUtilService.addStudentExamWithUser(examRepository.findByIdElseThrow(examModelingExercise.getExerciseGroup().getExam().getId()), login);
                yield examModelingExercise;
            }
            case NOT_IN_COURSE -> throw new IllegalArgumentException("the search only returns course students");
        };

        List<ExternalSubmissionCandidateDTO> candidates = candidatesOf(search(exercise, login).andExpect(status().isOk()).andReturn());
        assertThat(candidates).singleElement().extracting(ExternalSubmissionCandidateDTO::ineligibility).isEqualTo(expected);

        create(exercise, login).andExpect(status().isBadRequest()).andExpect(header().string("X-artemisApp-error", "error." + expected.getErrorKey()));
    }

    private static Stream<Arguments> ineligibleCases() {
        return Stream.of(Arguments.of(ExternalSubmissionIneligibility.RESULT_ALREADY_EXISTS), Arguments.of(ExternalSubmissionIneligibility.NO_STUDENT_EXAM),
                Arguments.of(ExternalSubmissionIneligibility.EXERCISE_NOT_IN_STUDENT_EXAM));
    }

    // I6
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void searchIsForbiddenForTutorsEvenWithATooShortTerm() throws Exception {
        search(modelingExercise, TEST_PREFIX + "student").andExpect(status().isForbidden());
        search(modelingExercise, "ab").andExpect(status().isForbidden());
    }

    // I6
    @Test
    @WithMockUser(username = TEST_PREFIX + "otherinstructor", roles = "USER")
    void searchIsForbiddenForInstructorsOfOtherCourses() throws Exception {
        userUtilService.createAndSaveUser(TEST_PREFIX + "otherinstructor");
        search(modelingExercise, TEST_PREFIX + "student").andExpect(status().isForbidden());
    }

    // I6
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchRejectsTooShortTerm() throws Exception {
        search(modelingExercise, "ab").andExpect(status().isBadRequest()).andExpect(header().string("X-artemisApp-error", "error.searchTermTooShort"));
    }

    // I8
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchAndCreateRejectExerciseBeforeItsDueDate() throws Exception {
        modelingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        modelingExerciseRepository.save(modelingExercise);

        search(modelingExercise, TEST_PREFIX + "student").andExpect(status().isBadRequest())
                .andExpect(header().string("X-artemisApp-error", "error.externalSubmissionBeforeDueDate"));
        create(modelingExercise, TEST_PREFIX + "student1").andExpect(status().isBadRequest())
                .andExpect(header().string("X-artemisApp-error", "error.externalSubmissionBeforeDueDate"));
    }

    // I8
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchAndCreateRejectQuizExercisesBeforeCheckingTheSearchTerm() throws Exception {
        var now = ZonedDateTime.now();
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(now.minusDays(1), now.minusHours(2), QuizMode.SYNCHRONIZED, course);
        course.addExercises(quizExercise);
        quizExerciseRepository.save(quizExercise);

        search(quizExercise, TEST_PREFIX + "student").andExpect(status().isBadRequest())
                .andExpect(header().string("X-artemisApp-error", "error.externalSubmissionForQuizExercise"));
        // the exercise-level check runs before the length check
        search(quizExercise, "ab").andExpect(status().isBadRequest()).andExpect(header().string("X-artemisApp-error", "error.externalSubmissionForQuizExercise"));
        create(quizExercise, TEST_PREFIX + "student1").andExpect(status().isBadRequest())
                .andExpect(header().string("X-artemisApp-error", "error.externalSubmissionForQuizExercise"));
    }

    private org.springframework.test.web.servlet.ResultActions search(Exercise exercise, String searchTerm) throws Exception {
        return request.performMvcRequest(get("/api/assessment/exercises/{exerciseId}/external-submission-candidates", exercise.getId()).param("searchTerm", searchTerm)
                .param("page", "0").param("pageSize", "10"));
    }

    private org.springframework.test.web.servlet.ResultActions create(Exercise exercise, String studentLogin) throws Exception {
        String body = request.getObjectMapper().writeValueAsString(new ExternalSubmissionResultDTO(100D, true, List.of()));
        return request.performMvcRequest(post("/api/assessment/exercises/{exerciseId}/external-submission-results", exercise.getId()).param("studentLogin", studentLogin)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<ExternalSubmissionCandidateDTO> candidatesOf(MvcResult response) throws Exception {
        var mapper = request.getObjectMapper();
        return mapper.readValue(response.getResponse().getContentAsString(), mapper.getTypeFactory().constructCollectionType(List.class, ExternalSubmissionCandidateDTO.class));
    }

    private void addResult(Exercise exercise, String studentLogin) {
        var participation = participationUtilService.createAndSaveParticipationForExercise(exercise, studentLogin);
        Submission submission = participationUtilService.addSubmission(participation, new ModelingSubmission());
        participationUtilService.addResultToSubmission(null, null, submission);
    }

    private String name(String login) {
        return userTestRepository.findOneByLogin(login).orElseThrow().getName();
    }
}
