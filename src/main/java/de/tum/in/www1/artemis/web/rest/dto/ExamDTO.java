package de.tum.in.www1.artemis.web.rest.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exam;
import de.tum.in.www1.artemis.domain.ExerciseGroup;

public class ExamDTO implements Serializable {

    public ExamDTO(Exam exam) {
        this.id = exam.getId();
        this.course = exam.getCourse();
        this.exerciseGroups = exam.getExerciseGroups();
        this.startDate = exam.getStartDate();
        this.endDate = exam.getEndDate();
        this.visibleDate = exam.getVisibleDate();
        this.startText = exam.getStartText();
        this.endText = exam.getEndText();
        this.confirmationStartText = exam.getConfirmationStartText();
        this.confirmationEndText = exam.getConfirmationEndText();
    }

    public Long id;

    public Course course;

    public List<ExerciseGroup> exerciseGroups;

    public ZonedDateTime startDate;

    public ZonedDateTime endDate;

    public ZonedDateTime visibleDate;

    public String startText;

    public String endText;

    public String confirmationStartText;

    public String confirmationEndText;
}
