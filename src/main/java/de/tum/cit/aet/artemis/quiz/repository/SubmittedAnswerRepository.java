package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.AbstractQuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;

/**
 * Spring Data JPA repository for the SubmittedAnswer entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface SubmittedAnswerRepository extends ArtemisJpaRepository<SubmittedAnswer, Long> {

    Set<SubmittedAnswer> findBySubmission(AbstractQuizSubmission submission);

    /**
     * Fetches all {@link MultipleChoiceSubmittedAnswer}s for the given submission ids together with their
     * {@code selectedOptions} collection via a deterministic {@code LEFT JOIN FETCH}.
     * <p>
     * Used to defensively re-load selected options whose EAGER {@code @ManyToMany} fetch is not consistently
     * initialized by Hibernate when the parent {@code submittedAnswers} set is loaded polymorphically across
     * the single-table {@code SubmittedAnswer} hierarchy.
     *
     * @param submissionIds the ids of the quiz submissions whose MC submitted answers should be returned
     * @return the MC submitted answers with their {@code selectedOptions} initialized
     */
    @Query("""
            SELECT DISTINCT mcSa
            FROM MultipleChoiceSubmittedAnswer mcSa
                LEFT JOIN FETCH mcSa.selectedOptions
            WHERE mcSa.submission.id IN :submissionIds
            """)
    Set<MultipleChoiceSubmittedAnswer> findMcSubmittedAnswersWithSelectedOptionsBySubmissionIds(@Param("submissionIds") Set<Long> submissionIds);

    /**
     * Loads submitted answers from the database in case there is a QuizSubmission in one of the passed student participation.
     * Assumes that submissions are loaded eagerly in case they exist.
     * <p>
     * Also explicitly loads {@code selectedOptions} for all MC submitted answers via {@link #findMcSubmittedAnswersWithSelectedOptionsBySubmissionIds(Set)}
     * and copies the resolved collections onto the submitted answers. This is needed because {@code selectedOptions} is an
     * EAGER {@code @ManyToMany} on a subclass in a single-table inheritance hierarchy with a second-level cache, and Hibernate does
     * not reliably populate it when submissions are loaded polymorphically. Without this, concurrent refreshes can observe different
     * subsets of the stored selection and the per-question score flips between 0 and its true value (see Artemis issue #12574).
     *
     * @param participations the student participations for which the submitted answers in quiz submissions should be loaded
     */
    default void loadQuizSubmissionsSubmittedAnswers(Collection<StudentParticipation> participations) {
        Set<QuizSubmission> quizSubmissions = new HashSet<>();
        for (var participation : participations) {
            if (participation.getExercise() instanceof QuizExercise) {
                if (participation.getSubmissions() != null) {
                    for (var submission : participation.getSubmissions()) {
                        var quizSubmission = (QuizSubmission) submission;
                        // submitted answers can only be lazy loaded in many cases, so we load them explicitly for each submission here
                        var submittedAnswers = findBySubmission(quizSubmission);
                        quizSubmission.setSubmittedAnswers(submittedAnswers);
                        quizSubmissions.add(quizSubmission);
                    }
                }
            }
        }
        initializeSelectedOptionsForMultipleChoiceAnswers(quizSubmissions);
    }

    /**
     * Force-loads {@code selectedOptions} for every {@link MultipleChoiceSubmittedAnswer} in the given submissions via a deterministic
     * {@code LEFT JOIN FETCH} query, then copies the resolved collection onto each submitted answer. See
     * {@link #loadQuizSubmissionsSubmittedAnswers(Collection)} for the motivation.
     *
     * @param submissions the quiz submissions whose MC submitted answers should have their selected options loaded (no-op if empty)
     */
    default void initializeSelectedOptionsForMultipleChoiceAnswers(Collection<QuizSubmission> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return;
        }
        Set<Long> submissionIds = submissions.stream().map(QuizSubmission::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (submissionIds.isEmpty()) {
            return;
        }
        Map<Long, MultipleChoiceSubmittedAnswer> byId = findMcSubmittedAnswersWithSelectedOptionsBySubmissionIds(submissionIds).stream()
                .collect(Collectors.toMap(MultipleChoiceSubmittedAnswer::getId, mcSa -> mcSa, (a, b) -> a));
        for (QuizSubmission submission : submissions) {
            Set<SubmittedAnswer> submittedAnswers = submission.getSubmittedAnswers();
            if (submittedAnswers == null) {
                continue;
            }
            for (SubmittedAnswer submittedAnswer : submittedAnswers) {
                if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer mcSa) {
                    MultipleChoiceSubmittedAnswer loaded = byId.get(mcSa.getId());
                    if (loaded != null) {
                        mcSa.setSelectedOptions(loaded.getSelectedOptions());
                    }
                }
            }
        }
    }
}
