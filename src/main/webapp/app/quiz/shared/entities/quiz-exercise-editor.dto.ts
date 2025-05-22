import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';

export default interface QuizExerciseEditorDTO {
    title?: string;
    channelName?: string;
    categories?: ExerciseCategory[];
    difficulty?: string;
    duration?: number;
    randomizeQuestionOrder?: boolean;
    quizMode?: string;
    releaseDate?: string;
    dueDate?: string;
    includedInOverallScore?: string;
    quizQuestions?: QuizQuestion[];
}
