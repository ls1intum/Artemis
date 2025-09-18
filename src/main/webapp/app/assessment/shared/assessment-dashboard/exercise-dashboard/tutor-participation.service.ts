import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { TutorParticipation, TutorParticipationDTO } from 'app/exercise/shared/entities/participation/tutor-participation.model';

export type EntityResponseType = HttpResponse<TutorParticipationDTO>;
export type EntityArrayResponseType = HttpResponse<TutorParticipation[]>;

@Injectable({ providedIn: 'root' })
export class TutorParticipationService {
    private http = inject(HttpClient);
    public resourceUrl = 'api/assessment/exercises';

    /**
     * Starts the exercise with the given ID for the current tutor. A tutor participation dto will be created and returned
     * for the exercise given by the exercise id. The tutor participation status will be assigned based on which
     * features are available for the exercise (e.g. grading instructions) The method is valid only for tutors,
     * since it inits the tutor participation to the exercise, which is different from a standard participation
     *
     * @param exerciseId The ID of the exercise for which to init a participation dto
     * @return The new tutor participation dto
     */
    create(exerciseId: number): Observable<EntityResponseType> {
        return this.http.post<TutorParticipationDTO>(`${this.resourceUrl}/${exerciseId}/tutor-participations`, null, { observe: 'response' });
    }

    /**
     * Add an example submission to the tutor participation of the given exercise. If it is just for review (not used for tutorial),
     * the method just records that the tutor has read it. If it is a tutorial, the method checks if the assessment given by the tutor is close enough to the instructor one. If
     * yes, then it returns the participation, if not, it returns an error
     *
     * @param exampleSubmission The to be added example submission
     * @param exerciseId The ID of the exercise of the tutor participation
     */
    assessExampleSubmission(exampleSubmission: ExampleSubmission, exerciseId: number): Observable<EntityResponseType> {
        return this.http.post<TutorParticipationDTO>(`${this.resourceUrl}/${exerciseId}/assess-example-submission`, exampleSubmission, { observe: 'response' });
    }
}
