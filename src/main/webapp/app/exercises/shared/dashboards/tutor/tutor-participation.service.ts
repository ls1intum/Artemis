import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { AccountService } from 'app/core/auth/account.service';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { TutorParticipation } from 'app/entities/participation/tutor-participation.model';

export type EntityResponseType = HttpResponse<TutorParticipation>;
export type EntityArrayResponseType = HttpResponse<TutorParticipation[]>;

@Injectable({ providedIn: 'root' })
export class TutorParticipationService {
    private http = inject(HttpClient);
    private accountService = inject(AccountService);

    public resourceUrl = 'api/exercises';

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
        return this.http.post<TutorParticipation>(`${this.resourceUrl}/${exerciseId}/tutor-participations`, tutorParticipation, {
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
    assessExampleSubmission(exampleSubmission: ExampleSubmission, exerciseId: number): Observable<HttpResponse<TutorParticipation>> {
        return this.http.post<TutorParticipation>(`${this.resourceUrl}/${exerciseId}/assess-example-submission`, exampleSubmission, { observe: 'response' });
    }

    /**
     * Deletes the tutor participation of the current user for the guided tour
     * @param course the course of the exercise
     * @param exercise  exercise with tutor participation
     */
    deleteTutorParticipationForGuidedTour(course: Course, exercise: Exercise): Observable<void> {
        if (course && this.accountService.isAtLeastTutorInCourse(course)) {
            return this.http.delete<void>(`api/guided-tour/exercises/${exercise.id}/example-submission`);
        }
        return new Observable();
    }
}
