import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import * as moment from 'moment';
import { ComplaintResponse } from 'app/entities/complaint-response.model';

type EntityResponseType = HttpResponse<ComplaintResponse>;

@Injectable({ providedIn: 'root' })
export class ComplaintResponseService {
    private resourceUrl = SERVER_API_URL + 'api/complaint-responses';

    constructor(private http: HttpClient) {}

    /** Creates a response to a complaint
     * @param {ComplaintResponse} complaintResponse - the response to add to the complaint
     * */
    create(complaintResponse: ComplaintResponse): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(complaintResponse);
        return this.http
            .post<ComplaintResponse>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }
    /** Returns a complaint found by its id
     * @param {number} complaintId - the complaint id to search for
     * */
    findByComplaintId(complaintId: number): Observable<EntityResponseType> {
        return this.http
            .get<ComplaintResponse>(`${this.resourceUrl}/complaint/${complaintId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    private convertDateFromClient(complaintResponse: ComplaintResponse): ComplaintResponse {
        return Object.assign({}, complaintResponse, {
            submittedTime: complaintResponse.submittedTime != null && moment(complaintResponse.submittedTime).isValid ? complaintResponse.submittedTime.toJSON() : null,
        });
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.submittedTime = res.body.submittedTime != null ? moment(res.body.submittedTime) : null;
        }
        return res;
    }
}
