package de.tum.in.www1.artemis.participation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionVersionDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;

class SubmissionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "submissionintegration";

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
    }

    @Test
    void addMultipleResultsToOneSubmission() {
        AssessmentType assessmentType = AssessmentType.MANUAL;

        Submission submission = new TextSubmission();
        submission = submissionRepository.save(submission);

        Result result1 = new Result().assessmentType(assessmentType).score(100D).rated(true);
        result1 = resultRepository.save(result1);
        result1.setSubmission(submission);

        Result result2 = new Result().assessmentType(assessmentType).score(200D).rated(true);
        result2 = resultRepository.save(result2);
        result2.setSubmission(submission);

        submission.addResult(result1);
        submission.addResult(result2);

        var savedSubmission = submissionRepository.save(submission);
        submission = submissionRepository.findWithEagerResultsAndAssessorById(savedSubmission.getId()).orElseThrow();

        assertThat(submission.getResults()).hasSize(2);
        assertThat(submission.getFirstResult()).isNotEqualTo(submission.getLatestResult());
        assertThat(submission.getFirstResult()).isEqualTo(result1);
        assertThat(submission.getLatestResult()).isEqualTo(result2);

    }

    @Test
    void addMultipleResultsToOneSubmissionSavedSequentially() {
        AssessmentType assessmentType = AssessmentType.MANUAL;

        Submission submission = new TextSubmission();
        submission = submissionRepository.save(submission);

        Result result1 = new Result().assessmentType(assessmentType).score(100D).rated(true);
        result1 = resultRepository.save(result1);
        result1.setSubmission(submission);

        submission.addResult(result1);
        submission = submissionRepository.save(submission);

        Result result2 = new Result().assessmentType(assessmentType).score(200D).rated(true);
        result2 = resultRepository.save(result2);
        result2.setSubmission(submission);

        submission.addResult(result2);
        submission = submissionRepository.save(submission);

        submission = submissionRepository.findWithEagerResultsAndAssessorById(submission.getId()).orElseThrow();

        assertThat(submission.getResults()).hasSize(2);
        assertThat(submission.getFirstResult()).isNotEqualTo(submission.getLatestResult());
        assertThat(submission.getFirstResult()).isEqualTo(result1);
        assertThat(submission.getLatestResult()).isEqualTo(result2);

    }

    @Test
    void updateMultipleResultsFromOneSubmission() {
        AssessmentType assessmentType = AssessmentType.MANUAL;

        Submission submission = new TextSubmission();
        submission = submissionRepository.save(submission);

        Result result1 = new Result().assessmentType(assessmentType).score(100D).rated(true);
        result1 = resultRepository.save(result1);
        result1.setSubmission(submission);

        submission.addResult(result1);
        submission = submissionRepository.save(submission);

        Result result2 = new Result().assessmentType(assessmentType).score(200D).rated(true);
        result2 = resultRepository.save(result2);
        result2.setSubmission(submission);

        submission.addResult(result2);
        submission = submissionRepository.save(submission);

        result1.setScore(42D);
        result1 = resultRepository.save(result1);

        result2.setScore(1337D);
        result2 = resultRepository.save(result2);

        submission = submissionRepository.findWithEagerResultsAndAssessorById(submission.getId()).orElseThrow();

        assertThat(submission.getResults()).hasSize(2);
        assertThat(submission.getFirstResult()).isNotEqualTo(submission.getLatestResult());
        assertThat(submission.getFirstResult()).isEqualTo(result1);
        assertThat(submission.getLatestResult()).isEqualTo(result2);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSubmissionsOnPageWithSize() throws Exception {
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        TextExercise textExercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        assertThat(textExercise).isNotNull();
        TextSubmission submission = ParticipationFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
        submission = textExerciseUtilService.saveTextSubmission(textExercise, submission, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(submission, AssessmentType.MANUAL, userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureStudentParticipationSearch("");

        var resultPage = request.getSearchResult("/api/exercises/" + textExercise.getId() + "/submissions-for-import", HttpStatus.OK, Submission.class,
                pageableSearchUtilService.searchMapping(search));
        assertThat(resultPage.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSubmissionsOnPageWithSize_exerciseNotFound() throws Exception {
        long randomExerciseId = UUID.nameUUIDFromBytes("test".getBytes()).getMostSignificantBits();
        SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureStudentParticipationSearch("");
        request.getSearchResult("/api/exercises/" + randomExerciseId + "/submissions-for-import", HttpStatus.NOT_FOUND, Submission.class,
                pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSubmissionsOnPageWithSize_isNotAtLeastInstructorInExercise_forbidden() throws Exception {
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        TextExercise textExercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        assertThat(textExercise).isNotNull();
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureStudentParticipationSearch("");
        request.getSearchResult("/api/exercises/" + textExercise.getId() + "/submissions-for-import", HttpStatus.FORBIDDEN, Submission.class,
                pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSubmissionVersionsBySubmissionId_isNotInstructorInCourse_forbidden() throws Exception {
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        TextExercise textExercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        TextSubmission submission = ParticipationFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
        submission = submissionRepository.save(submission);
        participationUtilService.addSubmission(textExercise, submission, TEST_PREFIX + "student1");
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        request.getList("/api/submissions/" + submission.getId() + "/versions", HttpStatus.FORBIDDEN, Submission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSubmissionVersionsBySubmissionIdForTextExercise_returnsCorrectContent() throws Exception {
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        TextExercise textExercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        TextSubmission submission = ParticipationFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
        submission = submissionRepository.save(submission);
        SubmissionVersion submissionVersion1 = ParticipationFactory.generateSubmissionVersion("test1", submission, student);
        submissionVersion1 = submissionVersionRepository.save(submissionVersion1);
        SubmissionVersion submissionVersion2 = ParticipationFactory.generateSubmissionVersion("test2", submission, student);
        submissionVersion2 = submissionVersionRepository.save(submissionVersion2);
        participationUtilService.addSubmission(textExercise, submission, TEST_PREFIX + "student1");
        var expected1 = SubmissionVersionDTO.of(submissionVersion1);
        var expected2 = SubmissionVersionDTO.of(submissionVersion2);
        List<SubmissionVersionDTO> versions = request.getList("/api/submissions/" + submission.getId() + "/versions", HttpStatus.OK, SubmissionVersionDTO.class);
        assertThat(versions).usingRecursiveFieldByFieldElementComparatorIgnoringFields("createdDate").containsExactly(expected1, expected2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSubmissionVersionsBySubmissionId_submissionNotFound() throws Exception {
        long randomSubmissionId = UUID.nameUUIDFromBytes("test".getBytes()).getMostSignificantBits();
        request.getList("/api/submissions/" + randomSubmissionId + "/versions", HttpStatus.NOT_FOUND, Submission.class);
    }

}
