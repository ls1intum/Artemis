import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizBatch, QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import dayjs from 'dayjs/esm';
import { CompetencyLinkDTO } from 'app/exercise/shared/exercise-update-shared-dto.model';
import { QuizQuestionCreateDTO, convertQuizQuestionsToDTOs } from './quiz-exercise-creation/quiz-question-creation-dto.model';

export interface QuizExerciseUpdateDTO {
    title?: string;
    channelName?: string;
    categories?: ExerciseCategory[];
    competencyLinks?: CompetencyLinkDTO[];
    difficulty?: DifficultyLevel;
    duration?: number;
    randomizeQuestionOrder?: boolean;
    quizMode?: QuizMode;
    quizBatches?: QuizBatch[];
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    includedInOverallScore?: IncludedInOverallScore;
    quizQuestions?: QuizQuestionCreateDTO[];
}

export function toQuizExerciseUpdateDTO(quizExercise: QuizExercise): QuizExerciseUpdateDTO {
    return {
        title: quizExercise.title,
        channelName: quizExercise.channelName,
        categories: quizExercise.categories,
        competencyLinks: (quizExercise.competencyLinks ?? []).map((link) => ({
            competency: { id: link.competency!.id! },
            weight: link.weight ?? 1,
        })),
        difficulty: quizExercise.difficulty,
        duration: quizExercise.duration,
        randomizeQuestionOrder: quizExercise.randomizeQuestionOrder,
        quizMode: quizExercise.quizMode,
        quizBatches: quizExercise.quizBatches,
        releaseDate: quizExercise.releaseDate,
        startDate: quizExercise.startDate,
        dueDate: quizExercise.dueDate,
        includedInOverallScore: quizExercise.includedInOverallScore,
        quizQuestions: quizExercise.quizQuestions ? convertQuizQuestionsToDTOs(quizExercise.quizQuestions) : undefined,
    };
}
