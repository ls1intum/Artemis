import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisQuizTimerDTO } from 'app/iris/shared/entities/iris-quiz-timer-dto.model';

export type IrisAskUserQuizType = 'regular' | 'inClass';

@Injectable({ providedIn: 'root' })
export class IrisAskUserHttpService {
    private http = inject(HttpClient);
    public resourceUrl = 'api/iris/programming-exercises';

    /**
     * Checks whether the in-class quiz been started already.
     *
     * @param exerciseId The unique identifier of the exercise
     */
    currentStartedInClassQuizForExercise(exerciseId: number): Observable<boolean> {
        return this.http.get<boolean>(`${this.resourceUrl}/${exerciseId}/ask-user/in-class/is-quiz-started`);
    }

    /**
     * Checks whether the quiz been started already.
     *
     * @param exerciseId The unique identifier of the exercise
     */
    currentStartedQuizForExercise(exerciseId: number): Observable<boolean> {
        return this.http.get<boolean>(`${this.resourceUrl}/${exerciseId}/ask-user/is-quiz-started`);
    }

    /**
     * registers a tab-defocus event while quiz is active and ends the quiz
     * @param exerciseId The unique identifier of the exercise
     */
    registerDefocusForCurrentSession(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${exerciseId}/ask-user/defocus`, {}, { observe: 'response' });
    }

    /**
     * starts the timer for the current quiz session
     * @param exerciseId The unique identifier of the exercise
     */
    startTimer(exerciseId: number): Observable<HttpResponse<IrisQuizTimerDTO>> {
        return this.http.patch<IrisQuizTimerDTO>(`${this.resourceUrl}/${exerciseId}/ask-user/start-timer`, {}, { observe: 'response' });
    }

    /**
     * starts the editor-controlled in-class quiz for the exercise for a student
     * @param exerciseId The unique identifier of the exercise
     * @return returns DTO with undefined timerExpired if no timer was started (because quiz was just stopped by something else)
     */
    startInClassQuiz(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${exerciseId}/ask-user/in-class/start`, null, { observe: 'response' });
    }

    /**
     * starts the quiz for the exercise for a student
     * @param exerciseId The unique identifier of the exercise
     */
    startQuiz(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${exerciseId}/ask-user/start`, null, { observe: 'response' });
    }

    latestSubmissionHasPoints(exerciseId: number): Observable<boolean> {
        return this.http.get<boolean>(`${this.resourceUrl}/${exerciseId}/ask-user/latest-submission-has-points`);
    }

    isQuizAlreadyDone(exerciseId: number, inClass: boolean): Observable<boolean> {
        return this.http.get<boolean>(`${this.resourceUrl}/${exerciseId}/ask-user/completed`, { params: { inClass } });
    }

    /**
     * stops the timer for the current quiz session
     * @param exerciseId The unique identifier of the exercise
     */
    stopTimer(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${exerciseId}/ask-user/stop-timer`, {}, { observe: 'response' });
    }
}
