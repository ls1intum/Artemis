import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ComplaintService } from 'app/complaints/complaint.service';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { RouterTestingModule } from '@angular/router/testing';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { By } from '@angular/platform-browser';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { NgModel } from '@angular/forms';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';

describe('ComplaintsForTutorComponent', () => {
    let complaintsForTutorComponent: ComplaintsForTutorComponent;
    let fixture: ComponentFixture<ComplaintsForTutorComponent>;
    let injectedComplaintResponseService: ComplaintResponseService;
    let injectedAccountService: AccountService;

    const tutorUser: User = {
        id: 42,
    } as User;

    const lockComplaintResponse = new ComplaintResponse();
    lockComplaintResponse.id = 1;

    const acceptedComplaintResponse = { ...lockComplaintResponse, responseText: 'accepted' };

    const rejectedComplaintResponse = { ...lockComplaintResponse, responseText: 'rejected' };

    const unhandledComplaint = new Complaint();
    unhandledComplaint.id = 1;
    unhandledComplaint.accepted = undefined;
    unhandledComplaint.complaintType = ComplaintType.COMPLAINT;
    unhandledComplaint.complaintText = 'please check again';
    unhandledComplaint.complaintResponse = undefined;

    let createLockStub: jest.SpyInstance<Observable<HttpResponse<ComplaintResponse>>, [complaintId: number]>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [ComplaintsForTutorComponent, TranslatePipeMock, MockPipe(ArtemisDatePipe), MockDirective(NgModel)],
            providers: [MockProvider(ComplaintResponseService), MockProvider(ComplaintService), MockProvider(AlertService), MockProvider(AccountService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintsForTutorComponent);
                complaintsForTutorComponent = fixture.componentInstance;
                injectedComplaintResponseService = fixture.debugElement.injector.get(ComplaintResponseService);
                injectedAccountService = fixture.debugElement.injector.get(AccountService);

                jest.spyOn(injectedAccountService, 'identity').mockReturnValue(Promise.resolve(tutorUser));
                createLockStub = jest.spyOn(injectedComplaintResponseService, 'createLock');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should just display an already handled complaint', () => {
        const handledComplaint = new Complaint();
        handledComplaint.id = 1;
        handledComplaint.accepted = true;
        handledComplaint.complaintText = 'please check again';
        handledComplaint.complaintResponse = new ComplaintResponse();
        handledComplaint.complaintResponse.id = 1;
        handledComplaint.complaintResponse.responseText = 'gj';
        handledComplaint.complaintType = ComplaintType.COMPLAINT;
        complaintsForTutorComponent.isAssessor = false;
        complaintsForTutorComponent.complaint = handledComplaint;
        fixture.detectChanges();

        const responseTextArea = complaintForTutorComponentFixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        const complainTextArea = complaintForTutorComponentFixture.debugElement.query(By.css('#complaintTextArea')).nativeElement;
        expect(responseTextArea.value).toEqual(handledComplaint.complaintResponse.responseText);
        expect(responseTextArea.disabled).toBe(true);
        expect(responseTextArea.readOnly).toBe(true);
        expect(complainTextArea.readOnly).toBe(true);
        expect(complainTextArea.value).toEqual(handledComplaint.complaintText);
        expect(complaintsForTutorComponent.handled).toBe(true);
        expect(complaintsForTutorComponent.isLockedForLoggedInUser).toBe(false);
    });

    it('should create a new complaint response for a unhandled complaint without a connected complaint response', fakeAsync(() => {
        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = unhandledComplaint;

        createLockStub.mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        complaintsForTutorComponent.complaint = unhandledComplaint;
        complaintsForTutorComponent.isAssessor = false;

        expectToBeLocked(freshlyCreatedComplaintResponse);
    }));

    it('should refresh a complaint response for a unhandled complaint with a connected complaint response', fakeAsync(() => {
        jest.spyOn(injectedComplaintResponseService, 'isComplaintResponseLockedForLoggedInUser').mockReturnValue(false);

        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = { ...unhandledComplaint, complaintResponse: lockComplaintResponse };

        createLockStub.mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        complaintsForTutorComponent.isAssessor = false;
        complaintsForTutorComponent.complaint = unhandledComplaint;

        expectToBeLocked(freshlyCreatedComplaintResponse);
    }));

    function expectToBeLocked(complaintResponse: ComplaintResponse) {
        fixture.detectChanges();

        tick();

        expect(createLockStub).toHaveBeenCalledTimes(1);
        expect(complaintsForTutorComponent.complaint).toEqual(complaintResponse.complaint);
        expect(complaintsForTutorComponent.complaintResponse).toEqual(complaintResponse);
        const lockButton = fixture.debugElement.nativeElement.querySelector('#lockButton');
        const lockDuration = fixture.debugElement.nativeElement.querySelector('#lockDuration');

        expect(lockButton).not.toBe(null);
        expect(lockDuration).not.toBe(null);

        // now we test if we can give up the lock
        const removeLockStub = jest.spyOn(injectedComplaintResponseService, 'removeLock').mockReturnValue(of());
        lockButton.nativeElement.click();
        expect(removeLockStub).toHaveBeenCalledTimes(1);
    }

    it('should send event when accepting a complaint', fakeAsync(() => {
        fixture.detectChanges();
        complaintsForTutorComponent.isLockedForLoggedInUser = false;

        complaintsForTutorComponent.complaint = { ...unhandledComplaint, complaintResponse: acceptedComplaintResponse };
        complaintsForTutorComponent.complaintResponse = complaintsForTutorComponent.complaint.complaintResponse!;

        const emitSpy = jest.spyOn(complaintsForTutorComponent.updateAssessmentAfterComplaint, 'emit');

        fixture.detectChanges();

        tick();

        const acceptComplaintButton = fixture.debugElement.query(By.css('#acceptComplaintButton')).nativeElement;
        acceptComplaintButton.click();
        expect(emitSpy).toHaveBeenCalledTimes(1);
        expect(emitSpy).toHaveBeenCalledWith(expect.toContainKey('complaint'));
        const event = emitSpy.mock.calls[0][0];
        expect(event).not.toBeNull();
    }));

    it('should directly resolve when rejecting a complaint', () => {
        complaintsForTutorComponent.isLockedForLoggedInUser = false;
        complaintsForTutorComponent.complaint = { ...unhandledComplaint, complaintResponse: rejectedComplaintResponse };
        complaintsForTutorComponent.complaintResponse = complaintsForTutorComponent.complaint.complaintResponse!;

        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = unhandledComplaint;

        const resolveStub = jest.spyOn(injectedComplaintResponseService, 'resolveComplaint').mockReturnValue(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        fixture.detectChanges();

        const rejectComplaintButton = fixture.debugElement.query(By.css('#rejectComplaintButton')).nativeElement;
        rejectComplaintButton.click();

        expect(resolveStub).toHaveBeenCalledTimes(1);
    });
});
