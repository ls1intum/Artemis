import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { By } from '@angular/platform-browser';
import { UnreferencedFeedbackDetailStubComponent } from '../../helpers/stubs/unreferenced-feedback-detail-stub.component';

describe('UnreferencedFeedbackComponent', () => {
    let comp: UnreferencedFeedbackComponent;
    let fixture: ComponentFixture<UnreferencedFeedbackComponent>;
    let sgiService: StructuredGradingCriterionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [UnreferencedFeedbackComponent, UnreferencedFeedbackDetailStubComponent, MockPipe(ArtemisTranslatePipe)],
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
        comp.updateFeedback(feedback);

        expect(comp.unreferencedFeedback).toHaveLength(1);
        expect(comp.unreferencedFeedback[0].text).toBe(newFeedbackText);
    });

    it('should add unreferenced feedback if it does not exist when updating', () => {
        const feedback = { text: 'NewFeedback', credits: 3 } as Feedback;
        comp.unreferencedFeedback = [];
        comp.updateFeedback(feedback);

        expect(comp.unreferencedFeedback).toHaveLength(1);
        expect(comp.unreferencedFeedback[0].text).toBe(feedback.text);
    });

    it('should delete unreferenced feedback', () => {
        const feedback = { text: 'NewFeedback', credits: 3 } as Feedback;
        comp.unreferencedFeedback = [feedback];
        comp.deleteFeedback(feedback);

        expect(comp.unreferencedFeedback).toHaveLength(0);
    });

    describe('Drag and drop assessment criteria', () => {
        let instruction: GradingInstruction;
        beforeEach(() => {
            instruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description of instruction', usageCount: 0 };
            comp.unreferencedFeedback = [];
            jest.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation((feedback) => {
                feedback.gradingInstruction = instruction;
                feedback.credits = instruction.credits;
            });
        });

        it('should add unreferenced feedback on dropping assessment instruction', () => {
            // Call spy function with empty event
            comp.createAssessmentOnDrop(new Event(''));
            expect(comp.unreferencedFeedback).toHaveLength(1);
            expect(comp.unreferencedFeedback[0].gradingInstruction).toBe(instruction);
            expect(comp.unreferencedFeedback[0].credits).toBe(instruction.credits);
        });

        it('should only replace feedback on drop, not add another one', () => {
            comp.createAssessmentOnDrop(new Event(''));
            fixture.detectChanges();

            const unreferencedFeedbackDetailDebugElement = fixture.debugElement.query(By.directive(UnreferencedFeedbackDetailStubComponent));
            const unreferencedFeedbackDetailComp: UnreferencedFeedbackDetailStubComponent = unreferencedFeedbackDetailDebugElement.componentInstance;

            const createAssessmentOnDropStub: jest.SpyInstance = jest.spyOn(comp, 'createAssessmentOnDrop');
            const updateFeedbackOnDropStub: jest.SpyInstance = jest.spyOn(unreferencedFeedbackDetailComp, 'updateFeedbackOnDrop');

            const dropEvent = new Event('drop', { bubbles: true, cancelable: true });
            unreferencedFeedbackDetailDebugElement.nativeElement.querySelector('div').dispatchEvent(dropEvent); // Event auf Subkomponente auslösen
            fixture.detectChanges();

            expect(updateFeedbackOnDropStub).toHaveBeenCalledOnce();
            expect(createAssessmentOnDropStub).not.toHaveBeenCalled();
        });
    });

    it('should remove discarded suggestions', () => {
        const suggestion = { text: 'FeedbackSuggestion:', detailText: 'test', type: FeedbackType.AUTOMATIC };
        comp.feedbackSuggestions = [suggestion];
        comp.discardSuggestion(suggestion);
        expect(comp.feedbackSuggestions).toBeEmpty();
    });
});
