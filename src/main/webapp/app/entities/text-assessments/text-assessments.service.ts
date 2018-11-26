import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Result } from 'src/main/webapp/app/entities/result';
import { TextAssessment, EntityResponseType } from 'app/entities/text-assessments/text-assessments.model';
import { TextSubmission } from 'app/entities/text-submission';
import { TextExercise } from 'app/entities/text-exercise';

@Injectable({
    providedIn: 'root'
})
export class TextAssessmentsService {
    private readonly resourceUrl = SERVER_API_URL + 'api/text-assessments';

    constructor(private http: HttpClient) {}

    public save(textAssessments: TextAssessment[], exerciseId: number, resultId: number): Observable<EntityResponseType> {
        const requestBody = { assessments: textAssessments };
        return this.http
            .put<Result>(`${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}`, requestBody, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    public getFeedbackDataForExerciseSubmission(
        exerciseId: number,
        submissionId: number
    ): Observable<{ assessments: TextAssessment[]; submission: TextSubmission; exercise: TextExercise; result: Result }> {
        return this.http.get<{ assessments: any; submission: TextSubmission; exercise: TextExercise; result: Result }>(
            `${this.resourceUrl}/exercise/${exerciseId}/submission/${submissionId}`
        );
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Result = this.convertItemFromServer(res.body);
        return res.clone({ body });
    }

    private convertItemFromServer(result: Result): Result {
        const copy: Result = Object.assign({}, result);
        return copy;
    }
}
