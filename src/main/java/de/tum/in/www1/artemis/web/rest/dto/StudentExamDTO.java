package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;
import java.util.Objects;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.exam.StudentExam;

public class StudentExamDTO {

    public Long id;

    public ExamDTO exam;

    public List<Exercise> exercises;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ExamDTO getExam() {
        return exam;
    }

    public void setExam(ExamDTO exam) {
        this.exam = exam;
    }

    public List<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(List<Exercise> exercises) {
        this.exercises = exercises;
    }

    public static StudentExamDTO createFromEntity(StudentExam studentExam) {
        StudentExamDTO studentExamDTO = new StudentExamDTO();
        studentExamDTO.setId(studentExam.getId());
        studentExamDTO.setExam(ExamDTO.createFromEntity(studentExam.getExam()));
        studentExamDTO.setExercises(studentExam.getExercises());
        return studentExamDTO;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        StudentExamDTO that = (StudentExamDTO) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "StudentExamDTO{" + "id=" + id + ", exam=" + exam + ", exercises=" + exercises + '}';
    }
}
