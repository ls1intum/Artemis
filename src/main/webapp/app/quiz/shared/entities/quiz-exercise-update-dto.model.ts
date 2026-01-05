import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizBatch, QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import dayjs from 'dayjs/esm';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';

export interface QuizExerciseUpdateDTO {
    title?: string;
    channelName?: string;
    categories?: ExerciseCategory[];
    competencyLinks?: CompetencyExerciseLinkUpdateDTO[];
    difficulty?: DifficultyLevel;
    duration?: number;
    randomizeQuestionOrder?: boolean;
    quizMode?: QuizMode;
    quizBatches?: QuizBatch[];
    releaseDate?: dayjs.Dayjs;
    startDate?: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    includedInOverallScore?: IncludedInOverallScore;
    quizQuestions?: QuizQuestion[];
}

interface CompetencyExerciseLinkUpdateDTO {
    competencyId?: number;
    weight?: number;
}

function toCompetencyExerciseLinkUpdateDTO(link: CompetencyExerciseLink): CompetencyExerciseLinkUpdateDTO {
    return {
        competencyId: link.competency?.id,
        weight: link.weight,
    };
}

export function toQuizExerciseUpdateDTO(quizExercise: QuizExercise): QuizExerciseUpdateDTO {
    return {
        title: quizExercise.title,
        channelName: quizExercise.channelName,
        categories: quizExercise.categories,
        competencyLinks: quizExercise.competencyLinks?.map(toCompetencyExerciseLinkUpdateDTO) || [],
        difficulty: quizExercise.difficulty,
        duration: quizExercise.duration,
        randomizeQuestionOrder: quizExercise.randomizeQuestionOrder,
        quizMode: quizExercise.quizMode,
        quizBatches: quizExercise.quizBatches,
        releaseDate: quizExercise.releaseDate,
        startDate: quizExercise.startDate,
        dueDate: quizExercise.dueDate,
        includedInOverallScore: quizExercise.includedInOverallScore,
        quizQuestions: quizExercise.quizQuestions,
    };
}
