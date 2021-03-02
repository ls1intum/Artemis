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

    /**
     * Check if due date of exercise has passed OR if the exam is over.
     * An exam is over if all the last possible end date (e.g., extension) has passed.
     *
     * @param exercise Exercise in question
     * @return exercise has ended
     */
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
