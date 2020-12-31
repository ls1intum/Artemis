import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { BrowserModule, By } from '@angular/platform-browser';
import { ComplaintService } from 'app/complaints/complaint.service';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { DebugElement } from '@angular/core';
import { HttpClientModule, HttpResponse } from '@angular/common/http';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { TranslateModule } from '@ngx-translate/core';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { MockProvider } from 'ng-mocks';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { of } from 'rxjs';

chai.use(sinonChai);
const expect = chai.expect;

describe('ComplaintsForTutorComponent', () => {
    let comp: ComplaintsForTutorComponent;
    let fixture: ComponentFixture<ComplaintsForTutorComponent>;
    let debugElement: DebugElement;
    let complaintResponseService: ComplaintResponseService;
    let complaintService: ComplaintService;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [BrowserModule, ArtemisSharedModule, MomentModule, ClipboardModule, HttpClientModule, TranslateModule.forRoot()],
            declarations: [ComplaintsForTutorComponent],
            providers: [MockProvider(ComplaintResponseService), MockProvider(ComplaintService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintsForTutorComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                complaintResponseService = fixture.debugElement.injector.get(ComplaintResponseService);
                complaintService = fixture.debugElement.injector.get(ComplaintService);
            });
    }));

    afterEach(() => {
        sinon.restore();
    });

    it('should show "already handled" alert when handled complaint as input', () => {
        const handledComplaint = new Complaint();
        handledComplaint.complaintText = 'Lorem Ipsum';
        handledComplaint.accepted = true; // handled
        handledComplaint.complaintResponse = new ComplaintResponse();

        comp.complaint = handledComplaint;
        fixture.detectChanges();

        expect(comp.handled).to.be.true;
        expect(debugElement.query(By.css('.alert.alert-info'))).to.exist;
        expect(debugElement.query(By.css('.text-light.bg-success.small'))).to.exist;
    });

    it('should not show alert when unhandled complaint as input', () => {
        const unhandledComplaint = new Complaint();
        unhandledComplaint.accepted = undefined; // unhandled
        unhandledComplaint.complaintText = 'Lorem Ipsum';
        unhandledComplaint.complaintResponse = new ComplaintResponse();

        comp.complaint = unhandledComplaint;
        fixture.detectChanges();

        expect(comp.handled).to.be.false;
        expect(debugElement.query(By.css('.alert.alert-info'))).to.not.exist;
        expect(debugElement.query(By.css('.text-light.bg-success.small'))).to.not.exist;
    });

    it('should create a new complaint response when none is connected to complaint', () => {
        const complaintWithoutResponse = new Complaint();
        complaintWithoutResponse.accepted = undefined;
        complaintWithoutResponse.complaintText = 'Lorem Ipsum';
        complaintWithoutResponse.complaintResponse = undefined;
        comp.complaint = complaintWithoutResponse;
        const complaintResponseFromServer = new ComplaintResponse();
        complaintResponseFromServer.complaint = complaintWithoutResponse;

        const createComplaintResponseStub = sinon.stub(complaintResponseService, 'create').returns(
            of(
                new HttpResponse({
                    body: complaintResponseFromServer,
                    status: 200,
                }),
            ),
        );

        fixture.detectChanges();

        expect(createComplaintResponseStub).to.have.been.called;
        expect(comp.complaintResponse).to.deep.equal(complaintResponseFromServer);
    });

    it('should send update event for accepted complaint', () => {
        comp.isAllowedToRespond = true;
        const complaint = new Complaint();
        complaint.complaintType = ComplaintType.COMPLAINT;
        complaint.complaintText = 'Lorem Ipsum';
        complaint.complaintResponse = new ComplaintResponse();
        complaint.complaintResponse.complaint = complaint;
        complaint.complaintResponse.responseText = 'Accepted';
        comp.complaint = complaint;
        comp.complaintResponse = complaint.complaintResponse;

        fixture.detectChanges();

        const eventSpy = sinon.spy(comp.updateAssessmentAfterComplaint, 'emit');

        comp.respondToComplaint(true);
        expect(eventSpy).to.have.been.called;
    });

    it('should call update endpoint for denied complaint', () => {
        comp.isAllowedToRespond = true;
        const complaint = new Complaint();
        complaint.complaintType = ComplaintType.COMPLAINT;
        complaint.complaintText = 'Lorem Ipsum';
        complaint.complaintResponse = new ComplaintResponse();
        complaint.complaintResponse.complaint = complaint;
        complaint.complaintResponse.responseText = 'Accepted';
        comp.complaint = complaint;
        comp.complaintResponse = complaint.complaintResponse;

        fixture.detectChanges();

        const responseFromServer = new ComplaintResponse();
        responseFromServer.complaint = new Complaint();
        const updateStub = sinon.stub(complaintResponseService, 'update').returns(
            of(
                new HttpResponse({
                    body: responseFromServer,
                    status: 200,
                }),
            ),
        );
        comp.respondToComplaint(false);
        expect(updateStub).to.have.been.called;
    });
});
