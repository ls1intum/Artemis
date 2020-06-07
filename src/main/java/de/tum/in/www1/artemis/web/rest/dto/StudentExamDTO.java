package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Set;

import de.tum.in.www1.artemis.domain.Exam;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;

public class StudentExamDTO {

    public Long id;

    public Exam exam;

    public User user;

    public Set<Exercise> exercises;

}
