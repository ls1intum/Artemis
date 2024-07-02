package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.MIN_SCORE_GREEN;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnitCompletion;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureUnitCompletionRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationObjectDTO.LearningObjectType;

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

    public LearningObjectService(ParticipantScoreService participantScoreService, ExerciseRepository exerciseRepository, LectureUnitRepository lectureUnitRepository,
            LectureUnitCompletionRepository lectureUnitCompletionRepository) {
        this.participantScoreService = participantScoreService;
        this.exerciseRepository = exerciseRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
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
            case Exercise exercise -> participantScoreService.getStudentAndTeamParticipations(user, Set.of(exercise)).anyMatch(score -> score.getLastScore() >= MIN_SCORE_GREEN);
            default -> throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
        };
    }

    /**
     * Get the completed learning objects for the given user and competencies.
     *
     * @param user         the user for which to get the completed learning objects
     * @param competencies the competencies for which to get the completed learning objects
     * @return the completed learning objects for the given user and competencies
     */
    public Stream<LearningObject> getCompletedLearningObjectsForUserAndCompetencies(User user, Set<CourseCompetency> competencies) {
        return Stream.concat(competencies.stream().map(CourseCompetency::getLectureUnits), competencies.stream().map(CourseCompetency::getExercises)).flatMap(Set::stream)
                .filter(learningObject -> learningObject.getCompletionDate(user).isPresent())
                .sorted(Comparator.comparing(learningObject -> learningObject.getCompletionDate(user).orElseThrow())).map(LearningObject.class::cast);
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
