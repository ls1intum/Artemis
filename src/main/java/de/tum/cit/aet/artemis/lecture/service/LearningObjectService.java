package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MIN_SCORE_GREEN;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreService;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationObjectDTO.LearningObjectType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

/**
 * Service implementation for interactions with learning objects.
 * <p>
 * The interface {@code LearningObject} is implemented by all {@code LectureUnit}s and {@code Exercise}s.
 *
 * @see LearningObject
 * @see LectureUnit
 * @see Exercise
 */
@Profile(PROFILE_CORE)
@Service
public class LearningObjectService {

    private final ParticipantScoreService participantScoreService;

    private final ExerciseRepository exerciseRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final SubmissionRepository submissionRepository;

    public LearningObjectService(ParticipantScoreService participantScoreService, ExerciseRepository exerciseRepository, LectureUnitRepository lectureUnitRepository,
            LectureUnitCompletionRepository lectureUnitCompletionRepository, SubmissionRepository submissionRepository) {
        this.participantScoreService = participantScoreService;
        this.exerciseRepository = exerciseRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Checks if the user has completed the learning object.
     *
     * @param learningObject the lecture unit or exercise
     * @param user           the user for which to check the completion status
     * @return true if the user completed the lecture unit or has at least one result for the exercise, false otherwise
     */
    public boolean isCompletedByUser(@NotNull LearningObject learningObject, @NotNull User user) {
        return switch (learningObject) {
            case LectureUnit lectureUnit -> lectureUnit.isCompletedFor(user);
            case Exercise exercise -> isCompletedByUser(exercise, user);
            default -> throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
        };
    }

    /**
     * Checks if the user has completed the exercise.
     * For manually assessed exercises the user cannot get more feedback usually, so we have to assume all submissions as completed for now
     * For other exercises the user can get more assessments and achieve high enough scores to surpass the {@link MIN_SCORE_GREEN} threshold
     *
     * @param exercise the exercise
     * @param user     the user for which to check the completion status
     * @return true if the user has completed the exercise
     */
    private boolean isCompletedByUser(Exercise exercise, User user) {
        if (exercise.getAssessmentType() == AssessmentType.MANUAL) {
            return submissionRepository.existsByExerciseIdAndParticipantIdAndSubmitted(exercise.getId(), user.getId());
        }
        else {
            return participantScoreService.getStudentAndTeamParticipations(user, Set.of(exercise)).anyMatch(score -> score.getLastScore() >= MIN_SCORE_GREEN);
        }
    }

    /**
     * Get learning object by id and type.
     *
     * @param learningObjectId   the id of the learning object
     * @param learningObjectType the type of the learning object
     * @return the learning object with the given id and type
     */
    public LearningObject getLearningObjectByIdAndType(Long learningObjectId, LearningObjectType learningObjectType) {
        return switch (learningObjectType) {
            case LECTURE -> lectureUnitRepository.findByIdWithCompletedUsersElseThrow(learningObjectId);
            case EXERCISE -> exerciseRepository.findByIdWithStudentParticipationsElseThrow(learningObjectId);
            case null -> throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
        };
    }

    /**
     * Set the lecture unit completions for the given lecture units and user.
     *
     * @param lectureUnits the lecture units for which to set the completions
     * @param user         the user for which to set the completions
     */
    public void setLectureUnitCompletions(Set<LectureUnit> lectureUnits, User user) {
        Set<LectureUnitCompletion> lectureUnitCompletions = lectureUnitCompletionRepository.findByLectureUnitsAndUserId(lectureUnits, user.getId());
        lectureUnits.forEach(lectureUnit -> {
            Optional<LectureUnitCompletion> completion = lectureUnitCompletions.stream().filter(lectureUnitCompletion -> lectureUnitCompletion.getLectureUnit().equals(lectureUnit))
                    .findFirst();
            lectureUnit.setCompletedUsers(completion.map(Set::of).orElse(Set.of()));
        });
    }
}
