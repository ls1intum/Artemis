import { async, ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import * as chai from 'chai';
import { By } from '@angular/platform-browser';
import { ComplaintService } from 'app/complaints/complaint.service';
import { MockComplaintResponse, MockComplaintService } from '../../helpers/mocks/service/mock-complaint.service';

import { MomentModule } from 'ngx-moment';
import { DebugElement } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ComplaintsComponent } from 'app/complaints/complaints.component';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Exercise } from 'app/entities/exercise.model';

const expect = chai.expect;
describe('ComplaintsComponent', () => {
    const exercise: Exercise = { id: 1, teamMode: false } as Exercise;
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
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintsComponent);
                comp = fixture.componentInstance;
                comp.exercise = exercise;
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
        comp.submissionId = 111;
        comp.ngOnInit();
        fixture.detectChanges();
        expect(comp.alreadySubmitted).to.be.true;
        expect(comp.submittedDate).to.be.undefined;
        expect(comp.accepted).to.be.true;
        expect(comp.handled).to.be.true;

        expect(debugElement.query(By.css('.text-light.bg-success'))).to.be.not.null;
    });
});
