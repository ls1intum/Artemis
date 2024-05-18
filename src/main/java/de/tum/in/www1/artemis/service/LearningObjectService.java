package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
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
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnitCompletion;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathNavigationObjectDto.LearningObjectType;

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

    public LearningObjectService(ParticipantScoreService participantScoreService, ExerciseRepository exerciseRepository, LectureUnitRepository lectureUnitRepository) {
        this.participantScoreService = participantScoreService;
        this.exerciseRepository = exerciseRepository;
        this.lectureUnitRepository = lectureUnitRepository;
    }

    /**
     * Checks if the user has completed the learning object.
     *
     * @param learningObject the lecture unit or exercise
     * @param user           the user for which to check the completion status
     * @return true if the user completed the lecture unit or has at least one result for the exercise, false otherwise
     */
    public boolean isCompletedByUser(@NotNull LearningObject learningObject, @NotNull User user) {
        if (learningObject instanceof LectureUnit lectureUnit) {
            return lectureUnit.getCompletedUsers().stream().map(LectureUnitCompletion::getUser).anyMatch(user1 -> user1.getId().equals(user.getId()));
        }
        else if (learningObject instanceof Exercise exercise) {
            return participantScoreService.getStudentAndTeamParticipations(user, Set.of(exercise)).findAny().isPresent();
        }
        throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
    }

    /**
     * Get the last completed learning object of the given competencies related to the given date.
     *
     * @param user         the user for which to get the last completed learning object
     * @param relatedDate  the date to which the completion date of the learning object should be related
     * @param competencies the competencies for which to get the last completed learning object
     * @return the last completed learning object of the given competencies related to the given date
     */
    public Optional<LearningObject> getCompletedPredecessorOfLearningObjectRelatedToDate(User user, Optional<ZonedDateTime> relatedDate, Set<Competency> competencies) {
        return getCompletedLearningObjectsForUserAndCompetencies(user, competencies)
                .filter(learningObject -> learningObject.getCompletionDate(user).orElseThrow().isBefore(relatedDate.orElse(ZonedDateTime.now())))
                .max(Comparator.comparing(o -> o.getCompletionDate(user).orElseThrow()));
    }

    /**
     * Get the next completed learning object of the given competencies related to the given date.
     *
     * @param user         the user for which to get the next completed learning object
     * @param relatedDate  the date to which the completion date of the learning object should be related
     * @param competencies the competencies for which to get the next completed learning object
     * @return the next completed learning object of the given competencies related to the given date
     */
    public Optional<LearningObject> getCompletedSuccessorOfLearningObjectRelatedToDate(User user, Optional<ZonedDateTime> relatedDate, Set<Competency> competencies) {
        if (relatedDate.isEmpty()) {
            throw new RuntimeException("relatedDate must be present to get next completed learning object.");
        }
        return getCompletedLearningObjectsForUserAndCompetencies(user, competencies)
                .filter(learningObject -> learningObject.getCompletionDate(user).orElseThrow().isAfter(relatedDate.get()))
                .min(Comparator.comparing(o -> o.getCompletionDate(user).orElseThrow()));
    }

    /**
     * Get the completed learning objects for the given user and competencies.
     *
     * @param user         the user for which to get the completed learning objects
     * @param competencies the competencies for which to get the completed learning objects
     * @return the completed learning objects for the given user and competencies
     */
    public Stream<LearningObject> getCompletedLearningObjectsForUserAndCompetencies(User user, Set<Competency> competencies) {
        return Stream.concat(competencies.stream().map(Competency::getLectureUnits), competencies.stream().map(Competency::getExercises)).flatMap(Set::stream)
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
}
