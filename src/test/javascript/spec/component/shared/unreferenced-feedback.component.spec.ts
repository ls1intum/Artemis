import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockComponent } from 'ng-mocks';
import { Feedback } from 'app/entities/feedback.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { AssessmentDetailComponent } from 'app/assessment/assessment-detail/assessment-detail.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';

describe('UnreferencedFeedbackComponent', () => {
    let comp: UnreferencedFeedbackComponent;
    let fixture: ComponentFixture<UnreferencedFeedbackComponent>;
    let sgiService: StructuredGradingCriterionService;

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
                sgiService = fixture.debugElement.injector.get(StructuredGradingCriterionService);
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

        expect(comp.unreferencedFeedback.length).toBe(1);
        expect(comp.unreferencedFeedback[0].reference).not.toBe(undefined);

        fixture.detectChanges();
        comp.addUnreferencedFeedback();

        expect(comp.unreferencedFeedback.length).toBe(2);
        expect(comp.unreferencedFeedback[1].reference).not.toBe(undefined);
    });

    it('should update unreferenced feedback', () => {
        const feedback = { text: 'NewFeedback', credits: 3 } as Feedback;
        comp.unreferencedFeedback = [feedback];
        const newFeedbackText = 'updated text';
        feedback.text = newFeedbackText;
        comp.updateAssessment(feedback);

        expect(comp.unreferencedFeedback.length).toBe(1);
        expect(comp.unreferencedFeedback[0].text).toBe(newFeedbackText);
    });

    it('should delete unreferenced feedback', () => {
        const feedback = { text: 'NewFeedback', credits: 3 } as Feedback;
        comp.unreferencedFeedback = [feedback];
        comp.deleteAssessment(feedback);

        expect(comp.unreferencedFeedback.length).toBe(0);
    });

    it('should add unreferenced feedback on dropping assessment instruction', () => {
        const instruction: GradingInstruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description of instruction', usageCount: 0 };
        comp.unreferencedFeedback = [];
        jest.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation(() => {
            comp.unreferencedFeedback[0].gradingInstruction = instruction;
            comp.unreferencedFeedback[0].credits = instruction.credits;
        });
        // Call spy function with empty event
        comp.createAssessmentOnDrop(new Event(''));
        expect(comp.unreferencedFeedback.length).toBe(1);
        expect(comp.unreferencedFeedback[0].gradingInstruction).toBe(instruction);
        expect(comp.unreferencedFeedback[0].credits).toBe(instruction.credits);
    });
});
