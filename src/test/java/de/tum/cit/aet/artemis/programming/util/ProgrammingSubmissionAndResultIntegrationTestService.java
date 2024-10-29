package de.tum.cit.aet.artemis.programming.util;

import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.JAVA;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for scenarios:
 * 1) Jenkins + Gitlab
 */
@Service
public class ProgrammingSubmissionAndResultIntegrationTestService {

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    public ProgrammingExercise programmingExercise;

    public ProgrammingExerciseParticipation participation;

    public void setUp_shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived(String userPrefix) {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, false, JAVA);
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndLegalSubmissionsById(programmingExercise.getId()).orElseThrow();
        participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "student1");
    }

    /**
     * Verifies both Atlassian and Jenkins/Gitlab tests of the same name
     *
     * @param firstCommitHash  Hash of the first commit made (second to be received)
     * @param firstCommitDate  Date of the first commit made (second to be received)
     * @param secondCommitHash Hash of the second commit made (first to be received)
     * @param secondCommitDate Date of the second commit made (first to be received)
     */
    public void shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived(String firstCommitHash, ZonedDateTime firstCommitDate, String secondCommitHash,
            ZonedDateTime secondCommitDate) {
        var submissions = programmingSubmissionRepository.findAllByParticipationIdWithResults(participation.getId());

        // Ensure correct submission and result count
        assertThat(submissions).hasSize(2);
        assertThat(submissions.getFirst().getResults()).hasSize(1);
        assertThat(submissions.get(1).getResults()).hasSize(1);

        Submission submissionOfFirstCommit = submissions.stream().filter(submission -> submission.getCommitHash().equals(firstCommitHash)).findFirst().orElseThrow();
        Submission submissionOfSecondCommit = submissions.stream().filter(submission -> submission.getCommitHash().equals(secondCommitHash)).findFirst().orElseThrow();

        // Ensure submission date is in correct order
        assertThat(submissionOfFirstCommit.getSubmissionDate()).isBefore(submissionOfSecondCommit.getSubmissionDate());

        // Ensure submission dates are precise, some decimals/nanos get lost through time conversions
        assertThat(ChronoUnit.MILLIS.between(submissionOfFirstCommit.getSubmissionDate(), firstCommitDate)).isLessThan(50);
        assertThat(ChronoUnit.MILLIS.between(submissionOfSecondCommit.getSubmissionDate(), secondCommitDate)).isLessThan(50);
    }

    /**
     * This is the simulated request from the VCS to Artemis on a new commit.
     *
     * @return The submission that was created
     */
    public ProgrammingSubmission postSubmission(Long participationId, HttpStatus expectedStatus, String jsonRequest) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Object obj = mapper.readValue(jsonRequest, Object.class);

        // Api should return ok.
        request.postWithoutLocation("/api/public/programming-submissions/" + participationId, obj, expectedStatus, new HttpHeaders());

        List<ProgrammingSubmission> submissions = programmingSubmissionRepository.findAllByParticipationIdWithResults(participationId);

        // Submission should have been created for the participation.
        assertThat(submissions).hasSize(1);
        // Make sure that both the submission and participation are correctly linked with each other.
        return submissions.getFirst();
    }
}
