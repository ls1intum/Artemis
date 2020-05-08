import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import { Result } from 'app/entities/result.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';

export interface IProgrammingExerciseParticipationService {
    getLatestResultWithFeedback: (participationId: number) => Observable<Result | null>;
    getStudentParticipationWithLatestResult: (participationId: number) => Observable<ProgrammingExerciseStudentParticipation>;
    checkIfParticipationHasResult: (participationId: number) => Observable<boolean>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseParticipationService implements IProgrammingExerciseParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercise-participations/';

    constructor(private http: HttpClient) {}

    /**
     * Gets the latest result and feedback for the participation in this exercise from the HTTP client.
     * @param {number} participationId - Id of the participation
     */
    getLatestResultWithFeedback(participationId: number): Observable<Result | null> {
        return this.http.get<Result | null>(this.resourceUrl + participationId + '/latest-result-with-feedbacks');
    }

    /**
     * Gets the student participation with the latest result and feedback from the HTTP client.
     * @param {number} participationId - Id of the participation
     */
    getStudentParticipationWithLatestResult(participationId: number) {
        return this.http.get<ProgrammingExerciseStudentParticipation>(this.resourceUrl + participationId + '/student-participation-with-latest-result-and-feedbacks');
    }

    /**
     * Checks if the participation has a result
     * @param {number} participationId - Id of the participation
     */
    checkIfParticipationHasResult(participationId: number): Observable<boolean> {
        return this.http.get<boolean>(this.resourceUrl + participationId + '/has-result');
    }
}
