package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.plagiarism.api.dtos.PlagiarismCaseScoreDTO;

/**
 * Everything specific to one student that is needed to calculate their course score.
 *
 * @param studentId                   the student whose score is calculated
 * @param gradeScores                 the student's projected, relevant results
 * @param plagiarismCases             the student's projected plagiarism verdicts
 * @param gradedPresentationScoreSum  the sum used by the weighted presentation scheme
 * @param basicPresentationScoreCount the number of positive presentation scores used by the basic presentation scheme
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentCourseScoreInputDTO(long studentId, Collection<CourseGradeScoreDTO> gradeScores, Collection<PlagiarismCaseScoreDTO> plagiarismCases,
        double gradedPresentationScoreSum, long basicPresentationScoreCount) {

    public StudentCourseScoreInputDTO {
        gradeScores = List.copyOf(gradeScores);
        plagiarismCases = List.copyOf(plagiarismCases);
    }

    /**
     * Returns the same score inputs without presentation contributions. Score breakdowns per exercise type use this,
     * because presentations only contribute to the total course score.
     *
     * @return this student's inputs with both presentation values set to zero
     */
    public StudentCourseScoreInputDTO withoutPresentations() {
        return new StudentCourseScoreInputDTO(studentId, gradeScores, plagiarismCases, 0.0, 0);
    }
}
