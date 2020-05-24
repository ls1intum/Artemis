import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { map, tap } from 'rxjs/operators';
import { TextSubmission } from 'app/entities/text-submission.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { stringifyCircular } from 'app/shared/util/utils';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';

export type EntityResponseType = HttpResponse<TextSubmission>;

@Injectable({ providedIn: 'root' })
export class TextSubmissionService {
    constructor(private http: HttpClient, private submissionService: SubmissionService) {}

    create(textSubmission: TextSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(textSubmission);
        return this.http
            .post<TextSubmission>(`api/exercises/${exerciseId}/text-submissions`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    update(textSubmission: TextSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(textSubmission);
        return this.http
            .put<TextSubmission>(`api/exercises/${exerciseId}/text-submissions`, stringifyCircular(copy), {
                headers: { 'Content-Type': 'application/json' },
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    getTextSubmissionsForExercise(exerciseId: number, req: { submittedOnly?: boolean; assessedByTutor?: boolean }): Observable<HttpResponse<TextSubmission[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<TextSubmission[]>(`api/exercises/${exerciseId}/text-submissions`, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<TextSubmission[]>) => this.convertArrayResponse(res)));
    }

    // option = 'head': Do not optimize assessment order. Only used to check if assessments available.
    getTextSubmissionForExerciseWithoutAssessment(exerciseId: number, option?: 'lock' | 'head'): Observable<TextSubmission> {
        let url = `api/exercises/${exerciseId}/text-submission-without-assessment`;
        if (option) {
            url += `?${option}=true`;
        }
        return this.http.get<TextSubmission>(url).pipe(
            tap((submission) => (submission.participation.submissions = [submission])),
            tap((submission) => (submission.participation.results = [submission.result])),
        );
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: TextSubmission = this.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<TextSubmission[]>): HttpResponse<TextSubmission[]> {
        const jsonResponse: TextSubmission[] = res.body!;
        const body: TextSubmission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to TextSubmission.
     */
    private convertItemFromServer(textSubmission: TextSubmission): TextSubmission {
        const convertedSubmission = Object.assign({}, textSubmission);
        convertedSubmission.durationInMinutes = this.submissionService.calculateDurationInMinutes(convertedSubmission);
        return convertedSubmission;
    }

    /**
     * Convert a TextSubmission to a JSON which can be sent to the server.
     */
    private convert(textSubmission: TextSubmission): TextSubmission {
        return Object.assign({}, textSubmission);
    }
}
