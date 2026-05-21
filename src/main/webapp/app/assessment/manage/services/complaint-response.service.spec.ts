import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { take } from 'rxjs/operators';
import { ComplaintResponseService } from 'app/assessment/manage/services/complaint-response.service';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { User, UserIdAndLoginDTO } from 'app/core/user/user.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ComplaintAction, ComplaintResponseDTO, ComplaintResponseUpdateDTO } from 'app/assessment/shared/entities/complaint-response-dto.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('ComplaintResponseService', () => {
    setupTestBed({ zoneless: true });
    let complaintResponseService: ComplaintResponseService;
    let httpTestingController: HttpTestingController;
    let defaultComplaintResponseDTO: ComplaintResponseDTO;
    let expectedComplaintResponse: any;
    let complaintResponseResolve: ComplaintResponseUpdateDTO;
    let complaintResponseRefresh: ComplaintResponseUpdateDTO;
    let accountService: AccountService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        })
            .compileComponents()
            .then(() => {
                expectedComplaintResponse = {} as HttpResponse<ComplaintResponse>;
                complaintResponseService = TestBed.inject(ComplaintResponseService);
                httpTestingController = TestBed.inject(HttpTestingController);
                accountService = TestBed.inject(AccountService);

                defaultComplaintResponseDTO = {
                    id: 1,
                    responseText: 'response_text',
                    submittedTime: dayjs(),
                    isCurrentlyLocked: true,
                    lockEndDate: dayjs(),
                    complaintId: 1,
                    reviewer: { id: 2, login: 'reviewer1' } as UserIdAndLoginDTO,
                } as ComplaintResponseDTO;

                complaintResponseResolve = new ComplaintResponseUpdateDTO();
                complaintResponseResolve.action = ComplaintAction.RESOLVE_COMPLAINT;
                complaintResponseResolve.responseText = 'response_text';
                complaintResponseResolve.complaintIsAccepted = true;
                complaintResponseRefresh = new ComplaintResponseUpdateDTO();
                complaintResponseRefresh.action = ComplaintAction.REFRESH_LOCK;
            });
    });

    afterEach(() => {
        httpTestingController.verify();
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

    it('should call refreshLock', async () => {
        const returnedFromService = { ...defaultComplaintResponseDTO, isCurrentlyLocked: true };
        complaintResponseService
            .refreshLockOrResolveComplaint(complaintResponseRefresh, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedComplaintResponse = resp));
        const req = httpTestingController.expectOne({ method: 'PATCH' });
        expect(req.request.body).toEqual(complaintResponseRefresh);
        req.flush(returnedFromService);

        expect(expectedComplaintResponse.body).toBeDefined();
        expect(expectedComplaintResponse.body!.id).toBe(1);
        expect(expectedComplaintResponse.body!.responseText).toBe('response_text');
        expect(expectedComplaintResponse.body!.isCurrentlyLocked).toBe(true);
        expect(expectedComplaintResponse.body!.reviewer?.login).toBe('reviewer1');
    });

    it('should call removeLock', async () => {
        const returnedFromService = {};
        complaintResponseService
            .removeLock(1)
            .pipe(take(1))
            .subscribe((resp) => (expectedComplaintResponse = resp));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush(returnedFromService);
    });

    it('should call createLock', async () => {
        const returnedFromService = { ...defaultComplaintResponseDTO };
        complaintResponseService
            .createLock(1)
            .pipe(take(1))
            .subscribe((resp) => (expectedComplaintResponse = resp));
        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedComplaintResponse.body).toBeDefined();
        expect(expectedComplaintResponse.body!.id).toBe(1);
        expect(expectedComplaintResponse.body!.responseText).toBe('response_text');
        expect(expectedComplaintResponse.body!.isCurrentlyLocked).toBe(true);
        expect(expectedComplaintResponse.body!.reviewer?.login).toBe('reviewer1');
    });

    it('should call resolveComplaint', async () => {
        const returnedFromService = { ...defaultComplaintResponseDTO, isCurrentlyLocked: false, responseText: 'response_text' };
        complaintResponseService
            .refreshLockOrResolveComplaint(complaintResponseResolve, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedComplaintResponse = resp));
        const req = httpTestingController.expectOne({ method: 'PATCH' });
        req.flush(returnedFromService);
        expect(expectedComplaintResponse.body).toBeDefined();
        expect(expectedComplaintResponse.body!.id).toBe(1);
        expect(expectedComplaintResponse.body!.responseText).toBe('response_text');
        expect(expectedComplaintResponse.body!.isCurrentlyLocked).toBe(false);
    });
});
