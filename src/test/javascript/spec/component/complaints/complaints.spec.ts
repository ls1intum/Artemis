import { async, ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import * as chai from 'chai';
import { BrowserModule, By } from '@angular/platform-browser';
import { ComplaintService } from 'app/complaints/complaint.service';
import { MockComplaintResponse, MockComplaintService } from '../../mocks/mock-complaint.service';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { AlertService } from 'app/core/alert/alert.service';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { DebugElement } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { ComplaintsComponent } from 'app/complaints/complaints.component';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const expect = chai.expect;
describe('ComplaintsComponent', () => {
    let comp: ComplaintsComponent;
    let fixture: ComponentFixture<ComplaintsComponent>;
    let debugElement: DebugElement;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, MomentModule],
            declarations: [ComplaintsComponent],
            providers: [
                {
                    provide: ComplaintService,
                    useClass: MockComplaintService,
                },
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
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
