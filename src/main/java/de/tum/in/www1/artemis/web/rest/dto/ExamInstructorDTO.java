package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;

public class ExamInstructorDTO {

    public Long id;

    public Course course;

    public Set<StudentExam> studentExams;

    public Set<ExerciseGroup> exerciseGroups;

    public Set<User> registeredUsers;

    public ZonedDateTime startDate;

    public ZonedDateTime endDate;

    public ZonedDateTime visibleDate;

    public String startText;

    public String endText;

    public String confirmationStartText;

    public String confirmationEndText;

    public Integer maxPoints;

    public Integer numberOfExercisesInExam;
}
