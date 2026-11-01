package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationOverviewDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

class ParticipationOverviewDTOTest {

    @Test
    void datedTextDraftCountsOnlyAfterItHasBeenSubmitted() {
        var participation = new StudentParticipation();
        var submission = new TextSubmission();
        submission.setSubmissionDate(ZonedDateTime.now().minusHours(1));
        submission.setSubmitted(false);
        participation.setSubmissions(Set.of(submission));
        assertThat(ParticipationOverviewDTO.of(participation).hasSubmitted()).isFalse();
        submission.setSubmitted(true);
        assertThat(ParticipationOverviewDTO.of(participation).hasSubmitted()).isTrue();
    }

    @Test
    void programmingCommitTimestampCountsAsASubmissionAndKeepsRepositoryLink() {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setRepositoryUri("https://artemis.example/git/course/exercise-student.git");
        var submission = new ProgrammingSubmission();
        submission.setSubmitted(false);
        participation.setSubmissions(Set.of(submission));
        assertThat(ParticipationOverviewDTO.of(participation).hasSubmitted()).isFalse();
        submission.setSubmissionDate(ZonedDateTime.now().minusHours(1));
        var overview = ParticipationOverviewDTO.of(participation);
        assertThat(overview.hasSubmitted()).isTrue();
        assertThat(overview.repositoryUri()).isEqualTo(participation.getRepositoryUri());
    }

    @Test
    void ratedResultCountsAsEvidenceEvenWhenSubmissionFlagIsMissing() {
        var participation = new StudentParticipation();
        var submission = new TextSubmission();
        var result = new Result();
        result.setRated(true);
        submission.addResult(result);
        participation.setSubmissions(Set.of(submission));
        assertThat(ParticipationOverviewDTO.of(participation).hasSubmitted()).isTrue();
        assertThat(ParticipationOverviewDTO.of(Set.of(participation))).singleElement().satisfies(overview -> assertThat(overview.hasSubmitted()).isTrue());
    }

    @Test
    void absentParticipationsAndSubmissionsAreEmpty() {
        assertThat(ParticipationOverviewDTO.of((Set<StudentParticipation>) null)).isEmpty();
        var participation = new StudentParticipation();
        participation.setSubmissions(null);
        var overview = ParticipationOverviewDTO.of(participation);
        assertThat(overview.submissions()).isEmpty();
        assertThat(overview.hasSubmitted()).isFalse();
    }
}
