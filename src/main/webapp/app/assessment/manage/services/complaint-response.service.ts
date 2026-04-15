import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { AccountService } from 'app/core/auth/account.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { ComplaintResponseDTO, ComplaintResponseUpdateDTO } from 'app/assessment/shared/entities/complaint-response-dto.model';
import { User } from 'app/core/user/user.model';

type EntityResponseType = HttpResponse<ComplaintResponseDTO>;

@Injectable({ providedIn: 'root' })
export class ComplaintResponseService {
    private http = inject(HttpClient);
    private accountService = inject(AccountService);

    private resourceUrl = 'api/assessment/complaints';

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
            !!complaintResponse.isCurrentlyLocked &&
            complaintResponse.submittedTime === undefined &&
            complaintResponse.reviewer?.login !== this.accountService.userIdentity()?.login
        );
    }

    /**
     * Checks if the lock on a complaint response is active and if the currently logged-in user is the creator of the lock
     * @param complaintResponse complaint response to check
     */
    isComplaintResponseLockedByLoggedInUser(complaintResponse: ComplaintResponse): boolean {
        return (
            !!complaintResponse.isCurrentlyLocked &&
            complaintResponse.submittedTime === undefined &&
            complaintResponse.reviewer?.login === this.accountService.userIdentity()?.login
        );
    }

    removeLock(complaintId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${complaintId}/response`, { observe: 'response' });
    }

    createLock(complaintId: number): Observable<HttpResponse<ComplaintResponse>> {
        return this.http
            .post<ComplaintResponseDTO>(`${this.resourceUrl}/${complaintId}/response`, {}, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertComplaintResponseEntityResponseDatesFromServer(res)));
    }

    refreshLockOrResolveComplaint(complaintResponseUpdate: ComplaintResponseUpdateDTO, complaintId: number | undefined): Observable<HttpResponse<ComplaintResponse>> {
        return this.http
            .patch<ComplaintResponseDTO>(`${this.resourceUrl}/${complaintId}/response`, complaintResponseUpdate, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertComplaintResponseEntityResponseDatesFromServer(res)));
    }

    public convertComplaintResponseEntityResponseDatesFromServer(res: EntityResponseType): HttpResponse<ComplaintResponse> {
        return res.clone({ body: res.body ? this.convertComplaintResponseFromServer(res.body) : undefined });
    }

    public convertComplaintResponseDatesFromServer(complaintResponse: ComplaintResponse): ComplaintResponse {
        if (complaintResponse) {
            complaintResponse.submittedTime = convertDateFromServer(complaintResponse.submittedTime);
            complaintResponse.lockEndDate = convertDateFromServer(complaintResponse.lockEndDate);
        }
        return complaintResponse;
    }

    public convertComplaintResponseFromServer(dto: ComplaintResponseDTO): ComplaintResponse {
        const complaintResponse = new ComplaintResponse();
        complaintResponse.id = dto.id;
        complaintResponse.responseText = dto.responseText ?? '';
        complaintResponse.submittedTime = convertDateFromServer(dto.submittedTime);
        complaintResponse.isCurrentlyLocked = dto.isCurrentlyLocked;
        complaintResponse.lockEndDate = convertDateFromServer(dto.lockEndDate);
        if (dto.reviewer) {
            complaintResponse.reviewer = new User(dto.reviewer.id, dto.reviewer.login);
        }
        return complaintResponse;
    }
}
