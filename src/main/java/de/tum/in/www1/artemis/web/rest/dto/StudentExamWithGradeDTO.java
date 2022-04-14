package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.GradeType;
import de.tum.in.www1.artemis.domain.exam.StudentExam;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StudentExamWithGradeDTO {

    public Integer maxPoints;

    public GradeType gradeType;

    public StudentExam studentExam;

    public ExamScoresDTO.StudentResult studentResult;

    /**
     * Empty constructor is needed by Jackson
     */
    public StudentExamWithGradeDTO() {
    }

    public StudentExamWithGradeDTO(Integer maxPoints, GradeType gradeType, StudentExam studentExam, ExamScoresDTO.StudentResult studentResult) {
        this.maxPoints = maxPoints;
        this.gradeType = gradeType;
        this.studentExam = studentExam;
        this.studentResult = studentResult;
    }
}
