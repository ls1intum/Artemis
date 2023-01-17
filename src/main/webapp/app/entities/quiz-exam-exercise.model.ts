import { ExamExercise } from 'app/entities/exam-exercise.model';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';

export interface QuizExamExercise extends ExamExercise {
    quizQuestions?: QuizQuestion[];
    randomizeQuestionOrder?: boolean;
    quizExam?: boolean;
}
