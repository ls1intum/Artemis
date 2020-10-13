import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import * as moment from 'moment';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared/util/request-util';
import { ISubmission } from 'app/shared/model/submission.model';

type EntityResponseType = HttpResponse<ISubmission>;
type EntityArrayResponseType = HttpResponse<ISubmission[]>;

@Injectable({ providedIn: 'root' })
export class SubmissionService {
    public resourceUrl = SERVER_API_URL + 'api/submissions';

    constructor(protected http: HttpClient) {}

    create(submission: ISubmission): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(submission);
        return this.http
            .post<ISubmission>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(submission: ISubmission): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(submission);
        return this.http
            .put<ISubmission>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<ISubmission>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<ISubmission[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    delete(id: number): Observable<HttpResponse<{}>> {
        return this.http.delete(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    protected convertDateFromClient(submission: ISubmission): ISubmission {
        const copy: ISubmission = Object.assign({}, submission, {
            submissionDate: submission.submissionDate && submission.submissionDate.isValid() ? submission.submissionDate.toJSON() : undefined,
        });
        return copy;
    }

    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.submissionDate = res.body.submissionDate ? moment(res.body.submissionDate) : undefined;
        }
        return res;
    }

    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((submission: ISubmission) => {
                submission.submissionDate = submission.submissionDate ? moment(submission.submissionDate) : undefined;
            });
        }
        return res;
    }
}
