import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssessmentComplaintAlertComponent } from 'app/assessment/manage/assessment-complaint-alert/assessment-complaint-alert.component';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('AssessmentComplaintAlertComponent', () => {
    let component: AssessmentComplaintAlertComponent;
    let fixture: ComponentFixture<AssessmentComplaintAlertComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentComplaintAlertComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
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
        fixture.componentRef.setInput('complaint', complaint);
        fixture.detectChanges();
    }

    it('should show complaint alert text for complaint', () => {
        setComplaintOfType(ComplaintType.COMPLAINT);

        const alertNode = fixture.debugElement.query(By.css('.alert-info'));
        expect(alertNode).toBeTruthy();
        expect(alertNode.attributes['jhiTranslate']).toBe('artemisApp.complaint.hint');
    });

    it('should show complaint alert text for more feedback request', () => {
        setComplaintOfType(ComplaintType.MORE_FEEDBACK);

        const alertNode = fixture.debugElement.query(By.css('.alert-info'));
        expect(alertNode).toBeTruthy();
        expect(alertNode.attributes['jhiTranslate']).toBe('artemisApp.moreFeedback.hint');
    });
});
