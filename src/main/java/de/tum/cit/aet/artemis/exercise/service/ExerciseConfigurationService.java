package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.repository.PlagiarismDetectionConfigRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;

/**
 * Gives every newly stored exercise its permanent configuration rows.
 * <p>
 * An exercise owns exactly one team assignment configuration and one plagiarism detection configuration, whatever its type
 * and whether or not it is a team exercise. Both hold the key to the exercise, so they can only be inserted once the exercise
 * is stored, and every path that stores a new exercise (creation, every kind of import, copies) calls {@link #initialize}
 * right after it. Later changes only update the rows in place; nothing here is called when a setting changes.
 * <p>
 * No transaction spans the calls: each is one simple repository statement, and the inserts are idempotent so that a path
 * which ran them already is not an error.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseConfigurationService {

    private final TeamAssignmentConfigRepository teamAssignmentConfigRepository;

    private final PlagiarismDetectionConfigRepository plagiarismDetectionConfigRepository;

    public ExerciseConfigurationService(TeamAssignmentConfigRepository teamAssignmentConfigRepository, PlagiarismDetectionConfigRepository plagiarismDetectionConfigRepository) {
        this.teamAssignmentConfigRepository = teamAssignmentConfigRepository;
        this.plagiarismDetectionConfigRepository = plagiarismDetectionConfigRepository;
    }

    /**
     * Creates the default configuration rows of a newly stored exercise and leaves them on the exercise's slots.
     *
     * @param stored the exercise that was just stored
     */
    public void initialize(Exercise stored) {
        initialize(stored, null, null);
    }

    /**
     * Creates the default configuration rows of a newly stored exercise, applies the settings the creating request carried to
     * them in place, and leaves the stored rows on the exercise's slots.
     *
     * @param stored              the exercise that was just stored
     * @param requestedTeam       the team settings the request carried, or null for the defaults
     * @param requestedPlagiarism the plagiarism detection settings the request carried, or null for the defaults
     */
    public void initialize(Exercise stored, @Nullable TeamAssignmentConfig requestedTeam, @Nullable PlagiarismDetectionConfig requestedPlagiarism) {
        teamAssignmentConfigRepository.initializeFor(stored, requestedTeam);
        plagiarismDetectionConfigRepository.initializeFor(stored, requestedPlagiarism);
    }

    /**
     * Reads the stored team settings of an exercise onto its slot; the exercise is left without them if it has none.
     *
     * @param exercise the exercise, which does not carry its settings after being loaded
     */
    public void attachTeamAssignmentConfig(Exercise exercise) {
        teamAssignmentConfigRepository.attachTo(exercise);
    }

    /**
     * Reads the stored plagiarism detection settings of an exercise onto its slot; the exercise is left without them if it
     * has none.
     *
     * @param exercise the exercise, which does not carry its settings after being loaded
     */
    public void attachPlagiarismDetectionConfig(Exercise exercise) {
        plagiarismDetectionConfigRepository.attachTo(exercise);
    }
}
