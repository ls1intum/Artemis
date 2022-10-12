import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { TextSubmission } from 'app/entities/text-submission.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { stringifyCircular } from 'app/shared/util/utils';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';

export type EntityResponseType = HttpResponse<TextSubmission>;

@Injectable({ providedIn: 'root' })
export class TextSubmissionService {
    constructor(private http: HttpClient, private submissionService: SubmissionService) {}

    create(textSubmission: TextSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.submissionService.convert(textSubmission);
        return this.http
            .post<TextSubmission>(`api/exercises/${exerciseId}/text-submissions`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    update(textSubmission: TextSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.submissionService.convert(textSubmission);
        return this.http
            .put<TextSubmission>(`api/exercises/${exerciseId}/text-submissions`, stringifyCircular(copy), {
                headers: { 'Content-Type': 'application/json' },
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    getTextSubmission(submissionId: number): Observable<TextSubmission> {
        return this.http
            .get<TextSubmission>(`api/text-submissions/${submissionId}`, {
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<TextSubmission>) => res.body!));
    }

    getSubmissions(exerciseId: number, req: { submittedOnly?: boolean; assessedByTutor?: boolean }, correctionRound = 0): Observable<HttpResponse<TextSubmission[]>> {
        const url = `api/exercises/${exerciseId}/text-submissions`;
        let params = createRequestOption(req);
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }

        return this.http
            .get<TextSubmission[]>(url, { observe: 'response', params })
            .pipe(map((res: HttpResponse<TextSubmission[]>) => this.submissionService.convertArrayResponse(res)));
    }

    /**
     *
     * @param exerciseId id of the exerciser
     * @param option 'head': Do not optimize assessment order. Only used to check if assessments available.
     * @param correctionRound: The correction round for which we want to get a new assessment
     */
    getSubmissionWithoutAssessment(exerciseId: number, option?: 'lock' | 'head', correctionRound = 0): Observable<TextSubmission> {
        const url = `api/exercises/${exerciseId}/text-submission-without-assessment`;
        let params = new HttpParams();
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }
        if (option) {
            params = params.set(option, 'true');
        }

        return this.http.get<TextSubmission>(url, { observe: 'response', params }).pipe(
            map((response) => {
                const submission = response.body!;
                setLatestSubmissionResult(submission, getLatestSubmissionResult(submission));
                submission.participation!.submissions = [submission];
                submission.participation!.results = [submission.latestResult!];
                submission.atheneTextAssessmentTrackingToken = response.headers.get('x-athene-tracking-authorization') || undefined;
                return submission;
            }),
        );
    }
}
