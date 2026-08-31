import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExerciseInstructionSsrContentComponent } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr-content.component';
import { SsrTask } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';

const INTERACTIVE_TASK_CLASS = 'artemis-task--interactive';

const task = (index: number, testIds: number[] = [1]): SsrTask => ({
    index,
    taskName: `Task ${index}`,
    testIds,
    status: 'success',
    authoredCount: 1,
    notExecutedCount: 0,
});

/** Wraps statement markup the way `assembleShadowContent` would, minus the stylesheets a test does not need. */
const content = (body: string): string => `<div class="artemis-ssr-body"><div class="artemis-problem-statement">${body}</div></div>`;
const taskSpan = (name: string): string => `<span class="artemis-task" data-task-name="${name}">${name}</span>`;

/**
 * jsdom performs no layout and its focus handling inside a shadow root is incomplete, so scroll and focus retention
 * across a re-render are asserted in the Playwright specs instead. What can be asserted here is everything the
 * component decides: what it injects, which task it marks interactive, and what a click or key resolves to.
 */
describe('ProgrammingExerciseInstructionSsrContentComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInstructionSsrContentComponent>;
    let comp: ProgrammingExerciseInstructionSsrContentComponent;

    const shadow = (): ShadowRoot => fixture.nativeElement.shadowRoot as ShadowRoot;
    const taskElements = (): HTMLElement[] => [...shadow().querySelectorAll<HTMLElement>('.artemis-task')];

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(ProgrammingExerciseInstructionSsrContentComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('html', content(taskSpan('A')));
        fixture.detectChanges();
    });

    afterEach(() => vi.restoreAllMocks());

    describe('injection', () => {
        it('renders the statement markup into its shadow root', () => {
            expect(shadow().querySelector('.artemis-problem-statement')).not.toBeNull();
            expect(taskElements()).toHaveLength(1);
        });

        it('replaces the markup when a new render arrives', () => {
            fixture.componentRef.setInput('html', content(taskSpan('X') + taskSpan('Y')));
            fixture.detectChanges();

            expect(taskElements()).toHaveLength(2);
        });
    });

    describe('task accessibility', () => {
        it('marks an interactive task as a button with a translated label and the interactive class', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();

            const element = taskElements()[0];
            expect(element.getAttribute('role')).toBe('button');
            expect(element.getAttribute('tabindex')).toBe('0');
            expect(element.getAttribute('aria-label')).toContain('Task 0');
            expect(element.classList.contains(INTERACTIVE_TASK_CLASS)).toBe(true);
        });

        it('leaves a task plain while no feedback dialog can be opened', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', false);
            fixture.detectChanges();

            const element = taskElements()[0];
            expect(element.getAttribute('role')).toBeNull();
            expect(element.hasAttribute('aria-label')).toBe(false);
            expect(element.classList.contains(INTERACTIVE_TASK_CLASS)).toBe(false);
        });

        it('does not mark a task that has no test ids, because there is no feedback to show', () => {
            fixture.componentRef.setInput('html', content(taskSpan('A') + taskSpan('B')));
            fixture.componentRef.setInput('tasks', [task(0, []), task(1, [2])]);
            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();

            expect(taskElements()[0].getAttribute('role')).toBeNull();
            expect(taskElements()[1].getAttribute('role')).toBe('button');
        });

        it('refreshes the gating without re-injecting when only the interactivity changes', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', false);
            fixture.detectChanges();
            const elementBefore = taskElements()[0];

            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();

            // The same element node, so the markup was not replaced, only its attributes updated.
            expect(taskElements()[0]).toBe(elementBefore);
            expect(elementBefore.getAttribute('role')).toBe('button');
        });
    });

    describe('task activation', () => {
        const clickTask = (element: Element): void => {
            element.dispatchEvent(new MouseEvent('click', { bubbles: true, composed: true, cancelable: true }));
        };

        it('emits the position of a clicked interactive task', () => {
            fixture.componentRef.setInput('html', content(taskSpan('A') + taskSpan('B')));
            fixture.componentRef.setInput('tasks', [task(0), task(1)]);
            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            clickTask(taskElements()[1]);

            expect(activated).toHaveBeenCalledWith(1);
        });

        it('activates on a click inside a task, not only on the task element itself', () => {
            fixture.componentRef.setInput('html', content('<span class="artemis-task" data-task-name="A"><em>nested</em></span>'));
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            clickTask(shadow().querySelector('em')!);

            expect(activated).toHaveBeenCalledWith(0);
        });

        it('emits nothing while no feedback dialog can be opened', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', false);
            fixture.detectChanges();
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            clickTask(taskElements()[0]);

            expect(activated).not.toHaveBeenCalled();
        });

        it('emits nothing for a task that carries no test ids', () => {
            fixture.componentRef.setInput('tasks', [task(0, [])]);
            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            clickTask(taskElements()[0]);

            expect(activated).not.toHaveBeenCalled();
        });

        it('activates on an Enter key on a focused task', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            taskElements()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, composed: true, cancelable: true }));

            expect(activated).toHaveBeenCalledWith(0);
        });
    });

    describe('links', () => {
        const clickAnchor = (): Event => {
            const event = new MouseEvent('click', { bubbles: true, composed: true, cancelable: true });
            shadow().querySelector('a')!.dispatchEvent(event);
            return event;
        };

        it('opens a link that is in the statement, without handing over the opener', () => {
            const open = vi.spyOn(window, 'open').mockReturnValue(null);
            fixture.componentRef.setInput('html', content('<a href="https://example.org/docs">docs</a>'));
            fixture.componentRef.setInput('linkTargets', ['https://example.org/docs']);
            fixture.detectChanges();

            const event = clickAnchor();

            expect(event.defaultPrevented).toBe(true);
            expect(open).toHaveBeenCalledWith('https://example.org/docs', '_blank', 'noopener,noreferrer');
        });

        it('cancels navigation but opens nothing for a link the statement does not list', () => {
            const open = vi.spyOn(window, 'open').mockReturnValue(null);
            fixture.componentRef.setInput('html', content('<a href="https://evil.example/steal">x</a>'));
            fixture.componentRef.setInput('linkTargets', ['https://example.org/docs']);
            fixture.detectChanges();

            const event = clickAnchor();

            expect(event.defaultPrevented).toBe(true);
            expect(open).not.toHaveBeenCalled();
        });

        it('opens a link even while the statement is not interactive', () => {
            const open = vi.spyOn(window, 'open').mockReturnValue(null);
            fixture.componentRef.setInput('html', content('<a href="https://example.org/docs">docs</a>'));
            fixture.componentRef.setInput('linkTargets', ['https://example.org/docs']);
            fixture.componentRef.setInput('interactive', false);
            fixture.detectChanges();

            clickAnchor();

            expect(open).toHaveBeenCalled();
        });

        it('refuses a javascript url even when the statement contains it', () => {
            const open = vi.spyOn(window, 'open').mockReturnValue(null);
            fixture.componentRef.setInput('html', content('<a href="javascript:alert(1)">x</a>'));
            fixture.componentRef.setInput('linkTargets', ['javascript:alert(1)']);
            fixture.detectChanges();

            clickAnchor();

            expect(open).not.toHaveBeenCalled();
        });

        it('intercepts an SVG anchor, which is an SVGElement rather than an HTMLAnchorElement', () => {
            // PlantUML emits inline SVG, and an SVG `<a>` would otherwise keep its default and navigate the whole app.
            const open = vi.spyOn(window, 'open').mockReturnValue(null);
            fixture.componentRef.setInput('html', content('<svg><a href="https://example.org/docs"><text>x</text></a></svg>'));
            fixture.componentRef.setInput('linkTargets', ['https://example.org/docs']);
            fixture.detectChanges();

            const event = new MouseEvent('click', { bubbles: true, composed: true, cancelable: true });
            shadow().querySelector('text')!.dispatchEvent(event);

            expect(event.defaultPrevented).toBe(true);
            expect(open).toHaveBeenCalledWith('https://example.org/docs', '_blank', 'noopener,noreferrer');
        });
    });

    describe('task precedence over a nested link', () => {
        it('activates the task, not the link, when the reader clicks a link inside an interactive task', () => {
            fixture.componentRef.setInput('html', content('<span class="artemis-task" data-task-name="A"><a href="https://example.org">A</a></span>'));
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', true);
            fixture.componentRef.setInput('linkTargets', ['https://example.org']);
            fixture.detectChanges();
            const open = vi.spyOn(window, 'open').mockReturnValue(null);
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            shadow()
                .querySelector('a')!
                .dispatchEvent(new MouseEvent('click', { bubbles: true, composed: true, cancelable: true }));

            expect(activated).toHaveBeenCalledWith(0);
            expect(open).not.toHaveBeenCalled();
        });
    });

    describe('focus retention across a re-render', () => {
        it('restores focus to the task when focus sat on a link inside it', () => {
            const withLink = '<span class="artemis-task" data-task-name="A"><a href="https://example.org">A</a></span>';
            fixture.componentRef.setInput('html', content(withLink));
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();

            // Focus the link inside the task; the reader is "on" the task even though the anchor holds focus.
            const anchor = shadow().querySelector('a') as HTMLElement;
            anchor.focus();
            // Guard: if jsdom did not move focus, the assertion below would be vacuous.
            expect(shadow().activeElement).toBe(anchor);

            // A result arrives and the statement is re-rendered with byte-different markup.
            fixture.componentRef.setInput('html', content(withLink + '<p>updated</p>'));
            fixture.detectChanges();

            // Focus lands back on the task, not at the top of the statement.
            expect(shadow().activeElement).toBe(taskElements()[0]);
        });
    });
});
