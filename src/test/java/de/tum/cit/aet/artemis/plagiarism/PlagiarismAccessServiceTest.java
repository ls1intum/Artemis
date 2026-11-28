package de.tum.cit.aet.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismSubmissionRepository;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismAccessService;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * The comparisons are looked up per exercise, because the compared ids of different exercise types come from different id spaces and can be equal.
 */
class PlagiarismAccessServiceTest {

    private static final long EXERCISE_ID = 7L;

    private static final long OTHER_EXERCISE_ID = 8L;

    private static final long COMPARED_ID = 42L;

    private PlagiarismComparisonRepository comparisonRepository;

    private PlagiarismSubmissionRepository submissionRepository;

    private PlagiarismAccessService service;

    @BeforeEach
    void setUp() {
        comparisonRepository = mock(PlagiarismComparisonRepository.class);
        submissionRepository = mock(PlagiarismSubmissionRepository.class);
        service = new PlagiarismAccessService(comparisonRepository, submissionRepository, mock(ExerciseDateService.class));
    }

    private static PlagiarismComparison notifiedComparisonFor(String login) {
        var plagiarismCase = new PlagiarismCase();
        plagiarismCase.setVerdict(PlagiarismVerdict.WARNING);
        var submission = new PlagiarismSubmission();
        submission.setSubmissionId(COMPARED_ID);
        submission.setStudentLogin(login);
        submission.setPlagiarismCase(plagiarismCase);
        var comparison = new PlagiarismComparison();
        comparison.setSubmissionA(submission);
        return comparison;
    }

    @Test
    void wasUserNotifiedByInstructor_onlyConsidersComparisonsOfTheGivenExercise() {
        when(comparisonRepository.findByPlagiarismResultExerciseIdAndSubmissions_SubmissionId(EXERCISE_ID, COMPARED_ID))
                .thenReturn(Optional.of(Set.of(notifiedComparisonFor("student1"))));
        when(comparisonRepository.findByPlagiarismResultExerciseIdAndSubmissions_SubmissionId(OTHER_EXERCISE_ID, COMPARED_ID)).thenReturn(Optional.empty());
        Submission submission = new TextSubmission();
        submission.setId(COMPARED_ID);

        assertThat(service.wasUserNotifiedByInstructor(EXERCISE_ID, submission, "student1")).isTrue();
        assertThat(service.wasUserNotifiedByInstructor(OTHER_EXERCISE_ID, submission, "student1")).isFalse();
    }

    @Test
    void findComparedCommitHash_asksForTheNewestRowOnly() {
        when(submissionRepository.findComparedCommitHashes(EXERCISE_ID, COMPARED_ID, Pageable.ofSize(1))).thenReturn(List.of("newest"));
        when(submissionRepository.findComparedCommitHashes(OTHER_EXERCISE_ID, COMPARED_ID, Pageable.ofSize(1))).thenReturn(List.of());

        assertThat(service.findComparedCommitHash(EXERCISE_ID, COMPARED_ID)).contains("newest");
        assertThat(service.findComparedCommitHash(OTHER_EXERCISE_ID, COMPARED_ID)).isEmpty();
    }

    @Test
    void findComparisonCreatedDate_asksForTheNewestRowOnly() {
        Instant created = Instant.parse("2026-01-01T10:00:00Z");
        when(submissionRepository.findComparisonCreatedDates(EXERCISE_ID, COMPARED_ID, Pageable.ofSize(1))).thenReturn(List.of(created));

        assertThat(service.findComparisonCreatedDate(EXERCISE_ID, COMPARED_ID)).contains(created);
        assertThat(service.findComparisonCreatedDate(OTHER_EXERCISE_ID, COMPARED_ID)).isEmpty();
    }
}
