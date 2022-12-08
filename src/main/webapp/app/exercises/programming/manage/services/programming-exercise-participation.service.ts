import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { createRequestOption } from 'app/shared/util/request.util';
import { Result } from 'app/entities/result.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';
import { Participation } from 'app/entities/participation/participation.model';

export interface IProgrammingExerciseParticipationService {
    getLatestResultWithFeedback: (participationId: number, withSubmission: boolean) => Observable<Result | undefined>;
    getStudentParticipationWithLatestResult: (participationId: number) => Observable<ProgrammingExerciseStudentParticipation>;
    checkIfParticipationHasResult: (participationId: number) => Observable<boolean>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseParticipationService implements IProgrammingExerciseParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercise-participations/';

    constructor(private http: HttpClient, private entityTitleService: EntityTitleService) {}

    getLatestResultWithFeedback(participationId: number, withSubmission = false): Observable<Result | undefined> {
        const options = createRequestOption({ withSubmission });
        return this.http
            .get<Result | undefined>(this.resourceUrl + participationId + '/latest-result-with-feedbacks', { params: options })
            .pipe(tap((res) => this.sendTitlesToEntityTitleService(res?.participation)));
    }

    getStudentParticipationWithLatestResult(participationId: number) {
        return this.http
            .get<ProgrammingExerciseStudentParticipation>(this.resourceUrl + participationId + '/student-participation-with-latest-result-and-feedbacks')
            .pipe(tap((res) => this.sendTitlesToEntityTitleService(res)));
    }

    checkIfParticipationHasResult(participationId: number): Observable<boolean> {
        return this.http.get<boolean>(this.resourceUrl + participationId + '/has-result');
    }

    resetRepository(participationId: number, gradedParticipationId?: number) {
        let params = new HttpParams();
        if (gradedParticipationId) {
            params = params.set('gradedParticipationId', gradedParticipationId.toString());
        }
        return this.http.put<void>(`${this.resourceUrl}${participationId}/reset-repository`, null, { observe: 'response', params });
    }

    sendTitlesToEntityTitleService(participation: Participation | undefined) {
        if (participation?.exercise) {
            const exercise = participation.exercise;
            this.entityTitleService.setTitle(EntityType.EXERCISE, [exercise.id], exercise.title);

            if (exercise.course) {
                const course = exercise.course;
                this.entityTitleService.setTitle(EntityType.COURSE, [course.id], course.title);
            }
        }
    }
}
