import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { MathNode } from 'app/math/shared/entities/math-node.model';
import { HintSuggestion } from 'app/math/shared/entities/hint-suggestion.model';

export type EntityResponseType = HttpResponse<MathSubmission>;

@Injectable({ providedIn: 'root' })
export class MathSubmissionService {
    private http = inject(HttpClient);
    private submissionService = inject(SubmissionService);

    getDataForMathEditor(participationId: number): Observable<EntityResponseType> {
        return this.http
            .get<MathSubmission>(`api/math/participations/${participationId}/math-editor`, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    create(mathSubmission: MathSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.submissionService.convert(mathSubmission);
        return this.http
            .post<MathSubmission>(`api/math/exercises/${exerciseId}/math-submissions`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    update(mathSubmission: MathSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.submissionService.convert(mathSubmission);
        return this.http
            .put<MathSubmission>(`api/math/exercises/${exerciseId}/math-submissions`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    getMathSubmission(submissionId: number): Observable<MathSubmission> {
        return this.http
            .get<MathSubmission>(`api/math/math-submissions/${submissionId}`, {
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<MathSubmission>) => res.body!));
    }

    getMathSubmissionForAssessment(submissionId: number): Observable<MathSubmission> {
        return this.http
            .get<MathSubmission>(`api/math/math-submissions/${submissionId}/for-assessment`, {
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<MathSubmission>) => res.body!));
    }

    getSubmittedSubmissions(exerciseId: number): Observable<MathSubmission[]> {
        return this.http
            .get<MathSubmission[]>(`api/math/exercises/${exerciseId}/math-submissions`, { observe: 'response' })
            .pipe(map((res: HttpResponse<MathSubmission[]>) => res.body ?? []));
    }

    saveManualResult(submissionId: number, score: number): Observable<MathSubmission> {
        return this.http
            .put<MathSubmission>(`api/math/math-submissions/${submissionId}/manual-result`, score, { observe: 'response' })
            .pipe(map((res: HttpResponse<MathSubmission>) => res.body!));
    }

    /** Asks the backend for ranked next-step suggestions at the current math state. */
    getHints(exerciseId: number, currentExpression: MathNode): Observable<HintSuggestion[]> {
        return this.http
            .post<HintSuggestion[]>(`api/math/exercises/${exerciseId}/hints`, { currentExpression }, { observe: 'response' })
            .pipe(map((res: HttpResponse<HintSuggestion[]>) => res.body ?? []));
    }
}
