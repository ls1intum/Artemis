package de.tum.in.www1.artemis.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.StudentQuestion;
import de.tum.in.www1.artemis.repository.StudentQuestionRepository;

@Service
@Transactional
public class StudentQuestionService {

    StudentQuestionRepository studentQuestionRepository;

    public StudentQuestionService(StudentQuestionRepository studentQuestionRepository) {
        this.studentQuestionRepository = studentQuestionRepository;
    }

    public List<StudentQuestion> findStudentQuestionsForExercise(Long exerciseId) {
        return studentQuestionRepository.findStudentQuestionsForExercise(exerciseId);
    }

    public List<StudentQuestion> findStudentQuestionsForLecture(Long lectureId) {
        return studentQuestionRepository.findStudentQuestionsForLecture(lectureId);
    }
}
