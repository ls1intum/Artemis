import { Service, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizExerciseApi } from 'app/openapi/api/quiz-exercise-api';
import { QuizExerciseDates } from 'app/openapi/model/quiz-exercise-dates';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { deepClone } from 'app/foundation/util/deep-clone.util';

/**
 * Whether the quiz can be started now: a synchronized quiz whose batch has not started, whether students can already see it (waiting room open) or not.
 * The status says exactly that. The server's `quizStarted` flag is not usable here, it only means "visible to students".
 */
export function canStartQuiz(quiz: QuizExercise): boolean {
    return (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.INVISIBLE) && quiz.quizMode === QuizMode.SYNCHRONIZED && !!quiz.isAtLeastEditor;
}

/** Whether the quiz can be ended now: an individual or batched quiz that has not ended yet. */
export function canEndQuiz(quiz: QuizExercise): boolean {
    return (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.ACTIVE) && quiz.quizMode !== QuizMode.SYNCHRONIZED && !!quiz.isAtLeastInstructor && !quiz.quizEnded;
}

/** Whether the quiz can be made visible to students now. */
export function canSetQuizVisible(quiz: QuizExercise): boolean {
    return quiz.status === QuizStatus.INVISIBLE && !!quiz.isAtLeastEditor && !quiz.visibleToStudents;
}

/** Whether the batches of the quiz can be managed now. */
export function canManageQuizBatches(quiz: QuizExercise): boolean {
    return quiz.quizMode === QuizMode.BATCHED && (quiz.status === QuizStatus.VISIBLE || quiz.status === QuizStatus.ACTIVE);
}

/**
 * Starts, ends or publishes a quiz exercise immediately. Each action emits the updated copy of the quiz, so the caller can
 * show the new state without reloading it.
 */
@Service()
export class QuizExerciseLifecycleService {
    private readonly quizExerciseApi = inject(QuizExerciseApi);

    startQuiz(quiz: QuizExercise): Observable<QuizExercise> {
        return this.quizExerciseApi.performActionForQuizExercise(quiz.id!, 'start-now').pipe(
            map((dates) => {
                const updatedExercise = withDates(quiz, dates);
                updatedExercise.visibleToStudents = true;
                updatedExercise.status = QuizStatus.ACTIVE;
                const batches = updatedExercise.quizBatches ? [...updatedExercise.quizBatches] : [];
                if (batches.length > 0) {
                    const firstBatch = deepClone(batches[0]);
                    firstBatch.started = true;
                    firstBatch.startTime = updatedExercise.startDate;
                    batches[0] = firstBatch;
                } else {
                    batches.push({
                        started: true,
                        startTime: updatedExercise.startDate,
                    });
                }
                updatedExercise.quizBatches = batches;
                return updatedExercise;
            }),
        );
    }

    endQuiz(quiz: QuizExercise): Observable<QuizExercise> {
        return this.quizExerciseApi.endQuizExercise(quiz.id!).pipe(
            map((dates) => {
                const updatedExercise = withDates(quiz, dates);
                updatedExercise.quizEnded = true;
                return updatedExercise;
            }),
        );
    }

    setVisible(quiz: QuizExercise): Observable<QuizExercise> {
        return this.quizExerciseApi.performActionForQuizExercise(quiz.id!, 'set-visible').pipe(
            map((dates) => {
                const updatedExercise = withDates(quiz, dates);
                updatedExercise.visibleToStudents = true;
                return updatedExercise;
            }),
        );
    }
}

/** A copy of the quiz with the dates the server returned. */
function withDates(quiz: QuizExercise, dates: QuizExerciseDates): QuizExercise {
    const exercise = deepClone(quiz);
    exercise.releaseDate = convertDateStringFromServer(dates.releaseDate);
    exercise.startDate = convertDateStringFromServer(dates.startDate);
    exercise.dueDate = convertDateStringFromServer(dates.dueDate);
    return exercise;
}
