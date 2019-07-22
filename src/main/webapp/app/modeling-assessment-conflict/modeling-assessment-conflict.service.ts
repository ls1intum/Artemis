import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';

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
        return this.http.get<Conflict>(`${this.resourceUrl}/model-assessment-conflicts/${conflictId}`);
    }

    getConflictsForSubmission(submissionID: number): Observable<Conflict[]> {
        return this.http.get<Conflict[]>(`${this.resourceUrl}/modeling-submissions/${submissionID}/model-assessment-conflicts`).map(conflicts => this.convertConflicts(conflicts));
    }

    getConflictsForResultInConflict(resultId: number): Observable<Conflict[]> {
        return this.http.get<Conflict[]>(`${this.resourceUrl}/results/${resultId}/model-assessment-conflicts`).map(conflicts => this.convertConflicts(conflicts));
    }

    escalateConflict(conflicts: Conflict[]): Observable<Conflict> {
        return this.http.put<Conflict>(`${this.resourceUrl}/model-assessment-conflicts/escalate`, conflicts);
    }

    updateConflicts(conflicts: Conflict[]): Observable<Conflict> {
        return this.http.put<Conflict>(`${this.resourceUrl}/model-assessment-conflicts`, conflicts);
    }

    convertConflicts(conflicts: Conflict[]) {
        conflicts.forEach((conflict: Conflict) => {
            this.modelAssessmentService.convertResult(conflict.causingConflictingResult.result);
            conflict.resultsInConflict.forEach((conflictingResult: ConflictingResult) => this.modelAssessmentService.convertResult(conflictingResult.result));
        });
        return conflicts;
    }
}
