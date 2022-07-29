import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockComponent } from 'ng-mocks';
import { Feedback } from 'app/entities/feedback.model';
import { AssessmentDetailComponent } from 'app/assessment/assessment-detail/assessment-detail.component';

describe('UnreferencedFeedbackComponent', () => {
    let comp: UnreferencedFeedbackComponent;
    let fixture: ComponentFixture<UnreferencedFeedbackComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [UnreferencedFeedbackComponent, MockPipe(ArtemisTranslatePipe), MockComponent(AssessmentDetailComponent)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UnreferencedFeedbackComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should validate feedback', () => {
        comp.validateFeedback();
        expect(comp.assessmentsAreValid).toBeFalse();

        const feedback = new Feedback();
        feedback.credits = undefined;
        comp.unreferencedFeedback.push(feedback);

        fixture.detectChanges();
        comp.validateFeedback();
        expect(comp.assessmentsAreValid).toBeFalse();

        feedback.credits = 1;
        fixture.detectChanges();

        comp.validateFeedback();
        expect(comp.assessmentsAreValid).toBeTrue();
    });

    it('should add unreferenced feedback', () => {
        comp.addReferenceIdForExampleSubmission = true;
        comp.addUnreferencedFeedback();

        expect(comp.unreferencedFeedback).toHaveLength(1);
        expect(comp.unreferencedFeedback[0].reference).toBeDefined();

        fixture.detectChanges();
        comp.addUnreferencedFeedback();

        expect(comp.unreferencedFeedback).toHaveLength(2);
        expect(comp.unreferencedFeedback[1].reference).toBeDefined();
    });

    it('should update unreferenced feedback', () => {
        const feedback = { text: 'NewFeedback', credits: 3 } as Feedback;
        comp.unreferencedFeedback = [feedback];
        const newFeedbackText = 'updated text';
        feedback.text = newFeedbackText;
        comp.updateAssessment(feedback);

        expect(comp.unreferencedFeedback).toHaveLength(1);
        expect(comp.unreferencedFeedback[0].text).toBe(newFeedbackText);
    });

    it('should delete unreferenced feedback', () => {
        const feedback = { text: 'NewFeedback', credits: 3 } as Feedback;
        comp.unreferencedFeedback = [feedback];
        comp.deleteAssessment(feedback);

        expect(comp.unreferencedFeedback).toHaveLength(0);
    });
});
