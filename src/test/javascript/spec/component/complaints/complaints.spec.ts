import { ComponentFixture, TestBed, async, tick, fakeAsync } from '@angular/core/testing';
import * as chai from 'chai';
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
import { ComplaintsComponent } from 'app/complaints';

const expect = chai.expect;
describe('ComplaintsComponent', () => {
    let comp: ComplaintsComponent;
    let fixture: ComponentFixture<ComplaintsComponent>;
    let debugElement: DebugElement;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [BrowserModule, ArTEMiSSharedModule, MomentModule, ClipboardModule, HttpClientModule],
            declarations: [ComplaintsComponent],
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
                fixture = TestBed.createComponent(ComplaintsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    }));

    it('should initialize with correct values for complaints service', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp.complaintText).to.be.equal(MockComplaintResponse.body.complaintText);
        expect(comp.alreadySubmitted).to.be.true;
        expect(comp.submittedDate).to.be.undefined;
        expect(comp.accepted).to.be.undefined;
        expect(comp.handled).to.be.false;
        tick(1000);
        const textarea = debugElement.query(By.css('#complainTextArea')).nativeElement;
        expect(textarea.disabled).to.be.true;
        expect(textarea.value).to.be.equal(MockComplaintResponse.body.complaintText);
        expect(textarea.readOnly).to.be.true;
    }));
    it('should show accepted message when complaint is accepted', () => {
        comp.resultId = 111;
        comp.ngOnInit();
        fixture.detectChanges();
        expect(comp.alreadySubmitted).to.be.true;
        expect(comp.submittedDate).to.be.undefined;
        expect(comp.accepted).to.be.true;
        expect(comp.handled).to.be.true;

        expect(debugElement.query(By.css('.text-light.bg-success'))).to.be.not.null;
    });
});
