import dayjs from 'dayjs/esm';
import { DifficultyLevel, ExerciseMode, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestionCreateDTO, convertQuizQuestionsToDTOs } from 'app/quiz/shared/entities/quiz-exercise-creation/quiz-question-creation-dto.model';

export interface QuizExerciseCreationDTO {
    title: string;
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    difficulty?: DifficultyLevel;
    mode?: ExerciseMode;
    includedInOverallScore?: IncludedInOverallScore;
    competencyLinks?: CompetencyExerciseLink[];
    categories?: ExerciseCategory[];
    channelName?: string;
    randomizeQuestionOrder?: boolean;
    quizMode?: QuizMode;
    duration?: number;
    quizBatches?: QuizBatchCreationDTO[];
    quizQuestions?: QuizQuestionCreateDTO[];
}

export interface QuizBatchCreationDTO {
    startTime?: dayjs.Dayjs;
}

export function convertQuizExerciseToCreationDTO(exercise: QuizExercise): QuizExerciseCreationDTO {
    return {
        title: exercise.title ?? '',
        releaseDate: exercise.releaseDate,
        startDate: exercise.startDate,
        dueDate: exercise.dueDate,
        difficulty: exercise.difficulty,
        mode: exercise.mode,
        includedInOverallScore: exercise.includedInOverallScore,
        competencyLinks: exercise.competencyLinks ? [...exercise.competencyLinks] : undefined,
        categories: exercise.categories ? [...exercise.categories] : undefined,
        channelName: exercise.channelName,
        randomizeQuestionOrder: exercise.randomizeQuestionOrder ?? true,
        quizMode: exercise.quizMode,
        duration: exercise.duration ?? 0,
        quizBatches: exercise.quizBatches ? exercise.quizBatches.map((batch) => ({ startTime: batch.startTime })) : undefined,
        quizQuestions: exercise.quizQuestions ? convertQuizQuestionsToDTOs(exercise.quizQuestions) : undefined,
    };
}
