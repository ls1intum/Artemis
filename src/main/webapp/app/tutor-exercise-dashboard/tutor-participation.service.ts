import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { TutorParticipation } from 'app/entities/tutor-participation';
import { SERVER_API_URL } from 'app/app.constants';
import { ExampleSubmission } from 'app/entities/example-submission/example-submission.model';

export type EntityResponseType = HttpResponse<TutorParticipation>;
export type EntityArrayResponseType = HttpResponse<TutorParticipation[]>;

@Injectable({ providedIn: 'root' })
export class TutorParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/exercises';

    constructor(private http: HttpClient) {}

    /**
     * Starts the exercise with the given ID for the current tutor. A tutor participation will be created and returned
     * for the exercise given by the exercise id. The tutor participation status will be assigned based on which
     * features are available for the exercise (e.g. grading instructions) The method is valid only for tutors,
     * since it inits the tutor participation to the exercise, which is different from a standard participation
     *
     * @param tutorParticipation The to be created tutor participation
     * @param exerciseId The ID of the exercise for which to init a participation
     * @return The new tutor participation
     */
    create(tutorParticipation: TutorParticipation, exerciseId: number): Observable<HttpResponse<TutorParticipation>> {
        return this.http.post<TutorParticipation>(`${this.resourceUrl}/${exerciseId}/tutorParticipations`, tutorParticipation, {
            observe: 'response',
        });
    }

    /**
     * Add an example submission to the tutor participation of the given exercise. If it is just for review (not used for tutorial),
     * the method just records that the tutor has read it. If it is a tutorial, the method checks if the assessment given by the tutor is close enough to the instructor one. If
     * yes, then it returns the participation, if not, it returns an error
     *
     * @param exampleSubmission The to be added example submission
     * @param exerciseId The ID of the exercise of the tutor participation
     */
    assessExampleSubmission(exampleSubmission: ExampleSubmission, exerciseId: number) {
        return this.http.post<TutorParticipation>(`${this.resourceUrl}/${exerciseId}/exampleSubmission`, exampleSubmission, { observe: 'response' });
    }
}
