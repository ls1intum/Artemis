import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { AccountService } from 'app/core/auth/account.service';
import { Exercise } from 'app/entities/exercise.model';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';

type EntityResponseType = HttpResponse<ComplaintResponse>;

@Injectable({ providedIn: 'root' })
export class ComplaintResponseService {
    private resourceUrl = 'api/complaint-responses';

    constructor(
        private http: HttpClient,
        private accountService: AccountService,
    ) {}

    /**
     * Checks if a complaint response is locked for the currently logged-in user
     *
     * A complaint response is never locked for the creator of the complaint response and for instructors
     *
     * @param complaintResponse complaint response to check the lock status for
     * @param exercise exercise used to find out if currently logged-in user is instructor
     */
    isComplaintResponseLockedForLoggedInUser(complaintResponse: ComplaintResponse, exercise: Exercise): boolean {
        return !this.accountService.isAtLeastInstructorForExercise(exercise) && this.isComplaintResponseLockedByOtherUser(complaintResponse);
    }

    /**
     * Checks if the lock on a complaint response is active and if NOT the currently logged-in user is the creator of the lock
     * @param complaintResponse complaint response to check
     */
    isComplaintResponseLockedByOtherUser(complaintResponse: ComplaintResponse): boolean {
        return (
            !!complaintResponse.isCurrentlyLocked && complaintResponse.submittedTime === undefined && complaintResponse.reviewer?.login !== this.accountService.userIdentity?.login
        );
    }

    /**
     * Checks if the lock on a complaint response is active and if the currently logged-in user is the creator of the lock
     * @param complaintResponse complaint response to check
     */
    isComplaintResponseLockedByLoggedInUser(complaintResponse: ComplaintResponse): boolean {
        return (
            !!complaintResponse.isCurrentlyLocked && complaintResponse.submittedTime === undefined && complaintResponse.reviewer?.login === this.accountService.userIdentity?.login
        );
    }

    refreshLock(complaintId: number): Observable<EntityResponseType> {
        return this.http
            .post<ComplaintResponse>(`${this.resourceUrl}/complaint/${complaintId}/refresh-lock`, {}, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertComplaintResponseEntityResponseDatesFromServer(res)));
    }

    removeLock(complaintId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/complaint/${complaintId}/remove-lock`, { observe: 'response' });
    }

    createLock(complaintId: number): Observable<EntityResponseType> {
        return this.http
            .post<ComplaintResponse>(`${this.resourceUrl}/complaint/${complaintId}/create-lock`, {}, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertComplaintResponseEntityResponseDatesFromServer(res)));
    }

    resolveComplaint(complaintResponse: ComplaintResponse): Observable<EntityResponseType> {
        const copy = this.convertComplaintResponseDatesFromClient(complaintResponse);
        return this.http
            .put<ComplaintResponse>(`${this.resourceUrl}/complaint/${complaintResponse.complaint!.id}/resolve`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertComplaintResponseEntityResponseDatesFromServer(res)));
    }

    findByComplaintId(complaintId: number): Observable<EntityResponseType> {
        return this.http
            .get<ComplaintResponse>(`${this.resourceUrl}/complaint/${complaintId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertComplaintResponseEntityResponseDatesFromServer(res)));
    }

    public convertComplaintResponseDatesFromClient(complaintResponse: ComplaintResponse): ComplaintResponse {
        return Object.assign({}, complaintResponse, {
            submittedTime: convertDateFromClient(complaintResponse.submittedTime),
            lockEndDate: convertDateFromClient(complaintResponse.lockEndDate),
        });
    }

    public convertComplaintResponseEntityResponseDatesFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            this.convertComplaintResponseDatesFromServer(res.body);
        }
        return res;
    }

    public convertComplaintResponseDatesFromServer(complaintResponse: ComplaintResponse): ComplaintResponse {
        if (complaintResponse) {
            complaintResponse.submittedTime = convertDateFromServer(complaintResponse.submittedTime);
            complaintResponse.lockEndDate = convertDateFromServer(complaintResponse.lockEndDate);
        }
        return complaintResponse;
    }
}
