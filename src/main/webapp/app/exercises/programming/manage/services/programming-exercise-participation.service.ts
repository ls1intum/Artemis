import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { createRequestOption } from 'app/shared/util/request-util';
import { SERVER_API_URL } from 'app/app.constants';
import { Result } from 'app/entities/result.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { addUserIndependentRepositoryUrl } from 'app/overview/participation-utils';

export interface IProgrammingExerciseParticipationService {
    getLatestResultWithFeedback: (participationId: number, withSubmission: boolean) => Observable<Result | undefined>;
    getStudentParticipationWithLatestResult: (participationId: number) => Observable<ProgrammingExerciseStudentParticipation>;
    checkIfParticipationHasResult: (participationId: number) => Observable<boolean>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseParticipationService implements IProgrammingExerciseParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercise-participations/';

    constructor(private http: HttpClient) {}

    getLatestResultWithFeedback(participationId: number, withSubmission = false): Observable<Result | undefined> {
        const options = createRequestOption({ withSubmission });
        return this.http.get<Result | undefined>(this.resourceUrl + participationId + '/latest-result-with-feedbacks', { params: options });
    }

    getStudentParticipationWithLatestResult(participationId: number) {
        return this.http.get<ProgrammingExerciseStudentParticipation>(this.resourceUrl + participationId + '/student-participation-with-latest-result-and-feedbacks');
    }

    getStudentParticipationWithResultOfCorrectionRound(participationId: number, correctionRound: number) {
        return this.http
            .get<ProgrammingExerciseStudentParticipation>(
                this.resourceUrl + participationId + '/student-participation-with-result-and-feedbacks-for/' + correctionRound + '/correction-round',
            )
            .map((participation: ProgrammingExerciseStudentParticipation) => {
                addUserIndependentRepositoryUrl(participation);
                return participation;
            });
    }

    checkIfParticipationHasResult(participationId: number): Observable<boolean> {
        return this.http.get<boolean>(this.resourceUrl + participationId + '/has-result');
    }
}
