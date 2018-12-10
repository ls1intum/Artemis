import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Result } from 'src/main/webapp/app/entities/result';
import { Participation } from 'app/entities/participation';
import { Feedback } from 'app/entities/feedback';
import * as moment from 'moment';

type EntityResponseType = HttpResponse<Result>;

@Injectable({
    providedIn: 'root'
})
export class TextAssessmentsService {
    private readonly resourceUrl = SERVER_API_URL + 'api/text-assessments';

    constructor(private http: HttpClient) {}

    public save(textAssessments: Feedback[], exerciseId: number, resultId: number): Observable<EntityResponseType> {
        return this.http
            .put<Result>(`${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}`, textAssessments, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    public submit(textAssessments: Feedback[], exerciseId: number, resultId: number): Observable<EntityResponseType> {
        return this.http
            .put<Result>(`${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}/submit`, textAssessments, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    public getFeedbackDataForExerciseSubmission(exerciseId: number, submissionId: number): Observable<Participation> {
        return this.http.get<Participation>(`${this.resourceUrl}/exercise/${exerciseId}/submission/${submissionId}`);
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Result = this.convertItemFromServer(res.body);

        if (body.completionDate) {
            body.completionDate = moment(body.completionDate);
        }
        if (body.submission && body.submission.submissionDate) {
            body.submission.submissionDate = moment(body.submission.submissionDate);
        }
        if (body.participation && body.participation.initializationDate) {
            body.participation.initializationDate = moment(body.participation.initializationDate);
        }

        return res.clone({ body });
    }

    private convertItemFromServer(result: Result): Result {
        const copy: Result = Object.assign({}, result);
        return copy;
    }
}
