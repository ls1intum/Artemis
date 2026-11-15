import { convertDateFromClient } from 'app/foundation/util/date.utils';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { toCategoryStrings, toCompetencyLinks } from 'app/quiz/shared/util/quiz-exercise-creation-request.util';
import { toQuizQuestionRequest } from 'app/quiz/shared/util/quiz-question-request.util';
import { UpdateQuizExercise } from 'app/openapi/model/update-quiz-exercise';

export function toUpdateQuizExercise(quizExercise: QuizExercise): UpdateQuizExercise {
    return {
        title: quizExercise.title,
        channelName: quizExercise.channelName,
        categories: toCategoryStrings(quizExercise.categories),
        competencyLinks: toCompetencyLinks(quizExercise.competencyLinks) ?? [],
        difficulty: quizExercise.difficulty,
        duration: quizExercise.duration,
        randomizeQuestionOrder: quizExercise.randomizeQuestionOrder,
        quizMode: quizExercise.quizMode,
        quizBatches: quizExercise.quizBatches?.map((batch) => ({ id: batch.id, startTime: convertDateFromClient(batch.startTime), password: batch.password })),
        releaseDate: convertDateFromClient(quizExercise.releaseDate),
        startDate: convertDateFromClient(quizExercise.startDate),
        dueDate: convertDateFromClient(quizExercise.dueDate),
        includedInOverallScore: quizExercise.includedInOverallScore,
        // Saved parts keep their ids; parts added in the editor are referenced by their client-side temporary ids.
        quizQuestions: quizExercise.quizQuestions?.map((question) => toQuizQuestionRequest(question, (part) => part.id)),
    };
}
