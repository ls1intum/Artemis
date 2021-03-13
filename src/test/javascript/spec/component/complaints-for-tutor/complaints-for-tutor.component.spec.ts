import { async, ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComplaintService } from 'app/complaints/complaint.service';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { MockPipe, MockProvider } from 'ng-mocks';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiAlertService } from 'ng-jhipster';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { FormsModule } from '@angular/forms';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { By } from '@angular/platform-browser';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

chai.use(sinonChai);
const expect = chai.expect;

describe('ComplaintsForTutorComponent', () => {
    let complaintsForTutorComponent: ComplaintsForTutorComponent;
    let complaintForTutorComponentFixture: ComponentFixture<ComplaintsForTutorComponent>;
    let injectedComplaintResponseService: ComplaintResponseService;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([]), FormsModule],
            declarations: [ComplaintsForTutorComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [MockProvider(ComplaintResponseService), MockProvider(ComplaintService), MockProvider(JhiAlertService)],
        })
            .compileComponents()
            .then(() => {
                complaintForTutorComponentFixture = TestBed.createComponent(ComplaintsForTutorComponent);
                complaintsForTutorComponent = complaintForTutorComponentFixture.componentInstance;
                injectedComplaintResponseService = complaintForTutorComponentFixture.debugElement.injector.get(ComplaintResponseService);
            });
    }));

    afterEach(() => {
        sinon.restore();
    });

    it('should instantiate', () => {
        complaintForTutorComponentFixture.detectChanges();
        expect(complaintsForTutorComponent).to.be.ok;
    });

    it('should just display an already handled complaint', fakeAsync(() => {
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
        complaintForTutorComponentFixture.detectChanges();
        // We need the tick as `ngModel` writes data asynchronously into the DOM!
        tick();

        const responseTextArea = complaintForTutorComponentFixture.debugElement.query(By.css('#responseTextArea')).nativeElement;
        const complainTextArea = complaintForTutorComponentFixture.debugElement.query(By.css('#complaintTextArea')).nativeElement;
        expect(responseTextArea.value).to.equal(handledComplaint.complaintResponse.responseText);
        expect(responseTextArea.disabled).to.be.true;
        expect(responseTextArea.readOnly).to.be.true;
        expect(complainTextArea.readOnly).to.be.true;
        expect(complainTextArea.value).to.equal(handledComplaint.complaintText);
    }));

    it('should create a new complaint response for a unhandled complaint without a connected complaint response', fakeAsync(() => {
        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;

        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = unhandledComplaint;

        const createLockStub = sinon.stub(injectedComplaintResponseService, 'createLock').returns(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        complaintsForTutorComponent.complaint = unhandledComplaint;
        complaintsForTutorComponent.isAssessor = false;
        complaintForTutorComponentFixture.detectChanges();
        // We need the tick as `ngModel` writes data asynchronously into the DOM!
        tick();

        expect(createLockStub).to.have.been.called;
        expect(complaintsForTutorComponent.complaint).to.deep.equal(freshlyCreatedComplaintResponse.complaint);
        expect(complaintsForTutorComponent.complaintResponse).to.deep.equal(freshlyCreatedComplaintResponse);
        const lockButton = complaintForTutorComponentFixture.debugElement.query(By.css('#lockButton')).nativeElement;
        const lockDuration = complaintForTutorComponentFixture.debugElement.query(By.css('#lockDuration')).nativeElement;

        expect(lockButton).to.be.ok;
        expect(lockDuration).to.be.ok;

        // now we test if we can give up the lock
        const removeLockStub = sinon.stub(injectedComplaintResponseService, 'removeLock').returns(of());
        lockButton.click();
        expect(removeLockStub).to.have.been.called;
    }));

    it('should refresh a complaint response for a unhandled complaint with a connected complaint response', fakeAsync(() => {
        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintResponse = new ComplaintResponse();
        unhandledComplaint.complaintResponse.id = 1;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;
        sinon.stub(injectedComplaintResponseService, 'isComplaintResponseLockedForLoggedInUser').returns(false);

        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = unhandledComplaint;

        const createLockStub = sinon.stub(injectedComplaintResponseService, 'refreshLock').returns(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        complaintsForTutorComponent.isAssessor = false;
        complaintsForTutorComponent.complaint = unhandledComplaint;
        complaintForTutorComponentFixture.detectChanges();
        // We need the tick as `ngModel` writes data asynchronously into the DOM!
        tick();

        expect(createLockStub).to.have.been.called;
        expect(complaintsForTutorComponent.complaint).to.deep.equal(freshlyCreatedComplaintResponse.complaint);
        expect(complaintsForTutorComponent.complaintResponse).to.deep.equal(freshlyCreatedComplaintResponse);
        const lockButton = complaintForTutorComponentFixture.debugElement.query(By.css('#lockButton')).nativeElement;
        const lockDuration = complaintForTutorComponentFixture.debugElement.query(By.css('#lockDuration')).nativeElement;

        expect(lockButton).to.be.ok;
        expect(lockDuration).to.be.ok;

        // now we test if we can give up the lock
        const removeLockStub = sinon.stub(injectedComplaintResponseService, 'removeLock').returns(of());
        lockButton.click();
        expect(removeLockStub).to.have.been.called;
    }));

    it('should send event when accepting a complaint', () => {
        complaintForTutorComponentFixture.detectChanges();
        complaintsForTutorComponent.isLockedForLoggedInUser = false;

        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintResponse = new ComplaintResponse();
        unhandledComplaint.complaintResponse.responseText = 'accepted';
        unhandledComplaint.complaintResponse.id = 1;
        complaintsForTutorComponent.complaintResponse = unhandledComplaint.complaintResponse;
        complaintsForTutorComponent.complaint = unhandledComplaint;

        const emitSpy = sinon.spy(complaintsForTutorComponent.updateAssessmentAfterComplaint, 'emit');

        complaintForTutorComponentFixture.detectChanges();

        const acceptComplaintButton = complaintForTutorComponentFixture.debugElement.query(By.css('#acceptComplaintButton')).nativeElement;
        acceptComplaintButton.click();
        expect(emitSpy).to.have.been.called;
        const event = emitSpy.getCalls()[0].args[0];
        expect(event).to.be.ok;
    });

    it('should directly resolve when rejecting a complaint', () => {
        complaintForTutorComponentFixture.detectChanges();
        complaintsForTutorComponent.isLockedForLoggedInUser = false;

        const unhandledComplaint = new Complaint();
        unhandledComplaint.id = 1;
        unhandledComplaint.accepted = undefined;
        unhandledComplaint.complaintType = ComplaintType.COMPLAINT;
        unhandledComplaint.complaintText = 'please check again';
        unhandledComplaint.complaintResponse = undefined;
        unhandledComplaint.complaintResponse = new ComplaintResponse();
        unhandledComplaint.complaintResponse.responseText = 'rejected';
        unhandledComplaint.complaintResponse.id = 1;
        complaintsForTutorComponent.complaintResponse = unhandledComplaint.complaintResponse;
        complaintsForTutorComponent.complaint = unhandledComplaint;

        const freshlyCreatedComplaintResponse = new ComplaintResponse();
        freshlyCreatedComplaintResponse.id = 1;
        freshlyCreatedComplaintResponse.isCurrentlyLocked = true;
        freshlyCreatedComplaintResponse.complaint = unhandledComplaint;

        const resolveStub = sinon.stub(injectedComplaintResponseService, 'resolveComplaint').returns(
            of(
                new HttpResponse({
                    body: freshlyCreatedComplaintResponse,
                    status: 201,
                }),
            ),
        );

        complaintForTutorComponentFixture.detectChanges();

        const rejectComplaintButton = complaintForTutorComponentFixture.debugElement.query(By.css('#rejectComplaintButton')).nativeElement;
        rejectComplaintButton.click();

        expect(resolveStub).to.have.been.called;
    });
});
