package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseScoresForExamBonusSourceDTO(double maxPoints, double reachablePoints, Integer presentationScoreThreshold,
        List<StudentScoresForExamBonusSourceDTO> studentScores) {

    public Map<Long, BonusSourceResultDTO> toBonusSourceResultMap() {
        return studentScores.stream()
                .collect(Collectors.toMap(StudentScoresForExamBonusSourceDTO::studentId,
                        studentScore -> new BonusSourceResultDTO(studentScore.presentationScorePassed() ? studentScore.absoluteScore() : 0.0,
                                studentScore.mostSeverePlagiarismVerdict(), studentScore.presentationScore(), presentationScoreThreshold, studentScore.hasParticipated())));
    }
}
