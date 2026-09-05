import type { QuizExerciseWithQuestionsForStudent } from './quiz-exercise-with-questions-for-student';
import type { QuizExerciseWithoutQuestionsForStudent } from './quiz-exercise-without-questions-for-student';
import type { QuizExerciseWithSolutionForStudent } from './quiz-exercise-with-solution-for-student';

export type QuizExerciseForStudentResponse = QuizExerciseWithoutQuestionsForStudent | QuizExerciseWithQuestionsForStudent | QuizExerciseWithSolutionForStudent;
