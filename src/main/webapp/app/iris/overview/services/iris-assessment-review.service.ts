import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { QAExchangeDTO } from 'app/iris/shared/entities/iris-qa-exchange-dto.model';
import { IrisAssessment } from 'app/iris/shared/entities/iris-assessment.model';
import { EntityResponseType } from 'app/exercise/participation/participation.service';

@Injectable({ providedIn: 'root' })
export class IrisAssessmentReviewService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/iris/assessment-review';

    /**
     * accepts the answers of the last prompting mode chat and makes the submission points count
     * @param assessmentId The unique identifier of the assessment
     */
    acceptAnswers(assessmentId: number): Observable<HttpResponse<IrisAssessment>> {
        return this.http.patch<StudentParticipation>(`${this.resourceUrl}/${assessmentId}/accept`, {}, { observe: 'response' });
    }

    /**
     * rejects the answers of the last prompting mode chat and makes the submission points NOT count
     * @param assessmentId The unique identifier of the assessment
     */
    rejectAnswers(assessmentId: number): Observable<HttpResponse<IrisAssessment>> {
        return this.http.patch<StudentParticipation>(`${this.resourceUrl}/${assessmentId}/reject`, {}, { observe: 'response' });
    }

    /**
     * gets the QAExchange objects of the last prompting mode chat
     * @param assessmentId The unique identifier of the assessment
     */
    getAssessmentChat(assessmentId: number): Observable<HttpResponse<QAExchangeDTO[]>> {
        return this.http.get<QAExchangeDTO[]>(`${this.resourceUrl}/${assessmentId}/chat`, { observe: 'response' });
    }

    findWithPoints(assessmentId: number): Observable<EntityResponseType> {
        return this.http.get<IrisAssessment>(`${this.resourceUrl}/${assessmentId}`, { observe: 'response' });
    }
}
