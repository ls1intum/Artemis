import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TextBlockRef } from 'app/entities/text-block-ref.model';

type FeedbackSuggestionsResponseType = HttpResponse<TextBlockRef[]>;

@Injectable({ providedIn: 'root' })
export class AthenaService {
    public resourceUrl = 'api/athena';

    constructor(protected http: HttpClient) {}

    /**
     * Get feedback suggestions for the given submission from Athena
     *
     * @param exerciseId the id of the exercise
     * @param submissionId the id of the submission
     */
    getFeedbackSuggestions(exerciseId: number, submissionId: number): Observable<FeedbackSuggestionsResponseType> {
        return this.http.get<TextBlockRef[]>(`${this.resourceUrl}/exercises/${exerciseId}/submissions/${submissionId}/feedback-suggestions`, { observe: 'response' });
    }
}
