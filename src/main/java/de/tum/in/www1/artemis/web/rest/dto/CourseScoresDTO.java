package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseScoresDTO(double maxPoints, double reachablePoints, List<StudentScore> studentScores) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record StudentScore(Long studentId, double absolutePoints, double relativeScore, double currentRelativeScore, int presentationScore,
            PlagiarismVerdict mostSeverePlagiarismVerdict) {
    }

    public Map<Long, BonusSourceResultDTO> toBonusSourceResultMap() {
        return studentScores.stream().collect(
                Collectors.toMap(StudentScore::studentId, studentScore -> new BonusSourceResultDTO(studentScore.absolutePoints(), studentScore.mostSeverePlagiarismVerdict())));
    }
}
