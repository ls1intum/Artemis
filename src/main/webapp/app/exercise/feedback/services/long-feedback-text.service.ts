import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

export type LongFeedbackResponse = HttpResponse<string>;

@Injectable({ providedIn: 'root' })
export class LongFeedbackTextService {
    private http = inject(HttpClient);

    find(feedbackId: number): Observable<LongFeedbackResponse> {
        return this.http.get(`api/assessment/feedbacks/${feedbackId}/long-feedback`, { observe: 'response', responseType: 'text' });
    }
}
