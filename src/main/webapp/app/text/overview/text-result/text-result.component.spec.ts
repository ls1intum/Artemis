import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TextResultComponent } from './text-result.component';
import { TranslateModule } from '@ngx-translate/core';
import { UnifiedFeedbackComponent } from '../../../shared/components/unified-feedback/unified-feedback.component';
import { TextResultBlock } from './text-result-block';
import { TextBlock } from '../../shared/entities/text-block.model';

describe('TextResultComponent', () => {
    let component: TextResultComponent;
    let fixture: ComponentFixture<TextResultComponent>;

    const createResult = (submission: any, feedbacks: any[] = []): any => {
        return {
            feedbacks: feedbacks as any[],
            submission: submission as any,
        } as any;
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TextResultComponent, TranslateModule.forRoot()],
        }).compileComponents();

        fixture = TestBed.createComponent(TextResultComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should early return when no submission present', () => {
        const result = { feedbacks: [] } as any;
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        expect(component.submissionText).toBe('');
        expect(component.textResults).toHaveLength(0);
    });

    it('should render single plain block when there are no feedbacks', () => {
        const submission: any = { text: 'hello world' };
        const result = createResult(submission, []);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        expect(component.textResults).toHaveLength(1);
        expect(component.textResults[0].startIndex).toBe(0);
        expect(component.textResults[0].endIndex).toBe('hello world'.length);
        const spans = fixture.nativeElement.querySelectorAll('.white-space');
        expect(spans).toHaveLength(1);
        expect(spans[0].innerHTML).toContain('hello world');
    });

    it('should create a block for numeric reference feedback', () => {
        const submission: any = { text: 'abc' };
        const fb: any = { reference: '1', credits: 1, text: 'good' };
        const result = createResult(submission, [fb]);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        // Expect 3 blocks: ["a"], ["b" with feedback], ["c"]
        expect(component.textResults).toHaveLength(3);
        const feedbackBlock = component.textResults.find((b) => b.startIndex === 1 && b.endIndex === 2)!;
        expect(feedbackBlock.feedback).toBeDefined();
        expect(feedbackBlock.cssClass).toContain('positive-feedback');
        // Unified feedback should render
        const feedbackComponents = fixture.nativeElement.querySelectorAll('jhi-unified-feedback');
        expect(feedbackComponents).toHaveLength(1);
    });

    it('should create a block for substring reference feedback', () => {
        const submission: any = { text: 'hello world' };
        const fb: any = { reference: 'world', credits: -1, text: 'bad' };
        const result = createResult(submission, [fb]);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        const feedbackBlock = component.textResults.find((b) => b.startIndex === 6 && b.endIndex === 11)!;
        expect(feedbackBlock.feedback).toBeDefined();
        expect(feedbackBlock.cssClass).toContain('negative-feedback');
    });

    it('should map SHA1 reference to existing submission block', () => {
        const block: any = { startIndex: 0, endIndex: 5, text: 'hello', id: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' };
        const submission: any = { text: 'hello world', blocks: [block] };
        const fb: any = { reference: block.id, credits: 1, text: 'good' };
        const result = createResult(submission, [fb]);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        const feedbackBlock = component.textResults.find((b) => b.startIndex === 0 && b.endIndex === 5)!;
        expect(feedbackBlock.feedback).toBeDefined();
        expect(feedbackBlock.cssClass).toContain('positive-feedback');
    });

    it('should ignore feedback with non-matching numeric or string references', () => {
        const submission: any = { text: 'xyz' };
        const fbs: any[] = [
            { reference: '99', credits: 1 }, // out of range
            { reference: 'not-there', credits: -1 }, // substring not found
            { credits: 0 }, // no reference
        ];
        const result = createResult(submission, fbs);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        // Should be a single plain block as all feedbacks are ignored
        expect(component.textResults).toHaveLength(1);
        expect(component.textResults[0].feedback).toBeUndefined();
    });

    it('should pass built feedback text to unified feedback', () => {
        jest.spyOn(component, 'buildFeedbackTextForReview').mockReturnValue('RENDERED');
        const submission: any = { text: 'abc' };
        const fb: any = { reference: '1', credits: 2, text: 'x' };
        const result = createResult(submission, [fb]);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        const feedbackDebug = fixture.debugElement.query(By.directive(UnifiedFeedbackComponent));
        const feedbackCmp = feedbackDebug.componentInstance as UnifiedFeedbackComponent;
        expect(feedbackCmp.feedbackContent()).toBe('RENDERED');
    });

    it('should handle multiple feedback blocks and plain slices in order', () => {
        const submission: any = { text: 'hello world' };
        const fbs: any[] = [
            { reference: '0', credits: 1, text: 'start' }, // numeric at index 0
            { reference: 'world', credits: -1, text: 'end' }, // substring at 6-11
        ];
        const result = createResult(submission, fbs);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        // Expect blocks covering: [h with fb], [ello ], [world with fb]
        expect(component.textResults).toHaveLength(3);
        const first = component.textResults[0];
        const middle = component.textResults[1];
        const last = component.textResults[2];
        expect(first.startIndex).toBe(0);
        expect(first.endIndex).toBe(1);
        expect(first.feedback).toBeDefined();
        expect(middle.feedback).toBeUndefined();
        expect(last.startIndex).toBe(6);
        expect(last.endIndex).toBe(11);
        expect(last.feedback).toBeDefined();
    });

    it('should ignore SHA1 reference when block not found', () => {
        const submission: any = { text: 'abcdef', blocks: [] };
        const sha1Ref = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
        const fb: any = { reference: sha1Ref, credits: 1 };
        const result = createResult(submission, [fb]);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        // only one plain block should exist
        expect(component.textResults).toHaveLength(1);
        expect(component.textResults[0].feedback).toBeUndefined();
    });

    it('should choose first occurrence for substring references', () => {
        const submission: any = { text: 'aba aba' };
        const fb: any = { reference: 'aba', credits: 1 };
        const result = createResult(submission, [fb]);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        const feedbackBlock = component.textResults.find((b) => b.feedback)!;
        expect(feedbackBlock.startIndex).toBe(0); // first occurrence at index 0
        expect(feedbackBlock.endIndex).toBe(3);
    });

    it('should bind points and showReference inputs to unified feedback', () => {
        const submission: any = { text: 'abc' };
        const fb: any = { reference: '2', credits: 3, text: 'ok' };
        const result = createResult(submission, [fb]);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        const feedbackDebug = fixture.debugElement.query(By.directive(UnifiedFeedbackComponent));
        const feedbackCmp = feedbackDebug.componentInstance as UnifiedFeedbackComponent;
        expect(feedbackCmp.points()).toBe(3);
        expect(feedbackCmp.showReference()).toBeFalse();
    });

    it('should ignore negative numeric reference', () => {
        const submission: any = { text: 'abcd' };
        const fb: any = { reference: '-1', credits: 1 };
        const result = createResult(submission, [fb]);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        expect(component.textResults).toHaveLength(1);
        expect(component.textResults[0].feedback).toBeUndefined();
    });

    it('should ignore numeric reference equal to length', () => {
        const submission: any = { text: 'abcd' };
        const fb: any = { reference: '4', credits: 1 };
        const result = createResult(submission, [fb]);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        expect(component.textResults).toHaveLength(1);
        expect(component.textResults[0].feedback).toBeUndefined();
    });

    it('should render span with feedback css class for feedback blocks', () => {
        const submission: any = { text: 'abc' };
        const fb: any = { reference: '1', credits: 1, text: 'x' };
        const result = createResult(submission, [fb]);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        const spans = fixture.nativeElement.querySelectorAll('.white-space');
        // there are three spans, the middle one should have positive-feedback class
        expect(spans).toHaveLength(3);
        expect(spans[1].className).toContain('text-with-feedback');
        expect(spans[1].className).toContain('positive-feedback');
    });

    it('should order mixed SHA1 and substring feedbacks with gaps as plain text', () => {
        const sha1Block: any = { startIndex: 0, endIndex: 4, text: 'abcd', id: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' };
        const submission: any = { text: 'abcdXYworld', blocks: [sha1Block] };
        const fbs: any[] = [
            { reference: sha1Block.id, credits: 1 }, // 0-4
            { reference: 'world', credits: -1 }, // 6-11
        ];
        const result = createResult(submission, fbs);
        fixture.componentRef.setInput('result', result);
        fixture.detectChanges();
        // Expect blocks: [abcd fb], [XY plain], [world fb]
        expect(component.textResults).toHaveLength(3);
        expect(component.textResults[0].startIndex).toBe(0);
        expect(component.textResults[0].endIndex).toBe(4);
        expect(!!component.textResults[0].feedback).toBeTrue();
        expect(component.textResults[1].startIndex).toBe(4);
        expect(component.textResults[1].endIndex).toBe(6);
        expect(component.textResults[1].feedback).toBeUndefined();
        expect(component.textResults[2].startIndex).toBe(6);
        expect(component.textResults[2].endIndex).toBe(11);
        expect(!!component.textResults[2].feedback).toBeTrue();
    });

    it('TextResultBlock getters for positive/negative/neutral/blank', () => {
        const base = new TextBlock();
        base.startIndex = 0;
        base.endIndex = 2;
        base.text = 'ab';

        // positive
        let block = new TextResultBlock(base, { credits: 1 } as any);
        expect(block).toHaveLength(2);
        expect(block.cssClass).toContain('positive-feedback');
        expect(block.iconCssClass).toContain('positive-feedback');
        expect(block.feedbackCssClass).toBe('alert alert-success');
        expect(block.icon).toBeDefined();
        expect(block.circleIcon).toBeDefined();

        // negative
        block = new TextResultBlock(base, { credits: -1 } as any);
        expect(block.cssClass).toContain('negative-feedback');
        expect(block.feedbackCssClass).toBe('alert alert-danger');

        // neutral/blank (0 credits is considered empty in current Feedback.isEmpty)
        block = new TextResultBlock(base, { credits: 0 } as any);
        expect(block.cssClass).toBe('');
        expect(block.feedbackCssClass).toBe('');
        expect(block.icon).toBeUndefined();
        expect(block.circleIcon).toBeUndefined();

        // blank (no feedback)
        block = new TextResultBlock(base);
        expect(block.cssClass).toBe('');
        expect(block.icon).toBeUndefined();
        expect(block.circleIcon).toBeUndefined();
        expect(block.iconCssClass).toBe('feedback-icon blank-feedback');
        expect(block.feedbackCssClass).toBe('');
    });
});
