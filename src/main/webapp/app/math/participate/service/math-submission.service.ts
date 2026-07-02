import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { SubmissionService } from 'app/exercise/submission/submission.service';

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
            .pipe(
                map((res: HttpResponse<MathSubmission>) => {
                    if (!res.body) {
                        throw new Error('Empty response body for getMathSubmission');
                    }
                    return res.body;
                }),
            );
    }

    getSubmittedSubmissions(exerciseId: number): Observable<MathSubmission[]> {
        return this.http
            .get<MathSubmission[]>(`api/math/exercises/${exerciseId}/math-submissions`, { observe: 'response' })
            .pipe(map((res: HttpResponse<MathSubmission[]>) => res.body ?? []));
    }
}
