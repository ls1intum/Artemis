package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.service.exam.ExamDateService;

@Service
public class ExerciseDateService {

    private final ExamDateService examDateService;

    public ExerciseDateService(ExamDateService examDateService) {
        this.examDateService = examDateService;
    }

    public boolean hasEnded(Exercise exercise) {
        if (!exercise.isExamExercise()) {
            return exercise.isEnded();
        }
        else {
            Exam exam = exercise.getExerciseGroup().getExam();
            return examDateService.isExamOver(exam);
        }
    }
}
