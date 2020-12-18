import { async, ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { stub } from 'sinon';
import { of } from 'rxjs';
import { BrowserModule, By } from '@angular/platform-browser';
import { ComplaintService, EntityResponseType } from 'app/complaints/complaint.service';
import { MockComplaintService } from '../../helpers/mocks/service/mock-complaint.service';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { DebugElement } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { TranslateModule } from '@ngx-translate/core';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('ComplaintsForTutorComponent', () => {
    let comp: ComplaintsForTutorComponent;
    let fixture: ComponentFixture<ComplaintsForTutorComponent>;
    let debugElement: DebugElement;
    let complaintResponseService: ComplaintResponseService;

    const complaintResponse = new ComplaintResponse();
    complaintResponse.id = 1;
    complaintResponse.responseText = 'myResponse';

    const complaint = new Complaint();
    complaint.id = 12;
    complaint.accepted = true;
    complaint.complaintText = 'Random';

    const complaint2 = new Complaint();
    complaint2.id = 11;
    complaint2.complaintText = '';

    const complaint3 = new Complaint();
    complaint3.id = 10;
    complaint3.accepted = true;
    complaint3.complaintText = 'myComplain';
    complaint3.complaintType = ComplaintType.COMPLAINT;

    const moreFeedbackComplaint = new Complaint();
    moreFeedbackComplaint.id = 9;
    moreFeedbackComplaint.accepted = true;
    moreFeedbackComplaint.complaintText = 'feedbackComplaint';
    moreFeedbackComplaint.complaintType = ComplaintType.MORE_FEEDBACK;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [BrowserModule, ArtemisSharedModule, MomentModule, ClipboardModule, HttpClientModule, TranslateModule.forRoot()],
            declarations: [ComplaintsForTutorComponent],
            providers: [
                ComplaintResponseService,
                {
                    provide: ComplaintService,
                    useClass: MockComplaintService,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintsForTutorComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                complaintResponseService = fixture.debugElement.injector.get(ComplaintResponseService);
                comp.complaint = complaint;
                comp.isAllowedToRespond = true;
            });
    }));

    afterEach(() => {
        if (comp.complaintResponse) {
            comp.complaintResponse.complaint = undefined;
        }
    });

    it('should show alert and accept message when complaint is handled', () => {
        complaintResponse.complaint = complaint;

        stub(complaintResponseService, 'findByComplaintId').returns(of({ body: complaintResponse } as EntityResponseType));
        comp.ngOnInit();
        fixture.detectChanges();
        expect(comp.complaint.accepted).to.be.true;
        expect(comp.handled).to.be.true;
        expect(comp.complaintText).to.be.equal(complaint.complaintText);
        expect(comp.complaintResponse.complaint).to.be.equal(complaint);
        expect(debugElement.query(By.css('.alert.alert-info'))).to.not.be.null;
        expect(debugElement.query(By.css('.text-light.bg-success.small'))).to.not.be.null;
    });

    it('should not show alert and accept message when complaint is not handled', () => {
        comp.complaint = complaint2;
        comp.ngOnInit();
        fixture.detectChanges();
        expect(comp.handled).to.be.false;

        expect(debugElement.query(By.css('.alert.alert-info'))).to.be.null;
        expect(debugElement.query(By.css('.text-light.bg-success.small'))).to.be.null;
    });

    it('updateAssessment should set the complaint for the complaintResponse', () => {
        comp.complaint = complaint3;
        stub(complaintResponseService, 'findByComplaintId').returns(of({ body: complaintResponse } as EntityResponseType));
        fixture.detectChanges();

        expect(comp.complaintResponse.complaint).to.be.undefined;
        comp.respondToComplaint(true);
        expect(comp.complaintResponse.complaint).to.be.equal(complaint3);
    });

    it('updateAssessment of a more feedback complaint should create a copy of the complaint response', fakeAsync(() => {
        comp.complaint = moreFeedbackComplaint;
        const copiedComplaintResponse = new ComplaintResponse();
        copiedComplaintResponse.id = complaintResponse.id;
        copiedComplaintResponse.responseText = complaintResponse.responseText;

        stub(complaintResponseService, 'findByComplaintId').returns(of({ body: complaintResponse } as EntityResponseType));
        stub(complaintResponseService, 'create').returns(of({ body: copiedComplaintResponse } as EntityResponseType));
        fixture.detectChanges();

        expect(comp.complaintResponse.complaint).to.be.undefined;
        comp.respondToComplaint(true);
        tick(5000);
        expect(comp.complaintResponse.id).to.be.equal(copiedComplaintResponse.id);
    }));
});
