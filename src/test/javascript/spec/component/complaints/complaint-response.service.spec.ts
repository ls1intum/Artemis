import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { getTestBed, TestBed } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Complaint } from 'app/entities/complaint.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockProvider } from 'ng-mocks';
import dayjs from 'dayjs';
import { User } from 'app/core/user/user.model';
import { TextExercise } from 'app/entities/text-exercise.model';

describe('ComplaintResponseService', () => {
    let testBed: TestBed;
    let complaintResponseService: ComplaintResponseService;
    let httpTestingController: HttpTestingController;
    let defaultComplaintResponse: ComplaintResponse;
    let expectedComplaintResponse: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [MockProvider(AccountService)],
        });
        expectedComplaintResponse = {} as HttpResponse<ComplaintResponse>;

        testBed = getTestBed();
        complaintResponseService = testBed.get(ComplaintResponseService);
        httpTestingController = testBed.get(HttpTestingController);

        defaultComplaintResponse = new ComplaintResponse();
        defaultComplaintResponse.id = 1;
        defaultComplaintResponse.lockEndDate = dayjs();
        defaultComplaintResponse.submittedTime = dayjs();
        defaultComplaintResponse.complaint = new Complaint();
        defaultComplaintResponse.complaint.id = 1;
    });

    afterEach(() => {
        httpTestingController.verify();
        jest.resetAllMocks();
    });

    function setupLockTest(loginOfLoggedInUser: string, loggedInUserIsInstructor: boolean, loginOfReviewer: string, lockActive: boolean) {
        const accountService = testBed.get(AccountService);
        jest.spyOn(accountService, 'userIdentity', 'get').mockImplementation(function getterFn() {
            const user = new User();
            user.login = loginOfLoggedInUser;
            return user;
        });
        jest.spyOn(accountService, 'isAtLeastInstructorForExercise').mockReturnValue(loggedInUserIsInstructor);

        const lockedComplaintResponse = new ComplaintResponse();
        lockedComplaintResponse.isCurrentlyLocked = lockActive;
        lockedComplaintResponse.submittedTime = undefined;
        const reviewer = new User();
        reviewer.login = loginOfReviewer;
        lockedComplaintResponse.reviewer = reviewer;
        return lockedComplaintResponse;
    }

    it('should correctly calculate that complaint response is locked for user', () => {
        const lockedComplaintResponse = setupLockTest('test', false, 'test2', true);
        const isLocked = complaintResponseService.isComplaintResponseLockedForLoggedInUser(lockedComplaintResponse, new TextExercise(undefined, undefined));
        expect(isLocked).toBe(true);
    });

    it('should correctly calculate that complaint response is not locked for instructor', () => {
        const lockedComplaintResponse = setupLockTest('test', true, 'test2', true);
        const isLocked = complaintResponseService.isComplaintResponseLockedForLoggedInUser(lockedComplaintResponse, new TextExercise(undefined, undefined));
        expect(isLocked).toBe(false);
    });

    it('should correctly calculate that complaint response is not locked for reviewer', () => {
        const lockedComplaintResponse = setupLockTest('test', false, 'test', true);
        const isLocked = complaintResponseService.isComplaintResponseLockedForLoggedInUser(lockedComplaintResponse, new TextExercise(undefined, undefined));
        expect(isLocked).toBe(false);
    });

    it('should call refreshLock', async () => {
        const returnedFromService = { ...defaultComplaintResponse };
        complaintResponseService
            .refreshLock(1)
            .pipe(take(1))
            .subscribe((resp) => (expectedComplaintResponse = resp));
        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedComplaintResponse.body).toEqual(defaultComplaintResponse);
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
        const returnedFromService = { ...defaultComplaintResponse };
        complaintResponseService
            .createLock(1)
            .pipe(take(1))
            .subscribe((resp) => (expectedComplaintResponse = resp));
        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedComplaintResponse.body).toEqual(defaultComplaintResponse);
    });

    it('should call resolveComplaint', async () => {
        const returnedFromService = { ...defaultComplaintResponse };
        complaintResponseService
            .resolveComplaint(defaultComplaintResponse)
            .pipe(take(1))
            .subscribe((resp) => (expectedComplaintResponse = resp));
        const req = httpTestingController.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        expect(expectedComplaintResponse.body).toEqual(defaultComplaintResponse);
    });

    it('should call findByComplaintId', async () => {
        const returnedFromService = { ...defaultComplaintResponse };
        complaintResponseService
            .findByComplaintId(1)
            .pipe(take(1))
            .subscribe((resp) => (expectedComplaintResponse = resp));
        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedComplaintResponse.body).toEqual(defaultComplaintResponse);
    });
});
