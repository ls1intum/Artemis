import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';

import { AssessmentGeneralFeedbackComponent } from 'app/assessment-shared/assessment-general-feedback/assessment-general-feedback.component';
import { Feedback } from 'app/entities/feedback';

describe('AssessmentGeneralFeedbackComponent', () => {
    let component: AssessmentGeneralFeedbackComponent;
    let fixture: ComponentFixture<AssessmentGeneralFeedbackComponent>;
    let feedback: Feedback;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [AssessmentGeneralFeedbackComponent],
            imports: [FormsModule],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(AssessmentGeneralFeedbackComponent);
        component = fixture.componentInstance;
        feedback = component.feedback = new Feedback();
        feedback.detailText = 'Initial Detail Text';
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should emit on input', () => {
        spyOn(component.feedbackChange, 'emit');

        const textarea = fixture.debugElement.query(By.css('textarea')).nativeElement;
        textarea.value = 'foo bar';
        textarea.dispatchEvent(new Event('input'));
        fixture.detectChanges();

        expect(component.feedbackChange.emit).toHaveBeenCalledWith(Object.assign({}, feedback, { detailText: 'foo bar' }));

        expect(feedback.detailText).toEqual('Initial Detail Text');
    });
});
