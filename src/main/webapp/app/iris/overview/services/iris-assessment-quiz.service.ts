import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, map, tap } from 'rxjs';
import { IrisQuizTimerDTO } from 'app/iris/shared/entities/iris-quiz-timer-dto.model';
import { IrisInClassQuizDTO } from 'app/iris/shared/entities/iris-in-class-quiz-dto.model';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import dayjs from 'dayjs/esm';

@Injectable({ providedIn: 'root' })
export class IrisAssessmentQuizService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/iris/programming-exercises';
    private readonly activeInClassQuizState = new BehaviorSubject<ReadonlyMap<number, IrisInClassQuizDTO>>(new Map());
    private readonly startedInClassQuizState = new BehaviorSubject<ReadonlySet<number>>(new Set());

    currentInClassQuizForExercise(exerciseId: number): Observable<IrisInClassQuizDTO | undefined> {
        return this.activeInClassQuizState.pipe(
            map((activeInClassQuizzes) => {
                const activeInClassQuiz = activeInClassQuizzes.get(exerciseId);

                if (activeInClassQuiz?.timerExpiresAt.isAfter(dayjs())) {
                    return activeInClassQuiz;
                }

                return undefined;
            }),
        );
    }

    currentStartedInClassQuizForExercise(exerciseId: number): Observable<boolean> {
        return this.startedInClassQuizState.pipe(map((startedInClassQuizzes) => startedInClassQuizzes.has(exerciseId)));
    }

    /**
     * registers a tab-defocus event while quiz is active and ends the quiz
     * @param exerciseId The unique identifier of the exercise
     */
    registerDefocusForCurrentSession(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${exerciseId}/assessment-quiz/defocus`, {}, { observe: 'response' });
    }

    /**
     * starts the timer for the current quiz session
     * @param exerciseId The unique identifier of the exercise
     */
    startTimer(exerciseId: number): Observable<HttpResponse<IrisQuizTimerDTO>> {
        return this.http.patch<IrisQuizTimerDTO>(`${this.resourceUrl}/${exerciseId}/assessment-quiz/start-timer`, {}, { observe: 'response' });
    }

    /**
     * starts the instructor-controlled in-class quiz window for the exercise
     * @param exerciseId The unique identifier of the exercise
     */
    startInClassQuiz(exerciseId: number): Observable<HttpResponse<IrisInClassQuizDTO>> {
        return this.http.patch<IrisInClassQuizDTO>(`${this.resourceUrl}/${exerciseId}/assessment-quiz/in-class/start`, {}, { observe: 'response' }).pipe(
            map((response) => this.convertInClassQuizResponseFromServer(response)),
            tap((response) => this.setActiveInClassQuiz(exerciseId, response.body ?? undefined)),
        );
    }

    /**
     * gets the active instructor-controlled in-class quiz window for the exercise
     * @param exerciseId The unique identifier of the exercise
     */
    getActiveInClassQuiz(exerciseId: number): Observable<HttpResponse<IrisInClassQuizDTO>> {
        return this.http.get<IrisInClassQuizDTO>(`${this.resourceUrl}/${exerciseId}/assessment-quiz/in-class`, { observe: 'response' }).pipe(
            map((response) => this.convertInClassQuizResponseFromServer(response)),
            tap((response) => this.setActiveInClassQuiz(exerciseId, response.body ?? undefined)),
        );
    }

    latestSubmissionHasPoints(exerciseId: number): Observable<boolean> {
        return this.http.get<boolean>(`${this.resourceUrl}/${exerciseId}/assessment-quiz/latest-submission-has-points`);
    }

    isQuizAlreadyDone(exerciseId: number, inClass: boolean): Observable<boolean> {
        return this.http.get<boolean>(`${this.resourceUrl}/${exerciseId}/assessment-quiz/completed`, { params: { inClass } });
    }

    clearActiveInClassQuiz(exerciseId: number): void {
        this.setActiveInClassQuiz(exerciseId, undefined);
    }

    setInClassPromptingModeStarted(exerciseId: number, started: boolean): void {
        const startedInClassQuizzes = new Set(this.startedInClassQuizState.value);

        if (started) {
            startedInClassQuizzes.add(exerciseId);
        } else {
            startedInClassQuizzes.delete(exerciseId);
        }

        this.startedInClassQuizState.next(startedInClassQuizzes);
    }

    /**
     * stops the timer for the current quiz session
     * @param exerciseId The unique identifier of the exercise
     */
    stopTimer(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${exerciseId}/assessment-quiz/stop-timer`, {}, { observe: 'response' });
    }

    private convertInClassQuizResponseFromServer(response: HttpResponse<IrisInClassQuizDTO>): HttpResponse<IrisInClassQuizDTO> {
        if (!response.body) {
            return response;
        }

        const timerExpiresAt = convertDateFromServer(response.body.timerExpiresAt)!;

        return response.clone({
            body: {
                ...response.body,
                timerExpiresAt,
                timeLimit: response.body.timeLimit ?? Math.max(timerExpiresAt.diff(dayjs(), 'second'), 0),
            },
        });
    }

    private setActiveInClassQuiz(exerciseId: number, inClassQuiz: IrisInClassQuizDTO | undefined): void {
        const activeInClassQuizzes = new Map(this.activeInClassQuizState.value);

        if (inClassQuiz?.timerExpiresAt.isAfter(dayjs())) {
            activeInClassQuizzes.set(exerciseId, inClassQuiz);
        } else {
            activeInClassQuizzes.delete(exerciseId);
        }

        this.activeInClassQuizState.next(activeInClassQuizzes);
    }
}
