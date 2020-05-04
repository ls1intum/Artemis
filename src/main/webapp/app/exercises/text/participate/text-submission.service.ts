import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { tap, map } from 'rxjs/operators';

import { TextSubmission } from 'app/entities/text-submission.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { stringifyCircular } from 'app/shared/util/utils';

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

    getTextSubmissionsForExercise(exerciseId: number, req: { submittedOnly?: boolean; assessedByTutor?: boolean }): Observable<HttpResponse<TextSubmission[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<TextSubmission[]>(`api/exercises/${exerciseId}/text-submissions`, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<TextSubmission[]>) => TextSubmissionService.convertArrayResponse(res)));
    }

    getTextSubmissionForExerciseWithoutAssessment(exerciseId: number, lock = false): Observable<TextSubmission> {
        let url = `api/exercises/${exerciseId}/text-submission-without-assessment`;
        if (lock) {
            url += '?lock=true';
        }
        return this.http.get<TextSubmission>(url).pipe(
            tap((submission) => (submission.participation.submissions = [submission])),
            tap((submission) => (submission.participation.results = [submission.result])),
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
        return Object.assign({}, textSubmission);
    }

    /**
     * Convert a TextSubmission to a JSON which can be sent to the server.
     */
    private static convert(textSubmission: TextSubmission): TextSubmission {
        return Object.assign({}, textSubmission);
    }
}
