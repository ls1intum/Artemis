import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import { Result } from 'app/entities/result';
import { ProgrammingExerciseAgentParticipation } from 'app/entities/participation';

export interface IProgrammingExerciseParticipationService {
    getLatestResultWithFeedback: (participationId: number) => Observable<Result | null>;
    getAgentParticipationWithLatestResult: (participationId: number) => Observable<ProgrammingExerciseAgentParticipation>;
    checkIfParticipationHasResult: (participationId: number) => Observable<boolean>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseParticipationService implements IProgrammingExerciseParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercise-participations/';

    constructor(private http: HttpClient) {}

    getLatestResultWithFeedback(participationId: number): Observable<Result | null> {
        return this.http.get<Result | null>(this.resourceUrl + participationId + '/latest-result-with-feedbacks');
    }

    getAgentParticipationWithLatestResult(participationId: number) {
        return this.http.get<ProgrammingExerciseAgentParticipation>(this.resourceUrl + participationId + '/student-participation-with-latest-result-and-feedbacks');
    }

    checkIfParticipationHasResult(participationId: number): Observable<boolean> {
        return this.http.get<boolean>(this.resourceUrl + participationId + '/has-result');
    }
}
