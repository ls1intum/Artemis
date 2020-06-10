package de.tum.in.www1.artemis.web.rest.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;

public class ExamDTO implements Serializable {

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
