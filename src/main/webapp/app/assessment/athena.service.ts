import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { TextBlock, TextBlockType } from 'app/entities/text-block.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';

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
        // TODO: Athena is not yet implemented
        // return mock data for now
        return new Observable<FeedbackSuggestionsResponseType>((subscriber) => {
            subscriber.next(
                new HttpResponse<TextBlockRef[]>({
                    body: [
                        new TextBlockRef(
                            {
                                id: null,
                                text: 'test',
                                startIndex: 5,
                                endIndex: 10,
                                submission: null,
                                type: TextBlockType.AUTOMATIC,
                            } as any as TextBlock,
                            {
                                id: 1,
                                detailText: 'First Feedback',
                                credits: 1,
                                reference: 'First text id',
                                type: FeedbackType.AUTOMATIC,
                            } as Feedback,
                        ),
                    ],
                }),
            );
            subscriber.complete();
        });
        //return this.http
        //    .get<TextBlockRef[]>(`${this.resourceUrl}/exercises/${exerciseId}/submissions/${submissionId}/feedback-suggestions`, { observe: 'response' });
    }
}
