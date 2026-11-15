import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ComplaintResponseService } from 'app/assessment/manage/services/complaint-response.service';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { User } from 'app/account/user/user.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import type { ComplaintResponse as ComplaintResponseDTO } from 'app/openapi/model/complaint-response';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('ComplaintResponseService', () => {
    let complaintResponseService: ComplaintResponseService;
    let defaultComplaintResponse: ComplaintResponseDTO;
    let accountService: AccountService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: AccountService, useClass: MockAccountService }],
        });
        complaintResponseService = TestBed.inject(ComplaintResponseService);
        accountService = TestBed.inject(AccountService);
        defaultComplaintResponse = {
            id: 1,
            responseText: 'response_text',
            submittedTime: '2026-01-01T10:00:00Z',
            isCurrentlyLocked: true,
            lockEndDate: '2026-01-01T11:00:00Z',
            complaintId: 1,
            reviewer: { id: 2, login: 'reviewer1' },
        };
    });

    afterEach(() => {
        vi.resetAllMocks();
    });

    function setupLockTest(loginOfLoggedInUser: string, loggedInUserIsInstructor: boolean, loginOfReviewer: string, lockActive: boolean) {
        accountService.userIdentity.set({ login: loginOfLoggedInUser } as User);
        vi.spyOn(accountService, 'isAtLeastInstructorForExercise').mockReturnValue(loggedInUserIsInstructor);

        const lockedComplaintResponse = new ComplaintResponse();
        lockedComplaintResponse.isCurrentlyLocked = lockActive;
        lockedComplaintResponse.submittedTime = undefined;
        const reviewer = new User();
        reviewer.login = loginOfReviewer;
        lockedComplaintResponse.reviewer = reviewer;
        return lockedComplaintResponse;
    }

    it.each([
        { user: 'test', instructor: false, reviewer: 'test2', lockActive: false, expected: false },
        { user: 'test', instructor: false, reviewer: 'test2', lockActive: true, expected: true },
        { user: 'test', instructor: true, reviewer: 'test2', lockActive: false, expected: false },
        { user: 'test', instructor: true, reviewer: 'test2', lockActive: true, expected: false },
        { user: 'test', instructor: false, reviewer: 'test', lockActive: true, expected: false },
        { user: 'test', instructor: false, reviewer: 'test', lockActive: false, expected: false },
        { user: 'test', instructor: true, reviewer: 'test', lockActive: false, expected: false },
    ])('should correctly calculate complaint lock status', ({ user, instructor, reviewer, lockActive, expected }) => {
        const lockedComplaintResponse = setupLockTest(user, instructor, reviewer, lockActive);
        const isLocked = complaintResponseService.isComplaintResponseLockedForLoggedInUser(lockedComplaintResponse, new TextExercise(undefined, undefined));
        expect(isLocked).toBe(expected);
    });

    it('should map the generated complaint response to the client entity', () => {
        const mapped = complaintResponseService.convertComplaintResponseFromServer(defaultComplaintResponse);

        expect(mapped.id).toBe(1);
        expect(mapped.responseText).toBe('response_text');
        expect(mapped.isCurrentlyLocked).toBe(true);
        expect(mapped.submittedTime!.isSame(dayjs('2026-01-01T10:00:00Z'))).toBe(true);
        expect(mapped.lockEndDate!.isSame(dayjs('2026-01-01T11:00:00Z'))).toBe(true);
        expect(mapped.reviewer).toBeInstanceOf(User);
        expect(mapped.reviewer?.login).toBe('reviewer1');
    });

    it('should keep absent dates and reviewer absent and an absent response text empty', () => {
        const mapped = complaintResponseService.convertComplaintResponseFromServer({ id: 2, complaintId: 1 });

        expect(mapped.submittedTime).toBeUndefined();
        expect(mapped.lockEndDate).toBeUndefined();
        expect(mapped.reviewer).toBeUndefined();
        expect(mapped.responseText).toBe('');
    });
});
