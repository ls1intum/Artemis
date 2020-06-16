package de.tum.in.www1.artemis.web.rest.dto;

import java.util.Set;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.exam.Exam;

public class ExerciseGroupDTO {

    public Long id;

    public String title;

    public Boolean isMandatory;

    public Exam exam;

    public Set<Exercise> exercises;

}
