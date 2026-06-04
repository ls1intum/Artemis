import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { IrisQuizTimerDTO } from 'app/iris/shared/entities/iris-quiz-timer-dto.model';

@Injectable({ providedIn: 'root' })
export class IrisAssessmentQuizService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/iris/assessment-quiz';

    /**
     * registers a tab-defocus event while quiz is active and ends the quiz
     * @param exerciseId The unique identifier of the exercise
     */
    registerDefocusForCurrentSession(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${exerciseId}/defocus`, {}, { observe: 'response' });
    }

    /**
     * starts the timer for the current quiz session
     * @param exerciseId The unique identifier of the exercise
     */
    startTimer(exerciseId: number): Observable<HttpResponse<IrisQuizTimerDTO>> {
        return this.http.patch<IrisQuizTimerDTO>(`${this.resourceUrl}/${exerciseId}/start-timer`, {}, { observe: 'response' });
    }

    /**
     * stops the timer for the current quiz session
     * @param exerciseId The unique identifier of the exercise
     */
    stopTimer(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${exerciseId}/stop-timer`, {}, { observe: 'response' });
    }
}
