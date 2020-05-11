import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { Franc, FrancLanguage } from './franc';
import { Language } from 'app/entities/tutor-group.model';
import { Rating } from 'app/entities/rating.model';
import { Feedback } from 'app/entities/feedback.model';

export type EntityArrayResponseType = HttpResponse<Rating[]>;

@Injectable({ providedIn: 'root' })
export class TextEditorService {
    readonly franc: Franc = require('franc-min');

    constructor(private http: HttpClient) {}

    get(id: number): Observable<any> {
        return this.http.get(`api/text-editor/${id}`, { responseType: 'json' });
    }

    /**
     * Takes a text and returns it's language
     * @param   {String} text
     *
     * @returns {Language} language of the text
     */
    predictLanguage(text: string): Language | null {
        const languageProbabilities = this.franc.all(text);

        switch (languageProbabilities[0][0]) {
            case FrancLanguage.ENGLISH:
                return Language.ENGLISH;

            case FrancLanguage.GERMAN:
                return Language.GERMAN;

            case FrancLanguage.UNDEFINED:
            default:
                return null;
        }
    }

    /**
     * Update the student rating for feedback on the server.
     * @param feedbackId - Feedback that is rated by the student
     * @param rating - Rating for The Feedback
     */
    setRating(feedbackId: number, newRating: number) {
        return this.http.post(`api/text-editor/${feedbackId}/rating/${newRating}`, { responseType: 'json' });
    }

    /**
     * Get rating for
     * @param feedbackId
     */
    getRating(feedbackIds: number[]): Observable<[{ id: number; rating: number; feedback: Feedback }]> {
        return this.http.get<[{ id: number; rating: number; feedback: Feedback }]>('api/text-editor/rating/', { params: { feedbackIds: feedbackIds.join(', ') } });
    }
}
