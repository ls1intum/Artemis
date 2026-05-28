import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ProofSubmission } from 'app/proof/shared/entities/proof-submission.model';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { MathNode } from 'app/proof/shared/entities/math-node.model';
import { HintSuggestion } from 'app/proof/shared/entities/hint-suggestion.model';

export type EntityResponseType = HttpResponse<ProofSubmission>;

@Injectable({ providedIn: 'root' })
export class ProofSubmissionService {
    private http = inject(HttpClient);
    private submissionService = inject(SubmissionService);

    getDataForProofEditor(participationId: number): Observable<EntityResponseType> {
        return this.http
            .get<ProofSubmission>(`api/proof/participations/${participationId}/proof-editor`, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    create(proofSubmission: ProofSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.submissionService.convert(proofSubmission);
        return this.http
            .post<ProofSubmission>(`api/proof/exercises/${exerciseId}/proof-submissions`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    update(proofSubmission: ProofSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.submissionService.convert(proofSubmission);
        return this.http
            .put<ProofSubmission>(`api/proof/exercises/${exerciseId}/proof-submissions`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    getProofSubmission(submissionId: number): Observable<ProofSubmission> {
        return this.http
            .get<ProofSubmission>(`api/proof/proof-submissions/${submissionId}`, {
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<ProofSubmission>) => res.body!));
    }

    getProofSubmissionForAssessment(submissionId: number): Observable<ProofSubmission> {
        return this.http
            .get<ProofSubmission>(`api/proof/proof-submissions/${submissionId}/for-assessment`, {
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<ProofSubmission>) => res.body!));
    }

    getSubmittedSubmissions(exerciseId: number): Observable<ProofSubmission[]> {
        return this.http
            .get<ProofSubmission[]>(`api/proof/exercises/${exerciseId}/proof-submissions`, { observe: 'response' })
            .pipe(map((res: HttpResponse<ProofSubmission[]>) => res.body ?? []));
    }

    saveManualResult(submissionId: number, score: number): Observable<ProofSubmission> {
        return this.http
            .put<ProofSubmission>(`api/proof/proof-submissions/${submissionId}/manual-result`, score, { observe: 'response' })
            .pipe(map((res: HttpResponse<ProofSubmission>) => res.body!));
    }

    /** Asks the backend for ranked next-step suggestions at the current proof state. */
    getHints(exerciseId: number, currentExpression: MathNode): Observable<HintSuggestion[]> {
        return this.http
            .post<HintSuggestion[]>(`api/proof/exercises/${exerciseId}/hints`, { currentExpression }, { observe: 'response' })
            .pipe(map((res: HttpResponse<HintSuggestion[]>) => res.body ?? []));
    }
}
