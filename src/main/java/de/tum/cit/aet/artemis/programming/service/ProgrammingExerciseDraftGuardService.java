package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentDraftRules;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Keeps a programming exercise that is changed with a personal access token a draft.
 * <p>
 * Besides the date rules every kind of exercise follows, a programming exercise must have no student participations: changing
 * its problem statement, tests or timeline would affect work students have already started. Requests that are not made with a
 * token are never restricted.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseDraftGuardService {

    private static final String PARTICIPATIONS_MESSAGE = "This exercise already has participations, of students or of staff who tried it. It can be changed only in the Artemis web interface.";

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public ProgrammingExerciseDraftGuardService(ProgrammingExerciseRepository programmingExerciseRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Requires a token request to change only a draft exercise without student participations, and not to publish it.
     *
     * @param exercise       the stored exercise
     * @param newReleaseDate the release date the request would set
     * @param newStartDate   the start date the request would set
     * @throws AccessForbiddenException if the exercise is published or has student participations
     */
    public void requireDraftForTokenRequest(ProgrammingExercise exercise, @Nullable ZonedDateTime newReleaseDate, @Nullable ZonedDateTime newStartDate) {
        if (!AgentDraftRules.isTokenRequest()) {
            return;
        }
        AgentDraftRules.requireStillDraft(exercise.getReleaseDate(), exercise.getStartDate(), newReleaseDate, newStartDate);
        if (programmingExerciseRepository.countStudentParticipationsByExerciseId(exercise.getId()) > 0) {
            throw new AccessForbiddenException(PARTICIPATIONS_MESSAGE);
        }
    }

    /**
     * Tells why a personal access token may not push to the repositories of an exercise: it is already visible to students, or has
     * participations, and a push would change work that exists. The rule is the one that applies to changing the exercise with a request.
     *
     * @param exerciseId  the id of the exercise
     * @param releaseDate the release date of the stored exercise
     * @param startDate   the start date of the stored exercise
     * @return the reason, or empty if the exercise is a draft without participations
     */
    public Optional<String> repositoryPushBlockedReason(long exerciseId, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate) {
        Optional<String> published = AgentDraftRules.publishedReason(releaseDate, startDate);
        if (published.isPresent()) {
            return published;
        }
        if (programmingExerciseRepository.countStudentParticipationsByExerciseId(exerciseId) > 0) {
            return Optional.of(PARTICIPATIONS_MESSAGE);
        }
        return Optional.empty();
    }

    /**
     * Requires a token request that does not change dates to change only a draft exercise without student participations.
     *
     * @param exercise the stored exercise
     * @throws AccessForbiddenException if the exercise is published or has student participations
     */
    public void requireDraftForTokenRequest(ProgrammingExercise exercise) {
        requireDraftForTokenRequest(exercise, exercise.getReleaseDate(), exercise.getStartDate());
    }
}
