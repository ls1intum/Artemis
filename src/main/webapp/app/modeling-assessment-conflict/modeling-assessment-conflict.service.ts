import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';
import { map } from 'rxjs/operators';
import { Feedback } from 'app/entities/feedback';

@Injectable({
    providedIn: 'root',
})
export class ModelingAssessmentConflictService {
    private localSubmissionConflictMap: Map<number, Conflict[]>;
    private resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient, private modelAssessmentService: ModelingAssessmentService) {
        this.localSubmissionConflictMap = new Map<number, Conflict[]>();
    }

    addLocalConflicts(submissionID: number, conflicts: Conflict[]) {
        return this.localSubmissionConflictMap.set(submissionID, conflicts);
    }

    popLocalConflicts(submissionID: number): Conflict[] {
        const conflicts = this.localSubmissionConflictMap.get(submissionID);
        this.localSubmissionConflictMap.delete(submissionID);
        if (conflicts) {
            return conflicts;
        } else {
            return [];
        }
    }

    getConflict(conflictId: number): Observable<Conflict> {
        return this.http.get<Conflict>(`${this.resourceUrl}/model-assessment-conflicts/${conflictId}`).pipe(map(conflict => this.convertConflict(conflict)));
    }

    getConflictsForSubmission(submissionID: number): Observable<Conflict[]> {
        return this.http.get<Conflict[]>(`${this.resourceUrl}/modeling-submissions/${submissionID}/model-assessment-conflicts`);
    }

    getConflictsForResultInConflict(resultId: number): Observable<Conflict[]> {
        return this.http.get<Conflict[]>(`${this.resourceUrl}/results/${resultId}/model-assessment-conflicts`).pipe(map(conflicts => this.convertConflicts(conflicts)));
    }

    escalateConflict(conflicts: Conflict[]): Observable<Conflict> {
        return this.http.put<Conflict>(`${this.resourceUrl}/model-assessment-conflicts/escalate`, conflicts);
    }

    updateConflicts(conflicts: Conflict[]): Observable<Conflict> {
        return this.http.put<Conflict>(`${this.resourceUrl}/model-assessment-conflicts`, conflicts);
    }

    resolveConflict(conflict: Conflict, decision: Feedback): Observable<any> {
        return this.http.put(`${this.resourceUrl}/model-assessment-conflicts/${conflict.id}/resolve `, decision);
    }

    convertConflicts(conflicts: Conflict[]): Conflict[] {
        conflicts.forEach((conflict: Conflict) => this.convertConflict(conflict));
        return conflicts;
    }

    private convertConflict(conflict: Conflict): Conflict {
        this.modelAssessmentService.convertResult(conflict.causingConflictingResult.result);
        conflict.resultsInConflict.forEach((conflictingResult: ConflictingResult) => this.modelAssessmentService.convertResult(conflictingResult.result));
        return conflict;
    }
}
