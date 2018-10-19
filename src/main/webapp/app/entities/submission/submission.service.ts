import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_FORMAT } from 'app/shared/constants/input.constants';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { ISubmission } from 'app/shared/model/submission.model';

type EntityResponseType = HttpResponse<ISubmission>;
type EntityArrayResponseType = HttpResponse<ISubmission[]>;

@Injectable({ providedIn: 'root' })
export class SubmissionService {
    public resourceUrl = SERVER_API_URL + 'api/submissions';

    constructor(private http: HttpClient) {}

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

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    private convertDateFromClient(submission: ISubmission): ISubmission {
        const copy: ISubmission = Object.assign({}, submission, {
            submissionDate:
                submission.submissionDate != null && submission.submissionDate.isValid() ? submission.submissionDate.toJSON() : null
        });
        return copy;
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        res.body.submissionDate = res.body.submissionDate != null ? moment(res.body.submissionDate) : null;
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        res.body.forEach((submission: ISubmission) => {
            submission.submissionDate = submission.submissionDate != null ? moment(submission.submissionDate) : null;
        });
        return res;
    }
}
