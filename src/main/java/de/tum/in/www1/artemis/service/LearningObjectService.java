package de.tum.in.www1.artemis.service;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LearningObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnitCompletion;

/**
 * Service implementation for interactions with learning objects.
 * <p>
 * The interface {@code LearningObject} is implemented by all {@code LectureUnit}s and {@code Exercise}s.
 *
 * @see LearningObject
 * @see LectureUnit
 * @see Exercise
 */
@Service
public class LearningObjectService {

    private final ParticipantScoreService participantScoreService;

    public LearningObjectService(ParticipantScoreService participantScoreService) {
        this.participantScoreService = participantScoreService;
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
}
