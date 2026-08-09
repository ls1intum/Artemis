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
     * Returns the score input for one exercise-type breakdown. Graded presentation points are course-wide and therefore
     * excluded, while basic presentation scores remain attributable to the type of their participation.
     *
     * @param presentationScoreCount the positive basic presentation scores on participations of the selected type
     * @return this student's input for one exercise type
     */
    public StudentCourseScoreInputDTO forExerciseType(long presentationScoreCount) {
        return new StudentCourseScoreInputDTO(studentId, gradeScores, plagiarismCases, 0.0, presentationScoreCount);
    }
}
