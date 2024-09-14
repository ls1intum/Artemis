package de.tum.cit.aet.artemis.assessment.dto.score;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

/**
 * DTO to send achieved points and achieved scores of a student to the client for courses / exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScoreDTO(long studentId, String studentLogin, double pointsAchieved, double scoreAchieved, double regularPointsAchievable) {

    public static ScoreDTO of(User student, double pointsAchieved, double scoreAchieved, double regularPointsAchievable) {
        return new ScoreDTO(student.getId(), student.getLogin(), pointsAchieved, scoreAchieved, regularPointsAchievable);
    }
}
