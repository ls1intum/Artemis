package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;

public class ExamInstructorDTO {

    public ExamInstructorDTO(Long id, Course course, Set<StudentExam> studentExams, Set<ExerciseGroup> exerciseGroups, ZonedDateTime releaseDate, ZonedDateTime dueDate,
            String startText, String endText, String confirmationStartText, String confirmationEndText, Long maxScore) {
        this.id = id;
        this.course = course;
        this.studentExams = studentExams;
        this.exerciseGroups = exerciseGroups;
        this.releaseDate = releaseDate;
        this.dueDate = dueDate;
        this.startText = startText;
        this.endText = endText;
        this.confirmationStartText = confirmationStartText;
        this.confirmationEndText = confirmationEndText;
        this.maxScore = maxScore;
    }

    public Long id;

    public Course course;

    public Set<StudentExam> studentExams;

    public Set<ExerciseGroup> exerciseGroups;

    public ZonedDateTime releaseDate;

    public ZonedDateTime dueDate;

    public String startText;

    public String endText;

    public String confirmationStartText;

    public String confirmationEndText;

    public Long maxScore;
}
