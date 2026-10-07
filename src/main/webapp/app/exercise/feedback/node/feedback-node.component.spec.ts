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

    describe('compact layout', () => {
        const createItem = (overrides: Partial<FeedbackItem> = {}): FeedbackItem =>
            ({ name: 'Test Case', type: 'Test', credits: 1, feedbackReference: {}, ...overrides }) as FeedbackItem;

        const createGroup = (members: FeedbackItem[], overrides: Partial<FeedbackGroup> = {}): FeedbackGroup =>
            ({ name: 'wrong', color: 'danger', members, credits: 4, open: true, ...overrides }) as unknown as FeedbackGroup;

        it('should render the group header as a compact bar with a 16px semibold title', () => {
            fixture.componentRef.setInput('feedbackItemNode', createGroup([createItem()]));
            fixture.detectChanges();

            const bar: HTMLElement = fixture.nativeElement.querySelector('.feedback-group');
            expect(bar.classList).toContain('feedback-group--danger');
            expect(bar.classList).toContain('min-h-10');
            expect(bar.classList).toContain('px-3');
            expect(bar.classList).toContain('py-2');
            expect(bar.classList).toContain('mb-2');
            expect(bar.classList).not.toContain('p-4');
            expect(bar.classList).not.toContain('mb-4');

            const title: HTMLElement = bar.querySelector('h4')!;
            expect(title.classList).toContain('m-0!');
            expect(title.classList).toContain('text-base!');
            expect(title.classList).toContain('font-semibold!');
        });

        it('should render the credits of the group header as semibold small text', () => {
            fixture.componentRef.setInput('feedbackItemNode', createGroup([createItem()]));
            fixture.detectChanges();

            const credits: HTMLElement = fixture.nativeElement.querySelector('.feedback-group > span');
            expect(credits.textContent).toContain('4P');
            expect(credits.classList).toContain('text-sm');
            expect(credits.classList).toContain('font-semibold');
        });

        it('should not inset the members of an expanded group against the group header', () => {
            fixture.componentRef.setInput('feedbackItemNode', createGroup([createItem({ title: 'first' }), createItem({ title: 'second' })]));
            fixture.detectChanges();

            const group: HTMLElement = fixture.nativeElement.querySelector('.feedback-item-group');
            expect(group.querySelector('.mx-3')).toBeNull();
            // the members are rendered directly below the header, so they share its left edge
            const members = Array.from(group.children).filter((child) => child.tagName.toLowerCase() === 'jhi-feedback-node');
            expect(members).toHaveLength(2);
        });

        it('should render the title of a feedback item with the 16px semibold heading style and keep the credits hook', () => {
            fixture.componentRef.setInput('feedbackItemNode', createItem({ title: 'passed' }));
            fixture.detectChanges();

            const title: HTMLElement = fixture.nativeElement.querySelector('.feedback-item h4.feedback-item__category');
            expect(title.classList).toContain('m-0!');
            expect(title.classList).toContain('text-base!');
            expect(title.classList).toContain('font-semibold!');
            // used by the Playwright page object ScaFeedbackModal
            expect(fixture.nativeElement.querySelector('.feedback-item__credits')?.textContent).toContain('1P');
        });

        it('should not add a bottom margin to the no feedback message', () => {
            fixture.componentRef.setInput('feedbackItemNode', createItem());
            fixture.detectChanges();

            const message: HTMLElement = fixture.nativeElement.querySelector('.feedback-item p');
            expect(message.classList).toContain('mb-0!');
        });
    });
});
