import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
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
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { provideHttpClient } from '@angular/common/http';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';

describe('UnreferencedFeedbackComponent', () => {
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

    describe('grading instruction selection', () => {
        const documentationInstruction = { id: 1, credits: 4, feedback: 'documented' } as GradingInstruction;
        const cameraInstruction = { id: 2, credits: -2, feedback: 'camera' } as GradingInstruction;
        const criteria = [
            { id: 1, title: 'Documentation', structuredGradingInstructions: [documentationInstruction] } as GradingCriterion,
            { id: 2, title: 'Camera', structuredGradingInstructions: [cameraInstruction] } as GradingCriterion,
        ];

        beforeEach(() => {
            fixture.componentRef.setInput('gradingCriteria', criteria);
        });

        it('should register itself as the selection host while it is editable', () => {
            const service = TestBed.inject(GradingInstructionSelectionService);
            fixture.componentRef.setInput('readOnly', false);
            fixture.detectChanges();

            expect(service.isSelectable()).toBe(true);

            fixture.destroy();
            expect(service.isSelectable()).toBe(false);
        });

        it('should not register a read-only list', () => {
            fixture.componentRef.setInput('readOnly', true);
            fixture.detectChanges();

            expect(TestBed.inject(GradingInstructionSelectionService).isSelectable()).toBe(false);
        });

        it('should add a feedback linked to the instruction when it is applied', () => {
            comp.applyInstruction(documentationInstruction);

            expect(comp.unreferencedFeedback).toHaveLength(1);
            expect(comp.unreferencedFeedback[0].gradingInstruction).toBe(documentationInstruction);
            expect(comp.unreferencedFeedback[0].credits).toBe(4);
            expect(comp.unreferencedFeedback[0].type).toBe(FeedbackType.MANUAL_UNREFERENCED);
            expect(comp.appliedInstructionIds()).toEqual(new Set([1]));
            expect(comp.appliedInstructionCounts()).toEqual(new Map([[1, 1]]));
        });

        it('should remove every feedback of the instruction when it is un-applied', () => {
            comp.applyInstruction(documentationInstruction);
            comp.applyInstruction(documentationInstruction);
            comp.applyInstruction(cameraInstruction);

            comp.unapplyInstruction(documentationInstruction);

            expect(comp.unreferencedFeedback).toHaveLength(1);
            expect(comp.unreferencedFeedback[0].gradingInstruction).toBe(cameraInstruction);
            expect(comp.appliedInstructionIds()).toEqual(new Set([2]));
            expect(comp.appliedInstructionCounts()).toEqual(new Map([[2, 1]]));
        });

        it('should group the feedback by criterion, with uncategorized feedback last', () => {
            comp.applyInstruction(cameraInstruction);
            comp.applyInstruction(documentationInstruction);
            comp.addUnreferencedFeedback();

            const groups = comp.feedbackGroups();
            expect(groups.map((group) => group.title)).toEqual(['Camera', 'Documentation', 'artemisApp.assessment.detail.otherFeedback']);
            expect(groups.map((group) => group.points)).toEqual([-2, 4, 0]);
            expect(groups[2].translateTitle).toBe(true);
            expect(comp.showGroupHeaders()).toBe(true);
        });

        it('should not show a group header for a single uncategorized block', () => {
            fixture.componentRef.setInput('gradingCriteria', []);
            comp.addUnreferencedFeedback();

            expect(comp.feedbackGroups()).toHaveLength(1);
            expect(comp.showGroupHeaders()).toBe(false);
        });

        it('should summarize awarded, deducted and resulting points', () => {
            comp.applyInstruction(documentationInstruction);
            comp.applyInstruction(cameraInstruction);

            expect(comp.pointsSummary()).toEqual({ awarded: 4, deducted: -2, total: 2 });
        });
    });
});
