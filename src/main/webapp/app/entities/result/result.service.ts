import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_FORMAT } from 'app/shared/constants/input.constants';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IResult } from 'app/shared/model/result.model';

type EntityResponseType = HttpResponse<IResult>;
type EntityArrayResponseType = HttpResponse<IResult[]>;

@Injectable({ providedIn: 'root' })
export class ResultService {
    private resourceUrl = SERVER_API_URL + 'api/results';

    constructor(private http: HttpClient) {}

    create(result: IResult): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(result);
        return this.http
            .post<IResult>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(result: IResult): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(result);
        return this.http
            .put<IResult>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<IResult>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<IResult[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    private convertDateFromClient(result: IResult): IResult {
        const copy: IResult = Object.assign({}, result, {
            completionDate: result.completionDate != null && result.completionDate.isValid() ? result.completionDate.toJSON() : null
        });
        return copy;
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        res.body.completionDate = res.body.completionDate != null ? moment(res.body.completionDate) : null;
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        res.body.forEach((result: IResult) => {
            result.completionDate = result.completionDate != null ? moment(result.completionDate) : null;
        });
        return res;
    }
}
