import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';

import { AssessmentComplaintAlertComponent } from 'app/assessment-shared/assessment-complaint-alert/assessment-complaint-alert.component';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Complaint, ComplaintType } from 'app/entities/complaint/complaint.model';

describe('AssessmentComplaintAlertComponent', () => {
    let component: AssessmentComplaintAlertComponent;
    let fixture: ComponentFixture<AssessmentComplaintAlertComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            declarations: [AssessmentComplaintAlertComponent],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(AssessmentComplaintAlertComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should not show anything without a complaint', () => {
        expect(fixture.debugElement.children).toEqual([]);

        const alertNode = fixture.debugElement.children[0];
        expect(alertNode).toBeFalsy();
    });

    function setComplaintOfType(type: ComplaintType): void {
        const complaint = new Complaint();
        complaint.complaintType = type;
        component.complaint = complaint;
        fixture.detectChanges();
    }

    it('should show complaint alert text for complaint', () => {
        setComplaintOfType(ComplaintType.COMPLAINT);

        const alertNode = fixture.debugElement.children[0];
        expect(alertNode).toBeTruthy();
        expect(alertNode.nativeElement.textContent).toContain('artemisApp.complaint.hint');
    });

    it('should show complaint alert text for more feedback request', () => {
        setComplaintOfType(ComplaintType.MORE_FEEDBACK);

        const alertNode = fixture.debugElement.children[0];
        expect(alertNode).toBeTruthy();
        expect(alertNode.nativeElement.textContent).toContain('artemisApp.moreFeedback.hint');
    });
});
