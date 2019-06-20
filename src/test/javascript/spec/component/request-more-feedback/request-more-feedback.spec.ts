import { ComponentFixture, TestBed, async, tick, fakeAsync } from '@angular/core/testing';
import * as chai from 'chai';
import { RequestMoreFeedbackComponent } from 'app/request-more-feedback';
import { By, BrowserModule } from '@angular/platform-browser';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { MockComplaintResponse, MockComplaintService } from '../../mocks/mock-complaint.service';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { ArTEMiSSharedModule } from 'app/shared';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { DebugElement } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';

const expect = chai.expect;
describe('RequestMoreFeedbackComponent', () => {
    let comp: RequestMoreFeedbackComponent;
    let fixture: ComponentFixture<RequestMoreFeedbackComponent>;
    let debugElement: DebugElement;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [BrowserModule, ArTEMiSSharedModule, MomentModule, ClipboardModule, HttpClientModule],
            declarations: [RequestMoreFeedbackComponent],
            providers: [
                {
                    provide: ComplaintService,
                    useClass: MockComplaintService,
                },
                {
                    provide: JhiAlertService,
                    useClass: MockAlertService,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(RequestMoreFeedbackComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    }));

    it('should initialize with correct values for complaints service', fakeAsync(() => {
        let textarea: HTMLTextAreaElement = debugElement.query(By.css('#complainTextArea')).nativeElement;
        expect(textarea.value).to.be.equal('');
        fixture.detectChanges();
        expect(comp.complaintText).to.be.equal(MockComplaintResponse.body.complaintText);
        expect(comp.alreadySubmitted).to.be.true;
        expect(comp.submittedDate).to.be.undefined;
        expect(comp.accepted).to.be.undefined;
        expect(comp.handled).to.be.false;
        tick();

        textarea = debugElement.query(By.css('#complainTextArea')).nativeElement;
        expect(textarea.disabled).to.be.true;
        expect(textarea.value).to.be.equal(MockComplaintResponse.body.complaintText);
        expect(textarea.readOnly).to.be.true;
    }));
});
