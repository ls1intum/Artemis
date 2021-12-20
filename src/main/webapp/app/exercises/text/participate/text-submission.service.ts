import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { TextSubmission } from 'app/entities/text-submission.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { stringifyCircular } from 'app/shared/util/utils';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';

export type EntityResponseType = HttpResponse<TextSubmission>;

@Injectable({ providedIn: 'root' })
export class TextSubmissionService {
    constructor(private http: HttpClient) {}

    create(textSubmission: TextSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = TextSubmissionService.convert(textSubmission);
        return this.http
            .post<TextSubmission>(`api/exercises/${exerciseId}/text-submissions`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => TextSubmissionService.convertResponse(res)));
    }

    update(textSubmission: TextSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = TextSubmissionService.convert(textSubmission);
        return this.http
            .put<TextSubmission>(`api/exercises/${exerciseId}/text-submissions`, stringifyCircular(copy), {
                headers: { 'Content-Type': 'application/json' },
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => TextSubmissionService.convertResponse(res)));
    }

    getTextSubmission(submissionId: number): Observable<TextSubmission> {
        return this.http
            .get<TextSubmission>(`api/text-submissions/${submissionId}`, {
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<TextSubmission>) => res.body!));
    }

    getTextSubmissionsForExerciseByCorrectionRound(
        exerciseId: number,
        req: { submittedOnly?: boolean; assessedByTutor?: boolean },
        correctionRound = 0,
    ): Observable<HttpResponse<TextSubmission[]>> {
        const url = `api/exercises/${exerciseId}/text-submissions`;
        let params = createRequestOption(req);
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }

        return this.http
            .get<TextSubmission[]>(url, { observe: 'response', params })
            .pipe(map((res: HttpResponse<TextSubmission[]>) => TextSubmissionService.convertArrayResponse(res)));
    }

    /**
     *
     * @param exerciseId id of the exerciser
     * @param option 'head': Do not optimize assessment order. Only used to check if assessments available.
     * @param correctionRound: The correction round for which we want to get a new assessment
     */
    getTextSubmissionForExerciseForCorrectionRoundWithoutAssessment(exerciseId: number, option?: 'lock' | 'head', correctionRound = 0): Observable<TextSubmission> {
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

    private static convertResponse(res: EntityResponseType): EntityResponseType {
        const body: TextSubmission = TextSubmissionService.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    private static convertArrayResponse(res: HttpResponse<TextSubmission[]>): HttpResponse<TextSubmission[]> {
        const jsonResponse: TextSubmission[] = res.body!;
        const body: TextSubmission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(TextSubmissionService.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to TextSubmission.
     */
    private static convertItemFromServer(textSubmission: TextSubmission): TextSubmission {
        const convertedTextSubmission = Object.assign({}, textSubmission);
        setLatestSubmissionResult(convertedTextSubmission, getLatestSubmissionResult(convertedTextSubmission));
        convertedTextSubmission.participation = ParticipationService.convertParticipationDatesFromServer(textSubmission.participation);
        return convertedTextSubmission;
    }

    /**
     * Convert a TextSubmission to a JSON which can be sent to the server.
     */
    private static convert(textSubmission: TextSubmission): TextSubmission {
        return Object.assign({}, textSubmission);
    }
}
