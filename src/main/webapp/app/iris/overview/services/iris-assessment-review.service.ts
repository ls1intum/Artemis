import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { QAExchangeDTO } from 'app/iris/shared/entities/iris-qa-exchange-dto.model';
import { IrisAssessment } from 'app/iris/shared/entities/iris-assessment.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';

@Injectable({ providedIn: 'root' })
export class IrisAssessmentReviewService {
    private http = inject(HttpClient);
    private participationService = inject(ParticipationService);

    public resourceUrl = 'api/iris/assessments';
    public programmingExerciseResourceUrl = 'api/iris/programming-exercises';

    /**
     * accepts the answers of the last prompting mode chat and makes the submission points count
     * @param assessmentId The unique identifier of the assessment
     */
    acceptAnswers(assessmentId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${assessmentId}/accept`, {}, { observe: 'response' });
    }

    /**
     * rejects the answers of the last prompting mode chat and makes the submission points NOT count
     * @param assessmentId The unique identifier of the assessment
     */
    rejectAnswers(assessmentId: number): Observable<HttpResponse<void>> {
        return this.http.patch<void>(`${this.resourceUrl}/${assessmentId}/reject`, {}, { observe: 'response' });
    }

    /**
     * gets the QAExchange objects of the last prompting mode chat
     * @param assessmentId The unique identifier of the assessment
     */
    getAssessmentChat(assessmentId: number): Observable<HttpResponse<QAExchangeDTO[]>> {
        return this.http.get<QAExchangeDTO[]>(`${this.resourceUrl}/${assessmentId}/chat`, { observe: 'response' });
    }

    findWithPoints(assessmentId: number): Observable<HttpResponse<IrisAssessment>> {
        return this.http.get<IrisAssessment>(`${this.resourceUrl}/${assessmentId}`, { observe: 'response' });
    }

    findAllParticipationsNonZeroLatestScoreByProgrammingExercise(exerciseId: number): Observable<HttpResponse<ProgrammingExerciseStudentParticipation[]>> {
        return this.http
            .get<ProgrammingExerciseStudentParticipation[]>(`${this.programmingExerciseResourceUrl}/${exerciseId}/participations/non-zero-latest-score`, {
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<ProgrammingExerciseStudentParticipation[]>) => this.participationService.processParticipationEntityArrayResponseType(res)));
    }
}
