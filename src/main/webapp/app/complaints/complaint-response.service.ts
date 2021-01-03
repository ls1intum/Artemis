import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import * as moment from 'moment';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { AccountService } from 'app/core/auth/account.service';
import { Exercise } from 'app/entities/exercise.model';

type EntityResponseType = HttpResponse<ComplaintResponse>;

@Injectable({ providedIn: 'root' })
export class ComplaintResponseService {
    private resourceUrl = SERVER_API_URL + 'api/complaint-responses';

    constructor(private http: HttpClient, private accountService: AccountService) {}

    isComplaintResponseLockedForLoggedInUser(complaintResponse: ComplaintResponse, exercise: Exercise) {
        if (complaintResponse.isCurrentlyLocked) {
            if (complaintResponse.reviewer?.login === this.accountService.userIdentity?.login || this.accountService.isAtLeastInstructorForExercise(exercise)) {
                return false;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    isComplaintResponseLockedByLoggedInUser(complaintResponse: ComplaintResponse) {
        if (complaintResponse.isCurrentlyLocked && complaintResponse.reviewer?.login === this.accountService.userIdentity?.login) {
            return true;
        } else {
            return false;
        }
    }

    updateLock(complaintId: number): Observable<EntityResponseType> {
        return this.http
            .post<ComplaintResponse>(`${this.resourceUrl}/complaint/${complaintId}/update-lock`, {}, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    removeLock(complaintId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/complaint/${complaintId}/remove-lock`, { observe: 'response' });
    }

    createInitialResponse(complaintId: number): Observable<EntityResponseType> {
        return this.http
            .post<ComplaintResponse>(`${this.resourceUrl}/complaint/${complaintId}/create-initial`, {}, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    update(complaintResponse: ComplaintResponse): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(complaintResponse);
        return this.http
            .put<ComplaintResponse>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    findByComplaintId(complaintId: number): Observable<EntityResponseType> {
        return this.http
            .get<ComplaintResponse>(`${this.resourceUrl}/complaint/${complaintId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    private convertDateFromClient(complaintResponse: ComplaintResponse): ComplaintResponse {
        return Object.assign({}, complaintResponse, {
            submittedTime: complaintResponse.submittedTime != undefined && moment(complaintResponse.submittedTime).isValid ? complaintResponse.submittedTime.toJSON() : undefined,
        });
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.submittedTime = res.body.submittedTime != undefined ? moment(res.body.submittedTime) : undefined;
            res.body.lockEndDate = res.body.lockEndDate != undefined ? moment(res.body.lockEndDate) : undefined;
        }
        return res;
    }
}
