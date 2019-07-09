import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';

import { ExerciseService } from 'app/entities/exercise';
import { Result, ResultService } from 'app/entities/result';
import { Participation } from 'app/entities/participation';
import { isSolutionParticipation, isStudentParticipation, isTemplateParticipation } from 'app/entities/programming-exercise/utils/programming-exercise.utils';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseParticipationService {
    public resourceUrlSolution = SERVER_API_URL + 'api/programming-exercises-solution-participation/';
    public resourceUrlTemplate = SERVER_API_URL + 'api/programming-exercises-template-participation/';

    constructor(private http: HttpClient, private exerciseService: ExerciseService, private resultService: ResultService) {}

    getLatestResultWithFeedback(participation: Participation): Observable<Result | null> {
        if (isStudentParticipation(participation)) {
            return this.resultService.getLatestResultWithFeedbacks(participation.id).pipe(map(({ body }: HttpResponse<Result>) => body));
        } else if (isTemplateParticipation(participation)) {
            return this.getLatestResultWithFeedbackForTemplateParticipation(participation.id);
        } else if (isSolutionParticipation(participation)) {
            return this.getLatestResultWithFeedbackForSolutionParticipation(participation.id);
        }
        return of(null);
    }

    getLatestResultWithFeedbackForSolutionParticipation(participationId: number): Observable<Result | null> {
        return this.http.get<Result>(this.resourceUrlSolution + participationId + '/latest-result-with-feedbacks');
    }

    getLatestResultWithFeedbackForTemplateParticipation(participationId: number): Observable<Result | null> {
        return this.http.get<Result>(this.resourceUrlTemplate + participationId + '/latest-result-with-feedbacks');
    }
}
