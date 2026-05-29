import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { By } from '@angular/platform-browser';
import { UnreferencedFeedbackDetailStubComponent } from 'test/helpers/stubs/exercise/unreferenced-feedback-detail-stub.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { UnreferencedFeedbackDetailComponent } from 'app/assessment/manage/unreferenced-feedback-detail/unreferenced-feedback-detail.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideHttpClient } from '@angular/common/http';

describe('UnreferencedFeedbackComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: UnreferencedFeedbackComponent;
    let fixture: ComponentFixture<UnreferencedFeedbackComponent>;
    let sgiService: StructuredGradingCriterionService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UnreferencedFeedbackComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, { provide: DialogService, useClass: MockDialogService }, provideHttpClient()],
        })
            .overrideComponent(UnreferencedFeedbackComponent, {
                remove: { imports: [TranslateDirective, UnreferencedFeedbackDetailComponent] },
                add: { imports: [MockDirective(TranslateDirective), UnreferencedFeedbackDetailStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(UnreferencedFeedbackComponent);
        comp = fixture.componentInstance;
        sgiService = TestBed.inject(StructuredGradingCriterionService);
    });

    it('should validate feedback', () => {
        comp.validateFeedback();
        expect(comp.assessmentsAreValid).toBe(false);

        const feedback = new Feedback();
        feedback.credits = undefined;
        comp.unreferencedFeedback = [...comp.unreferencedFeedback, feedback];

        fixture.changeDetectorRef.detectChanges();
        comp.validateFeedback();
        expect(comp.assessmentsAreValid).toBe(false);

        feedback.credits = 1;
        fixture.changeDetectorRef.detectChanges();

        comp.validateFeedback();
        expect(comp.assessmentsAreValid).toBe(true);
    });

    it('should add unreferenced feedback', () => {
        fixture.componentRef.setInput('addReferenceIdForExampleSubmission', true);
        comp.addUnreferencedFeedback();

        expect(comp.unreferencedFeedback).toHaveLength(1);
        expect(comp.unreferencedFeedback[0].reference).toBeDefined();

        fixture.changeDetectorRef.detectChanges();
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

    it('should add unreferenced feedback on dropping assessment instruction', () => {
        const instruction = { id: 1, credits: 2, feedback: 'test', gradingScale: 'good', instructionDescription: 'description of instruction', usageCount: 0 };
        comp.unreferencedFeedback = [];
        vi.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation((feedback) => {
            feedback.gradingInstruction = instruction;
            feedback.credits = instruction.credits;
        });

        // Call spy function with empty event
        comp.createAssessmentOnDrop(new Event(''));
        expect(comp.unreferencedFeedback).toHaveLength(1);
        expect(comp.unreferencedFeedback[0].gradingInstruction).toBe(instruction);
        expect(comp.unreferencedFeedback[0].credits).toBe(instruction.credits);
    });

    it('should convert an accepted feedback suggestion to a marked manual feedback', () => {
        const suggestion = { text: 'FeedbackSuggestion:', detailText: 'test', type: FeedbackType.AUTOMATIC };
        comp.feedbackSuggestions.set([suggestion]);
        comp.acceptSuggestion(suggestion);
        expect(comp.feedbackSuggestions()).toHaveLength(0);
        expect(comp.unreferencedFeedback).toEqual([
            {
                text: 'FeedbackSuggestion:accepted:',
                detailText: 'test',
                type: FeedbackType.MANUAL_UNREFERENCED,
            },
        ]);
    });

    it('should only replace feedback on drop, not add another one', () => {
        vi.spyOn(sgiService, 'updateFeedbackWithStructuredGradingInstructionEvent').mockImplementation(() => {});
        comp.createAssessmentOnDrop(new Event(''));
        fixture.changeDetectorRef.detectChanges();

        const unreferencedFeedbackDetailDebugElement = fixture.debugElement.query(By.css('jhi-unreferenced-feedback-detail'));
        const unreferencedFeedbackDetailComp: UnreferencedFeedbackDetailStubComponent = unreferencedFeedbackDetailDebugElement.componentInstance;

        const createAssessmentOnDropStub: ReturnType<typeof vi.spyOn> = vi.spyOn(comp, 'createAssessmentOnDrop');
        const updateFeedbackOnDropStub: ReturnType<typeof vi.spyOn> = vi.spyOn(unreferencedFeedbackDetailComp, 'updateFeedbackOnDrop');

        const dropEvent = new Event('drop', { bubbles: true, cancelable: true });
        unreferencedFeedbackDetailDebugElement.nativeElement.querySelector('div').dispatchEvent(dropEvent);
        fixture.changeDetectorRef.detectChanges();

        expect(updateFeedbackOnDropStub).toHaveBeenCalledOnce();
        // do not propagate the event to the parent component
        expect(createAssessmentOnDropStub).not.toHaveBeenCalled();
    });

    it('should remove discarded suggestions', () => {
        const suggestion = { text: 'FeedbackSuggestion:', detailText: 'test', type: FeedbackType.AUTOMATIC };
        comp.feedbackSuggestions.set([suggestion]);
        comp.discardSuggestion(suggestion);
        expect(comp.feedbackSuggestions()).toHaveLength(0);
    });
});
