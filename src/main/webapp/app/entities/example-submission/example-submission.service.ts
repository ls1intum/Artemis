import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ExampleSubmission } from 'app/entities/example-submission/example-submission.model';

export type EntityResponseType = HttpResponse<ExampleSubmission>;

@Injectable({ providedIn: 'root' })
export class ExampleSubmissionService {

    constructor(private http: HttpClient) {}

    create(exampleSubmission: ExampleSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(exampleSubmission);
        return this.http
            .post<ExampleSubmission>(`api/exercises/${exerciseId}/example-submissions`, copy, {
                observe: 'response'
            })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(exampleSubmission: ExampleSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(exampleSubmission);
        return this.http
            .put<ExampleSubmission>(`api/exercises/${exerciseId}/example-submissions`, copy, {
                observe: 'response'
            })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    get(exampleSubmissionId: number): Observable<EntityResponseType> {
        return this.http
            .get<ExampleSubmission>(`api/example-submissions/${exampleSubmissionId}`, {
                observe: 'response'
            })
            .map((res: HttpResponse<ExampleSubmission>) => this.convertResponse(res));
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: ExampleSubmission = this.convertItemFromServer(res.body);
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to ExampleSubmission.
     */
    private convertItemFromServer(exampleSubmission: ExampleSubmission): ExampleSubmission {
        return Object.assign({}, exampleSubmission);
    }

    /**
     * Convert a ExampleSubmission to a JSON which can be sent to the server.
     */
    private convert(exampleSubmission: ExampleSubmission): ExampleSubmission {
        return Object.assign({}, exampleSubmission);
    }
}
