import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssessmentComplaintAlertComponent } from 'app/assessment/assessment-complaint-alert/assessment-complaint-alert.component';
import { ArtemisTestModule } from '../../test.module';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';

describe('AssessmentComplaintAlertComponent', () => {
    let component: AssessmentComplaintAlertComponent;
    let fixture: ComponentFixture<AssessmentComplaintAlertComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [AssessmentComplaintAlertComponent, TranslatePipeMock],
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
