import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { IncludedInOverallScore } from 'app/entities/exercise.model';

export interface QuizConfiguration {
    id?: number;
    quizQuestions?: QuizQuestion[];
    randomizeQuestionOrder?: boolean;
    title?: string;
    maxPoints?: number;
    includedInOverallScore?: IncludedInOverallScore;
}
