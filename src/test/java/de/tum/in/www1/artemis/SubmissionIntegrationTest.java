package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;

class SubmissionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "submissionintegration";

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private CourseRepository courseRepository;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(TEST_PREFIX, 1, 1, 0, 1);
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
        Course course = database.addCourseWithModelingAndTextExercise();
        TextExercise textExercise = database.getFirstExerciseWithType(course, TextExercise.class);
        assertThat(textExercise).isNotNull();
        TextSubmission submission = ModelFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
        submission = database.saveTextSubmission(textExercise, submission, TEST_PREFIX + "student1");
        database.addResultToSubmission(submission, AssessmentType.MANUAL, database.getUserByLogin(TEST_PREFIX + "instructor1"));
        PageableSearchDTO<String> search = database.configureStudentParticipationSearch("");

        var resultPage = request.getSearchResult("/api/exercises/" + textExercise.getId() + "/submissions-for-import", HttpStatus.OK, Submission.class,
                database.searchMapping(search));
        assertThat(resultPage.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSubmissionsOnPageWithSize_exerciseNotFound() throws Exception {
        long randomExerciseId = 12345L;
        PageableSearchDTO<String> search = database.configureStudentParticipationSearch("");
        request.getSearchResult("/api/exercises/" + randomExerciseId + "/submissions-for-import", HttpStatus.NOT_FOUND, Submission.class, database.searchMapping(search));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSubmissionsOnPageWithSize_isNotAtLeastInstructorInExercise_forbidden() throws Exception {
        Course course = database.addCourseWithModelingAndTextExercise();
        TextExercise textExercise = database.getFirstExerciseWithType(course, TextExercise.class);
        assertThat(textExercise).isNotNull();
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        PageableSearchDTO<String> search = database.configureStudentParticipationSearch("");
        request.getSearchResult("/api/exercises/" + textExercise.getId() + "/submissions-for-import", HttpStatus.FORBIDDEN, Submission.class, database.searchMapping(search));
    }

}
