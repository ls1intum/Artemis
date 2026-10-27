/**
 * Vitest tests for TextAssessmentAreaComponent.
 * Tests the text assessment area functionality with signal-based inputs.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorHandler } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TextAssessmentAreaComponent } from 'app/text/manage/assess/text-assessment-area/text-assessment-area.component';
import { TextBlockAssessmentCardComponent } from 'app/text/manage/assess/textblock-assessment-card/text-block-assessment-card.component';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { By } from '@angular/platform-browser';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';
import { ManualTextblockSelectionComponent } from 'app/text/manage/assess/manual-textblock-selection/manual-textblock-selection.component';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { triggerDeleteDialogDelete } from 'app/shared-ui/delete-dialog/delete-dialog.model';

describe('TextAssessmentAreaComponent', () => {
    let component: TextAssessmentAreaComponent;
    let fixture: ComponentFixture<TextAssessmentAreaComponent>;

    const submission = { id: 1, text: 'Test submission text' } as TextSubmission;

    const newBlock = (startIndex: number, endIndex: number): TextBlock => {
        const block = new TextBlock();
        block.startIndex = startIndex;
        block.endIndex = endIndex;
        block.setTextFromSubmission(submission);
        return block;
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TextAssessmentAreaComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ id: 123 }) },
                MockProvider(StructuredGradingCriterionService),
                MockProvider(TextAssessmentAnalytics),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TextAssessmentAreaComponent);
        component = fixture.componentInstance;
        // Set required inputs
        fixture.componentRef.setInput('submission', submission);
        fixture.componentRef.setInput('textBlockRefs', []);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should add a TextblockAssessmentCardComponent for each TextBlockRef', () => {
        const textBlockRefs = [
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
            TextBlockRef.new(),
        ];

        for (let i = 0; i < textBlockRefs.length; i++) {
            // Use setInput for model signals
            fixture.componentRef.setInput('textBlockRefs', textBlockRefs.slice(0, i));
            fixture.changeDetectorRef.detectChanges();

            const all = fixture.debugElement.queryAll(By.directive(TextBlockAssessmentCardComponent));
            expect(all).toHaveLength(i);
        }
    });

    it('should toggle on alt', () => {
        const spyOnAlt = vi.spyOn(component, 'onAltToggle');
        const eventMock = new KeyboardEvent('keydown', { key: 'Alt' });

        component.onAltToggle(eventMock, false);
        expect(spyOnAlt).toHaveBeenCalledOnce();
        expect(component.autoTextBlockAssessment()).toBe(false);
    });

    it('should not toggle on alt when manual selection forbidden', () => {
        const spyOnAlt = vi.spyOn(component, 'onAltToggle');
        const eventMock = new KeyboardEvent('keydown', { key: 'Alt' });
        // Use setInput for input signals
        fixture.componentRef.setInput('allowManualBlockSelection', false);
        fixture.detectChanges();
        component.onAltToggle(eventMock, false);
        expect(spyOnAlt).toHaveBeenCalledOnce();
        expect(component.autoTextBlockAssessment()).toBe(true);
    });

    it('should add TextBlockRef if text block is added manually', () => {
        const initialRefs = [TextBlockRef.new(), TextBlockRef.new(), TextBlockRef.new(), TextBlockRef.new()];
        fixture.componentRef.setInput('textBlockRefs', initialRefs);
        fixture.detectChanges();

        vi.spyOn(component.textBlockRefsAddedRemoved, 'emit');

        component.addTextBlockRef(TextBlockRef.new());
        fixture.changeDetectorRef.detectChanges();

        expect(component.textBlockRefsAddedRemoved.emit).toHaveBeenCalledOnce();
        expect(component.textBlockRefs()).toHaveLength(5);
    });

    it('should preserve assessment cards across a re-match that re-wraps the same text blocks', () => {
        // The parent re-matches blocks with feedback on many occasions (validating feedback, Athena suggestions, etc.),
        // which creates NEW TextBlockRef wrappers around the SAME TextBlock objects. The cards must be reused rather than
        // destroyed and recreated, otherwise the assessor's in-progress feedback editing gets churned ("blocks mixed up").
        const block1 = new TextBlock();
        block1.startIndex = 0;
        block1.endIndex = 4;
        block1.setTextFromSubmission(submission);
        const block2 = new TextBlock();
        block2.startIndex = 5;
        block2.endIndex = 9;
        block2.setTextFromSubmission(submission);

        fixture.componentRef.setInput('textBlockRefs', [new TextBlockRef(block1), new TextBlockRef(block2)]);
        fixture.changeDetectorRef.detectChanges();
        const cardsBefore = fixture.debugElement.queryAll(By.directive(TextBlockAssessmentCardComponent)).map((card) => card.nativeElement);
        expect(cardsBefore).toHaveLength(2);

        // Re-match: brand-new wrappers, but around the same block objects.
        fixture.componentRef.setInput('textBlockRefs', [new TextBlockRef(block1), new TextBlockRef(block2)]);
        fixture.changeDetectorRef.detectChanges();
        const cardsAfter = fixture.debugElement.queryAll(By.directive(TextBlockAssessmentCardComponent)).map((card) => card.nativeElement);

        expect(cardsAfter).toHaveLength(2);
        // Same DOM nodes => Angular reused the cards instead of recreating them (this would fail when tracking by the ref wrapper).
        expect(cardsAfter[0]).toBe(cardsBefore[0]);
        expect(cardsAfter[1]).toBe(cardsBefore[1]);
    });

    it('should reuse the existing cards and only add one when a block is inserted via a re-match', () => {
        const block1 = newBlock(0, 4);
        const block2 = newBlock(10, 14);
        fixture.componentRef.setInput('textBlockRefs', [new TextBlockRef(block1), new TextBlockRef(block2)]);
        fixture.changeDetectorRef.detectChanges();
        const cardsBefore = fixture.debugElement.queryAll(By.directive(TextBlockAssessmentCardComponent)).map((card) => card.nativeElement);
        expect(cardsBefore).toHaveLength(2);

        // A new block is inserted between the two (e.g. a manual block / split), re-wrapping the same block1/block2.
        const inserted = newBlock(5, 9);
        fixture.componentRef.setInput('textBlockRefs', [new TextBlockRef(block1), new TextBlockRef(inserted), new TextBlockRef(block2)]);
        fixture.changeDetectorRef.detectChanges();
        const cardsAfter = fixture.debugElement.queryAll(By.directive(TextBlockAssessmentCardComponent)).map((card) => card.nativeElement);

        expect(cardsAfter).toHaveLength(3);
        // Pin the exact reuse: block1's card stays at index 0, block2's card is pushed to index 2, and only the inserted
        // block gets a fresh card at index 1. (By.directive returns nodes in document order; the sorted blocks
        // 0-4 / 5-9 / 10-14 keep this positional mapping.) Asserting only membership would still pass under a
        // track-$index regression that repurposes the wrong card.
        expect(cardsAfter[0]).toBe(cardsBefore[0]);
        expect(cardsAfter[2]).toBe(cardsBefore[1]);
        expect(cardsBefore).not.toContain(cardsAfter[1]);
    });

    it('should reuse surviving cards and drop only the removed one when a block is deleted via a re-match', () => {
        const block1 = newBlock(0, 4);
        const block2 = newBlock(5, 9);
        const block3 = newBlock(10, 14);
        fixture.componentRef.setInput('textBlockRefs', [new TextBlockRef(block1), new TextBlockRef(block2), new TextBlockRef(block3)]);
        fixture.changeDetectorRef.detectChanges();
        const cardsBefore = fixture.debugElement.queryAll(By.directive(TextBlockAssessmentCardComponent)).map((card) => card.nativeElement);
        expect(cardsBefore).toHaveLength(3);

        // The middle block is removed; the survivors are re-wrapped in fresh refs.
        fixture.componentRef.setInput('textBlockRefs', [new TextBlockRef(block1), new TextBlockRef(block3)]);
        fixture.changeDetectorRef.detectChanges();
        const cardsAfter = fixture.debugElement.queryAll(By.directive(TextBlockAssessmentCardComponent)).map((card) => card.nativeElement);

        expect(cardsAfter).toHaveLength(2);
        // Pin the exact survivors: block1's and block3's cards are reused (in document order) and block2's (the removed
        // middle) card is gone. Asserting only membership would still pass if the wrong original card survived (e.g. the
        // middle card kept and an end card dropped under a track-$index regression).
        expect(cardsAfter[0]).toBe(cardsBefore[0]);
        expect(cardsAfter[1]).toBe(cardsBefore[2]);
        expect(cardsAfter).not.toContain(cardsBefore[1]);
    });

    it('should remove TextBlockRef if text block is deleted', () => {
        const initialRefs = [TextBlockRef.new(), TextBlockRef.new(), TextBlockRef.new(), TextBlockRef.new()];
        fixture.componentRef.setInput('textBlockRefs', initialRefs);
        fixture.detectChanges();

        vi.spyOn(component.textBlockRefsAddedRemoved, 'emit');

        component.removeTextBlockRef(component.textBlockRefs()[0]);
        fixture.changeDetectorRef.detectChanges();

        expect(component.textBlockRefsAddedRemoved.emit).toHaveBeenCalledOnce();
        expect(component.textBlockRefs()).toHaveLength(3);
    });

    describe('focus after manual selection', () => {
        const pressAlt = (type: 'keydown' | 'keyup') => {
            document.dispatchEvent(new KeyboardEvent(type, { key: 'Alt' }));
            fixture.detectChanges();
        };

        const feedbackTextareas = (): HTMLTextAreaElement[] => Array.from(fixture.nativeElement.querySelectorAll('textarea.unified-feedback-detail-input'));

        const deleteFeedbackOfNewBlock = () => {
            // Escape opens the delete confirmation for an assessment feedback card.
            const unifiedFeedback = fixture.debugElement.query(By.directive(UnifiedFeedbackComponent));
            unifiedFeedback.nativeElement.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
            fixture.detectChanges();
        };

        const selectWordsManually = () => {
            pressAlt('keydown');
            const manualSelection = fixture.debugElement.query(By.directive(ManualTextblockSelectionComponent)).componentInstance as ManualTextblockSelectionComponent;
            manualSelection.handleTextSelection([
                { word: 'Test', index: 0 },
                { word: 'submission', index: 5 },
            ]);
            fixture.detectChanges();
        };

        beforeEach(() => {
            // one automatic block without feedback that covers the whole submission, so the words can be selected manually
            fixture.componentRef.setInput('textBlockRefs', [new TextBlockRef(newBlock(0, submission.text!.length))]);
            fixture.detectChanges();
        });

        it('should focus the feedback textarea of the new block once Alt is released', async () => {
            selectWordsManually();
            pressAlt('keyup');
            await fixture.whenStable();

            expect(component.textBlockRefs()).toHaveLength(2);
            const textareas = feedbackTextareas();
            expect(textareas).toHaveLength(1);
            expect(document.activeElement).toBe(textareas[0]);
        });

        it('should not focus a feedback textarea when no text block was selected manually', async () => {
            pressAlt('keydown');
            pressAlt('keyup');
            await fixture.whenStable();

            expect(feedbackTextareas()).toHaveLength(0);
            expect(document.activeElement).toBe(document.body);
        });

        it('should not throw when the feedback of the new block is deleted while Alt is still held', async () => {
            const handleErrorSpy = vi.spyOn(TestBed.inject(ErrorHandler), 'handleError');
            const openDeleteDialog = vi.spyOn(TestBed.inject(DeleteDialogService), 'openDeleteDialog');
            selectWordsManually();
            deleteFeedbackOfNewBlock();
            triggerDeleteDialogDelete(openDeleteDialog.mock.calls[0][0].delete);
            fixture.detectChanges();
            expect(feedbackTextareas()).toHaveLength(0);

            pressAlt('keyup');
            await fixture.whenStable();

            expect(handleErrorSpy).not.toHaveBeenCalled();
            expect(feedbackTextareas()).toHaveLength(0);
            expect(document.activeElement).toBe(document.body);
            expect(component.blockToFocus()).toBeUndefined();
        });

        it('should not move the focus again after the feedback textarea was focused and the assessment is toggled read-only', async () => {
            selectWordsManually();
            pressAlt('keyup');
            await fixture.whenStable();
            feedbackTextareas()[0].blur();

            fixture.componentRef.setInput('readOnly', true);
            fixture.detectChanges();
            fixture.componentRef.setInput('readOnly', false);
            fixture.detectChanges();
            await fixture.whenStable();

            expect(document.activeElement).toBe(document.body);
        });

        it('should skip the focus without an error when the text block refs are replaced while Alt is still held', async () => {
            const handleErrorSpy = vi.spyOn(TestBed.inject(ErrorHandler), 'handleError');
            selectWordsManually();
            fixture.componentRef.setInput('textBlockRefs', [new TextBlockRef(newBlock(0, submission.text!.length))]);
            fixture.detectChanges();

            pressAlt('keyup');
            await fixture.whenStable();

            expect(handleErrorSpy).not.toHaveBeenCalled();
            expect(feedbackTextareas()).toHaveLength(0);
            expect(document.activeElement).toBe(document.body);
        });

        it('should not steal the focus again when Alt is pressed later on', async () => {
            selectWordsManually();
            pressAlt('keyup');
            await fixture.whenStable();
            const textarea = feedbackTextareas()[0];
            expect(document.activeElement).toBe(textarea);

            textarea.blur();
            pressAlt('keydown');
            pressAlt('keyup');
            await fixture.whenStable();

            expect(feedbackTextareas()).toHaveLength(1);
            expect(document.activeElement).toBe(document.body);
        });
    });
});
