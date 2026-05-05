import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { QAExchangeDTO } from 'app/iris/shared/entities/iris-qa-exchange-dto.model';

@Injectable({ providedIn: 'root' })
export class IrisAssessmentReviewService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/iris/assessment-review';

    /**
     * accepts the answers of the last prompting mode chat and makes the submission points count
     * @param participationId The unique identifier of the participation
     */
    acceptAnswers(participationId: number): Observable<HttpResponse<StudentParticipation>> {
        return this.http.patch<StudentParticipation>(`${this.resourceUrl}/${participationId}/accept`, {}, { observe: 'response' });
    }

    /**
     * rejects the answers of the last prompting mode chat and makes the submission points NOT count
     * @param participationId The unique identifier of the participation
     */
    rejectAnswers(participationId: number): Observable<HttpResponse<StudentParticipation>> {
        return this.http.patch<StudentParticipation>(`${this.resourceUrl}/${participationId}/reject`, {}, { observe: 'response' });
    }

    /**
     * gets the QAExchange objects of the last prompting mode chat
     * @param participationId The unique identifier of the participation
     */
    getAssessmentChat(participationId: number): Observable<HttpResponse<QAExchangeDTO[]>> {
        return this.http.get<QAExchangeDTO[]>(`${this.resourceUrl}/${participationId}`, { observe: 'response' });
    }
}
