import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { LongFeedbackText } from 'app/entities/long-feedback-text.model';
import { Observable } from 'rxjs';

export type LongFeedbackResponse = HttpResponse<LongFeedbackText>;

@Injectable({ providedIn: 'root' })
export class LongFeedbackService {
    constructor(protected http: HttpClient) {}

    find(resultId: number, feedbackId: number): Observable<LongFeedbackResponse> {
        return this.http.get<LongFeedbackText>(`${SERVER_API_URL}api/results/${resultId}/feedbacks/${feedbackId}/long-feedback`, { observe: 'response' });
    }
}
