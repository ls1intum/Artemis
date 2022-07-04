package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
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
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

public class SubmissionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private CourseRepository courseRepository;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(1, 1, 0, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void addMultipleResultsToOneSubmission() {
        AssessmentType assessmentType = AssessmentType.MANUAL;

        Submission submission = new TextSubmission();
        submission = submissionRepository.save(submission);

        Result result1 = new Result().assessmentType(assessmentType).resultString("x points of y").score(100D).rated(true);
        result1 = resultRepository.save(result1);
        result1.setSubmission(submission);

        Result result2 = new Result().assessmentType(assessmentType).resultString("x points of y 2").score(200D).rated(true);
        result2 = resultRepository.save(result2);
        result2.setSubmission(submission);

        submission.addResult(result1);
        submission.addResult(result2);

        var savedSubmission = submissionRepository.save(submission);
        submission = submissionRepository.findWithEagerResultsAndAssessorById(savedSubmission.getId()).orElseThrow();

        assert submission.getResults() != null;
        assertThat(submission.getResults()).hasSize(2);
        assertThat(submission.getFirstResult()).isNotEqualTo(submission.getLatestResult());
        assertThat(submission.getFirstResult()).isEqualTo(result1);
        assertThat(submission.getLatestResult()).isEqualTo(result2);

    }

    @Test
    public void addMultipleResultsToOneSubmissionSavedSequentially() {
        AssessmentType assessmentType = AssessmentType.MANUAL;

        Submission submission = new TextSubmission();
        submission = submissionRepository.save(submission);

        Result result1 = new Result().assessmentType(assessmentType).resultString("x points of y").score(100D).rated(true);
        result1 = resultRepository.save(result1);
        result1.setSubmission(submission);

        submission.addResult(result1);
        submission = submissionRepository.save(submission);

        Result result2 = new Result().assessmentType(assessmentType).resultString("x points of y 2").score(200D).rated(true);
        result2 = resultRepository.save(result2);
        result2.setSubmission(submission);

        submission.addResult(result2);
        submission = submissionRepository.save(submission);

        submission = submissionRepository.findWithEagerResultsAndAssessorById(submission.getId()).orElseThrow();

        assert submission.getResults() != null;
        assertThat(submission.getResults()).hasSize(2);
        assertThat(submission.getFirstResult()).isNotEqualTo(submission.getLatestResult());
        assertThat(submission.getFirstResult()).isEqualTo(result1);
        assertThat(submission.getLatestResult()).isEqualTo(result2);

    }

    @Test
    public void updateMultipleResultsFromOneSubmission() {
        AssessmentType assessmentType = AssessmentType.MANUAL;

        Submission submission = new TextSubmission();
        submission = submissionRepository.save(submission);

        Result result1 = new Result().assessmentType(assessmentType).resultString("Points 1").score(100D).rated(true);
        result1 = resultRepository.save(result1);
        result1.setSubmission(submission);

        submission.addResult(result1);
        submission = submissionRepository.save(submission);

        Result result2 = new Result().assessmentType(assessmentType).resultString("Points 2").score(200D).rated(true);
        result2 = resultRepository.save(result2);
        result2.setSubmission(submission);

        submission.addResult(result2);
        submission = submissionRepository.save(submission);

        result1.setResultString("New Result #1");
        result1 = resultRepository.save(result1);

        result2.setResultString("New Result #2");
        result2 = resultRepository.save(result2);

        submission = submissionRepository.findWithEagerResultsAndAssessorById(submission.getId()).orElseThrow();

        assert submission.getResults() != null;
        assertThat(submission.getResults()).hasSize(2);
        assertThat(submission.getFirstResult()).isNotEqualTo(submission.getLatestResult());
        assertThat(submission.getFirstResult()).isEqualTo(result1);
        assertThat(submission.getLatestResult()).isEqualTo(result2);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetSubmissionsOnPageWithSize() throws Exception {
        Course course = database.addCourseWithModelingAndTextExercise();
        TextExercise textExercise = (TextExercise) course.getExercises().stream().filter(exercise -> exercise instanceof TextExercise).findFirst().orElse(null);
        assertThat(textExercise).isNotNull();
        TextSubmission submission = ModelFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
        submission = database.saveTextSubmission(textExercise, submission, "student1");
        database.addResultToSubmission(submission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"));
        PageableSearchDTO<String> search = database.configureStudentParticipationSearch("");

        var resultPage = request.get("/api/exercises/" + textExercise.getId() + "/submissions-for-import", HttpStatus.OK, SearchResultPageDTO.class,
                database.searchMapping(search));
        assertThat(resultPage.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetSubmissionsOnPageWithSize_exerciseNotFound() throws Exception {
        long randomExerciseId = 12345L;
        PageableSearchDTO<String> search = database.configureStudentParticipationSearch("");
        request.get("/api/exercises/" + randomExerciseId + "/submissions-for-import", HttpStatus.NOT_FOUND, SearchResultPageDTO.class, database.searchMapping(search));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetSubmissionsOnPageWithSize_isNotAtLeastInstructorInExercise_forbidden() throws Exception {
        Course course = database.addCourseWithModelingAndTextExercise();
        TextExercise textExercise = (TextExercise) course.getExercises().stream().filter(exercise -> exercise instanceof TextExercise).findFirst().orElse(null);
        assertThat(textExercise).isNotNull();
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        PageableSearchDTO<String> search = database.configureStudentParticipationSearch("");
        request.get("/api/exercises/" + textExercise.getId() + "/submissions-for-import", HttpStatus.FORBIDDEN, SearchResultPageDTO.class, database.searchMapping(search));
    }

}
