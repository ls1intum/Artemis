import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { FeedbackNodeComponent } from 'app/exercise/feedback/node/feedback-node.component';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { FeedbackGroup } from 'app/exercise/feedback/group/feedback-group';

describe('FeedbackNodeComponent', () => {
    let fixture: ComponentFixture<FeedbackNodeComponent>;
    let component: FeedbackNodeComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeedbackNodeComponent],
            providers: [provideTranslateService()],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackNodeComponent);
        component = fixture.componentInstance;
    });

    it('should set specific node type correctly for feedback item', () => {
        fixture.componentRef.setInput('feedbackItemNode', new FeedbackItem());
        fixture.detectChanges();

        expect(component.feedbackItem()).toBeDefined();
    });

    it('should set specific node type correctly for feedback group', () => {
        fixture.componentRef.setInput('feedbackItemNode', { members: [], credits: 0 } as unknown as FeedbackGroup);
        fixture.detectChanges();

        expect(component.feedbackItemGroup()).toBeDefined();
    });

    it('should expand a group only when it is open', () => {
        fixture.componentRef.setInput('feedbackItemNode', { members: [], credits: 0, open: false } as unknown as FeedbackGroup);
        fixture.detectChanges();

        expect(component.isGroupExpanded()).toBe(false);

        component.toggleFeedbackItemGroupOpen();
        expect(component.isGroupExpanded()).toBe(true);
    });

    it('should render every group expanded while printing, regardless of its open flag', () => {
        // Replaces the former parent-side mutate-then-restore of group.open: printing is now a derived display state.
        fixture.componentRef.setInput('feedbackItemNode', { members: [], credits: 0, open: false } as unknown as FeedbackGroup);
        fixture.componentRef.setInput('isPrinting', true);
        fixture.detectChanges();

        expect(component.isGroupExpanded()).toBe(true);
        // The underlying open flag is NOT mutated, so it collapses back to its previous state once printing ends.
        expect(component.feedbackItemGroup().open).toBe(false);

        fixture.componentRef.setInput('isPrinting', false);
        fixture.detectChanges();
        expect(component.isGroupExpanded()).toBe(false);
    });

    it('should render the location without displaying the file path as source code', () => {
        fixture.componentRef.setInput('feedbackItemNode', {
            name: 'Feedback',
            type: 'Reviewer',
            feedbackReference: {},
            codeReference: {
                filePath: 'src/main/java/Example.java',
                line: 7,
            },
        } as FeedbackItem);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('src/main/java/Example.java');
        expect(fixture.nativeElement.textContent).toContain('7');
        expect(fixture.nativeElement.querySelector('.feedback-item__code-reference-line code')).toBeNull();
    });

    it('should highlight code references with highlight.js', () => {
        fixture.componentRef.setInput('feedbackItemNode', {
            name: 'Feedback',
            type: 'Reviewer',
            feedbackReference: {},
            codeReference: {
                filePath: 'src/main/java/Example.java',
                line: 7,
                lines: [{ line: 7, code: 'public class Example {}', referenced: true }],
            },
        } as FeedbackItem);
        fixture.detectChanges();

        const codeElement = fixture.nativeElement.querySelector('.feedback-item__code-reference-line code');
        expect(codeElement.classList).toContain('hljs');
        expect(codeElement.innerHTML).toContain('hljs-keyword');
    });

    it('should mark every referenced code line', () => {
        fixture.componentRef.setInput('feedbackItemNode', {
            name: 'Feedback',
            type: 'Reviewer',
            feedbackReference: {},
            codeReference: {
                filePath: 'src/main/java/Example.java',
                line: 11,
                lineEnd: 13,
                lines: [
                    { line: 10, code: 'before();', referenced: false },
                    { line: 11, code: 'first();', referenced: true },
                    { line: 12, code: 'second();', referenced: true },
                    { line: 13, code: 'third();', referenced: true },
                    { line: 14, code: 'after();', referenced: false },
                ],
            },
        } as FeedbackItem);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelectorAll('.feedback-item__code-reference-line--referenced')).toHaveLength(3);
    });
});
