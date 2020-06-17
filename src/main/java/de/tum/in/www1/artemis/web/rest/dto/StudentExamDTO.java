package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Set;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;

public class StudentExamDTO {

    public Long id;

    public Exam exam;

    public User user;

    public Set<Exercise> exercises;

}
