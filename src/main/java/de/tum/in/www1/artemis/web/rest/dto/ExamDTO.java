package de.tum.in.www1.artemis.web.rest.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ExerciseGroup;

public class ExamDTO implements Serializable {

    public ExamDTO(Long id, Course course, Set<ExerciseGroup> exerciseGroups, ZonedDateTime releaseDate, ZonedDateTime dueDate, String startText, String endText,
            String confirmationStartText, String confirmationEndText) {
        this.id = id;
        this.course = course;
        this.exerciseGroups = exerciseGroups;
        this.releaseDate = releaseDate;
        this.dueDate = dueDate;
        this.startText = startText;
        this.endText = endText;
        this.confirmationStartText = confirmationStartText;
        this.confirmationEndText = confirmationEndText;
    }

    public Long id;

    public Course course;

    public Set<ExerciseGroup> exerciseGroups;

    public ZonedDateTime releaseDate;

    public ZonedDateTime dueDate;

    public String startText;

    public String endText;

    public String confirmationStartText;

    public String confirmationEndText;
}
