import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { TutorParticipation } from 'app/entities/tutor-participation';
import { SERVER_API_URL } from 'app/app.constants';
import { ExampleSubmission } from 'app/entities/example-submission';

export type EntityResponseType = HttpResponse<TutorParticipation>;
export type EntityArrayResponseType = HttpResponse<TutorParticipation[]>;

@Injectable({ providedIn: 'root' })
export class TutorParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/exercises';

    constructor(private http: HttpClient) {}

    create(tutorParticipation: TutorParticipation, exerciseId: number): Observable<HttpResponse<TutorParticipation>> {
        return this.http.post<TutorParticipation>(`${this.resourceUrl}/${exerciseId}/tutorParticipations`, tutorParticipation, {
            observe: 'response'
        });
    }

    assessExampleSubmission(exampleSubmission: ExampleSubmission, exerciseId: number) {
        return this.http.post<TutorParticipation>(
            `${this.resourceUrl}/${exerciseId}/exampleSubmission`,
            exampleSubmission,
            { observe: 'response' }
        );
    }
}
