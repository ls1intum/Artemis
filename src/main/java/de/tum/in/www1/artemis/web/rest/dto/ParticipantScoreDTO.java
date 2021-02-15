package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TeamScore;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ParticipantScoreDTO {

    public Long id;

    public Long userId;

    public String userName;

    public Long teamId;

    public String teamName;

    public Long exerciseId;

    public String exerciseTitle;

    public Long lastResultId;

    public Long lastResultScore;

    public Long lastRatedResultId;

    public Long lastRatedResultScore;

    public ParticipantScoreDTO(Long id, Long userId, String userName, Long teamId, String teamName, Long exerciseId, String exerciseTitle, Long lastResultId, Long lastResultScore,
            Long lastRatedResultId, Long lastRatedResultScore) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.teamId = teamId;
        this.teamName = teamName;
        this.exerciseId = exerciseId;
        this.exerciseTitle = exerciseTitle;
        this.lastResultId = lastResultId;
        this.lastResultScore = lastResultScore;
        this.lastRatedResultId = lastRatedResultId;
        this.lastRatedResultScore = lastRatedResultScore;
    }

    public ParticipantScoreDTO() {
    }

    /**
     * Generates a {@link ParticipantScoreDTO} from a {@link ParticipantScore}
     *
     * @param participantScore ParticipantScore input
     * @return {@link ParticipantScoreDTO}
     */
    public static ParticipantScoreDTO generateFromParticipantScore(ParticipantScore participantScore) {
        String userName = null;
        Long userId = null;
        String teamName = null;
        Long teamId = null;

        if (participantScore.getClass().equals(StudentScore.class)) {
            StudentScore studentScore = (StudentScore) participantScore;
            userName = studentScore.getUser() != null && studentScore.getUser().getLogin() != null ? studentScore.getUser().getLogin() : null;
            userId = studentScore.getUser() != null ? studentScore.getUser().getId() : null;
        }
        else {
            TeamScore teamScore = (TeamScore) participantScore;
            teamName = teamScore.getTeam() != null && teamScore.getTeam().getName() != null ? teamScore.getTeam().getName() : null;
            teamId = teamScore.getTeam() != null ? teamScore.getTeam().getId() : null;
        }

        Long id = participantScore.getId();
        String exerciseTitle = participantScore.getExercise() != null && participantScore.getExercise().getTitle() != null ? participantScore.getExercise().getTitle() : null;
        Long exerciseId = participantScore.getExercise() != null ? participantScore.getExercise().getId() : null;
        Long lastResultId = participantScore.getLastResult() != null ? participantScore.getLastResult().getId() : null;
        Long lastResultScore = participantScore.getLastScore();
        Long lastRatedResultId = participantScore.getLastRatedResult() != null ? participantScore.getLastRatedResult().getId() : null;
        Long lastRatedResultScore = participantScore.getLastRatedScore();

        return new ParticipantScoreDTO(id, userId, userName, teamId, teamName, exerciseId, exerciseTitle, lastResultId, lastResultScore, lastRatedResultId, lastRatedResultScore);
    }
}
