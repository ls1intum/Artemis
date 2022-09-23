package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseScoresDTO(double maxPoints, double reachablePoints, Integer presentationScoreThreshold, List<StudentScore> studentScores) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StudentScore(Long studentId, double absolutePoints, double relativeScore, double currentRelativeScore, int achievedPresentationScore,
            boolean presentationScorePassed, PlagiarismVerdict mostSeverePlagiarismVerdict) {

        public double getAbsolutePointsEligibleForBonus() {
            return presentationScorePassed ? absolutePoints : 0.0;
        }
    }

    public Map<Long, BonusSourceResultDTO> toBonusSourceResultMap() {
        return studentScores.stream().collect(Collectors.toMap(StudentScore::studentId, studentScore -> new BonusSourceResultDTO(studentScore.getAbsolutePointsEligibleForBonus(),
                studentScore.mostSeverePlagiarismVerdict(), studentScore.achievedPresentationScore(), presentationScoreThreshold)));
    }
}
