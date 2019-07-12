import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import { Result } from 'app/entities/result';
import { Participation } from 'app/entities/participation';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises-participation/';

    constructor(private http: HttpClient) {}

    getStudentParticipationWithLatestResult(participationId: number) {
        return this.http.get<Participation>(this.resourceUrl + participationId + '/student-participation-with-latest-result-and-feedbacks');
    }

    getLatestResultWithFeedback(participationId: number): Observable<Result> {
        return this.http.get<Result>(this.resourceUrl + participationId + '/latest-result-with-feedbacks');
    }
}
