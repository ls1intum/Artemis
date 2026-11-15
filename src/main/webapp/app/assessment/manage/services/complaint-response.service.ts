import { Service, inject } from '@angular/core';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { AccountService } from 'app/core/auth/account.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import type { ComplaintResponse as ComplaintResponseDTO } from 'app/openapi/model/complaint-response';
import { User } from 'app/account/user/user.model';

/**
 * Keeps the complaint response lock rules and the mapping of the generated complaint response to the client entity (dayjs dates, user instance).
 */
@Service()
export class ComplaintResponseService {
    private accountService = inject(AccountService);

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

    public convertComplaintResponseFromServer(dto: ComplaintResponseDTO): ComplaintResponse {
        const complaintResponse = new ComplaintResponse();
        complaintResponse.id = dto.id;
        complaintResponse.responseText = dto.responseText ?? '';
        complaintResponse.submittedTime = convertDateStringFromServer(dto.submittedTime);
        complaintResponse.isCurrentlyLocked = dto.isCurrentlyLocked;
        complaintResponse.lockEndDate = convertDateStringFromServer(dto.lockEndDate);
        if (dto.reviewer) {
            const reviewer = new User(dto.reviewer.id, dto.reviewer.login, dto.reviewer.firstName, dto.reviewer.lastName);
            reviewer.name = dto.reviewer.name;
            complaintResponse.reviewer = reviewer;
        }
        return complaintResponse;
    }
}
