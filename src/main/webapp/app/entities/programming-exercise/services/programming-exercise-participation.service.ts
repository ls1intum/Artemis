import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';

import { ExerciseService } from 'app/entities/exercise';
import { Result } from 'app/entities/result';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseParticipationService {
    public resourceUrlSolution = SERVER_API_URL + 'api/programming-exercises-solution-participation/';
    public resourceUrlTemplate = SERVER_API_URL + 'api/programming-exercises-template-participation/';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    getLatestResultWithFeedbackForSolutionParticipation(participationId: number): Observable<Result | null> {
        return this.http.get(this.resourceUrlSolution + participationId + '/latest-result-with-feedbacks').pipe(map(({ body }: HttpResponse<Result>) => body));
    }

    getLatestResultWithFeedbackForTemplateParticipation(participationId: number): Observable<Result | null> {
        return this.http.get(this.resourceUrlTemplate + participationId + '/latest-result-with-feedbacks').pipe(map(({ body }: HttpResponse<Result>) => body));
    }
}
