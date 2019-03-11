import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import * as moment from 'moment';

import { Complaint } from 'app/entities/complaint/complaint.model';

type EntityResponseType = HttpResponse<Complaint>;

@Injectable({ providedIn: 'root' })
export class ComplaintService {
    private resourceUrl = SERVER_API_URL + 'api/complaints';

    constructor(private http: HttpClient) {}

    create(complaint: Complaint): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(complaint);
        return this.http
            .post<Complaint>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<Complaint>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    findByResultId(resultId: number): Observable<EntityResponseType> {
        return this.http
            .get<Complaint>(`${this.resourceUrl}/result/${resultId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    private convertDateFromClient(complaint: Complaint): Complaint {
        return Object.assign({}, complaint, {
            submittedTime: complaint.submittedTime != null && moment(complaint.submittedTime).isValid ? complaint.submittedTime.toJSON() : null
        });
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.submittedTime = res.body.submittedTime != null ? moment(res.body.submittedTime) : null;
        }
        return res;
    }
}
