package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.config.Constants.PROGRAMMING_SUBMISSION_RESOURCE_API_PATH;
import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.JAVA;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for both scenarios:
 * 1) Bamboo + Bitbucket
 * 2) Jenkins + Gitlab
 */
@Service
public class ProgrammingSubmissionAndResultIntegrationTestService {

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private DatabaseUtilService database;

    public ProgrammingExercise programmingExercise;

    public ProgrammingExerciseParticipation participation;

    public void setUp_shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived() {
        database.addCourseWithOneProgrammingExercise(false, false, JAVA);
        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipationsAndLegalSubmissions().get(1);
        participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
    }

    /**
     * Verifies both Atlassian and Jenkins/Gitlab tests of the same name
     *
     * @param firstCommitHash Hash of the first commit made (second to be received)
     * @param firstCommitDate Date of the first commit made (second to be received)
     * @param secondCommitHash Hash of the second commit made (first to be received)
     * @param secondCommitDate Date of the second commit made (first to be received)
     */
    public void shouldSetSubmissionDateForBuildCorrectlyIfOnlyOnePushIsReceived(String firstCommitHash, ZonedDateTime firstCommitDate, String secondCommitHash,
            ZonedDateTime secondCommitDate) {
        var submissions = programmingSubmissionRepository.findAllByParticipationIdWithResults(participation.getId());

        // Ensure correct submission and result count
        assertThat(submissions).hasSize(2);
        assertThat(submissions.get(0).getResults()).hasSize(1);
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
     * @return The submission that was created
     */
    public ProgrammingSubmission postSubmission(Long participationId, HttpStatus expectedStatus, String jsonRequest) throws Exception {
        JSONParser jsonParser = new JSONParser();
        Object obj = jsonParser.parse(jsonRequest);

        // Api should return ok.
        request.postWithoutLocation(PROGRAMMING_SUBMISSION_RESOURCE_API_PATH + participationId, obj, expectedStatus, new HttpHeaders());

        List<ProgrammingSubmission> submissions = programmingSubmissionRepository.findAll();

        // Submission should have been created for the participation.
        assertThat(submissions).hasSize(1);
        // Make sure that both the submission and participation are correctly linked with each other.
        return submissions.get(0);
    }
}
