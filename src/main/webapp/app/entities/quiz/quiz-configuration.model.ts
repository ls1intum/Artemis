import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { IncludedInOverallScore } from 'app/exercise/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

export interface QuizConfiguration {
    id?: number;
    exerciseGroup?: ExerciseGroup;
    quizQuestions?: QuizQuestion[];
    randomizeQuestionOrder?: boolean;
    title?: string;
    maxPoints?: number;
    includedInOverallScore?: IncludedInOverallScore;
}
