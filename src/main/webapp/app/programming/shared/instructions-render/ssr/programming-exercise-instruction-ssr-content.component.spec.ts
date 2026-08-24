import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FRAME_PROTOCOL_VERSION, INTERACTIVE_TASK_CLASS } from 'app/programming/shared/instructions-render/ssr/problem-statement-frame-script';
import { ProgrammingExerciseInstructionSsrContentComponent } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr-content.component';
import { SsrTask } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';
import { runFrameScript } from 'test/helpers/problem-statement-frame.helper';

const GENERATION = 'generation-1';

const task = (index: number, testIds: number[] = [1]): SsrTask => ({
    index,
    taskName: `Task ${index}`,
    testIds,
    status: 'success',
    authoredCount: 1,
    notExecutedCount: 0,
});

/**
 * jsdom implements neither iframe sandboxing nor layout, so what the frame *is* cannot be asserted here; that is
 * what the Playwright specs are for, and they run in three engines because the guarantees differ per engine.
 * What can be asserted here is every decision on either side of the boundary: what the component sends, what it
 * accepts back, and what the real frame script does with it.
 */
describe('ProgrammingExerciseInstructionSsrContentComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInstructionSsrContentComponent>;
    let comp: ProgrammingExerciseInstructionSsrContentComponent;

    const frame = (): HTMLIFrameElement => fixture.nativeElement.querySelector('iframe');

    /** Everything the component has told its frame. */
    let posted: Record<string, unknown>[];

    /** Delivers a message as if the component's own frame had sent it. */
    const sendFromFrame = (data: Record<string, unknown>, overrides: { source?: unknown } = {}): void => {
        window.dispatchEvent(
            new MessageEvent('message', {
                data: { v: FRAME_PROTOCOL_VERSION, gen: GENERATION, ...data },
                source: (overrides.source ?? frame().contentWindow) as MessageEventSource,
            }),
        );
    };

    /** Announces the frame as loaded, which is what makes the component start talking to it. */
    const announceReady = (): void => sendFromFrame({ type: 'ready' });

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(ProgrammingExerciseInstructionSsrContentComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('srcdoc', '<!DOCTYPE html><html><body><span class="artemis-task">A</span></body></html>');
        fixture.componentRef.setInput('generation', GENERATION);
        fixture.detectChanges();
        posted = [];
        vi.spyOn(frame().contentWindow!, 'postMessage').mockImplementation((message) => {
            posted.push(message as Record<string, unknown>);
        });
    });

    afterEach(() => vi.restoreAllMocks());

    describe('the sandbox, which is the whole point of the frame', () => {
        it('permits scripts and nothing else', () => {
            // Any addition here is a security decision. `allow-same-origin` in particular would hand the statement
            // back the cookies, storage and parent DOM this component exists to keep it away from.
            expect(frame().getAttribute('sandbox')).toBe('allow-scripts');
        });

        it('carries the same sandbox the browser tests assert, which they cannot read out of the template', () => {
            // Angular forbids binding `sandbox`, so the template holds a literal and the browser tests import the
            // constant. This is what keeps the two from drifting apart unnoticed.
            expect(frame().getAttribute('sandbox')).toBe(comp.expectedSandbox);
        });

        it('grants no browser features and sends no referrer', () => {
            expect(frame().getAttribute('allow')).toBe('');
            expect(frame().getAttribute('referrerpolicy')).toBe('no-referrer');
        });

        it('hands the document to the element byte for byte', () => {
            // Bound as `[attr.srcdoc]`, Angular's sanitizer treats the value as an HTML security context and reduces
            // a full document to a few characters, dropping the `<meta>` policy and the nonced script. Every
            // string-level assertion still passes while the isolation is gone, so only an assertion against the
            // element itself catches it.
            const document_ =
                '<!DOCTYPE html><html><head><meta http-equiv="Content-Security-Policy" content="default-src \'none\'"></head><body><p>x</p><script nonce="abc">window.x=1;</script></body></html>';
            fixture.componentRef.setInput('srcdoc', document_);
            fixture.detectChanges();

            expect(frame().srcdoc).toBe(document_);
        });

        it('is named, so it is reachable for a screen reader', () => {
            expect(frame().getAttribute('title')).toBeTruthy();
        });
    });

    describe('what it accepts from the frame', () => {
        it('ignores a message from a window that is not its frame', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', true);
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            sendFromFrame({ type: 'task', index: 0 }, { source: window });

            expect(activated).not.toHaveBeenCalled();
        });

        it('ignores a message carrying a superseded generation', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            window.dispatchEvent(
                new MessageEvent('message', { data: { v: FRAME_PROTOCOL_VERSION, gen: 'an-older-frame', type: 'task', index: 0 }, source: frame().contentWindow }),
            );

            expect(activated).not.toHaveBeenCalled();
        });

        it('ignores a message from a protocol it does not speak', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            window.dispatchEvent(new MessageEvent('message', { data: { v: 99, gen: GENERATION, type: 'task', index: 0 }, source: frame().contentWindow }));

            expect(activated).not.toHaveBeenCalled();
        });

        it('refuses an activation for a task it does not currently offer', () => {
            // A bounds check alone would let a hostile frame ask for a task the reader is not being offered.
            fixture.componentRef.setInput('tasks', [task(0), task(1, [])]);
            fixture.componentRef.setInput('interactive', true);
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            sendFromFrame({ type: 'task', index: 1 });

            expect(activated).not.toHaveBeenCalled();
        });

        it('refuses an activation while no feedback dialog can be opened at all', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', false);
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            sendFromFrame({ type: 'task', index: 0 });

            expect(activated).not.toHaveBeenCalled();
        });

        it('emits the position of an activated task', () => {
            fixture.componentRef.setInput('interactive', true);
            fixture.componentRef.setInput('tasks', [task(0), task(1)]);
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            sendFromFrame({ type: 'task', index: 1 });

            expect(activated).toHaveBeenCalledWith(1);
        });

        it.each([
            { case: 'an index past the end', index: 5 },
            { case: 'a negative index', index: -1 },
            { case: 'a fractional index', index: 0.5 },
            { case: 'something that is not a number', index: '0' },
        ])('drops a task activation with $case', ({ index }) => {
            fixture.componentRef.setInput('interactive', true);
            fixture.componentRef.setInput('tasks', [task(0)]);
            const activated = vi.fn();
            comp.taskActivated.subscribe(activated);

            sendFromFrame({ type: 'task', index });

            expect(activated).not.toHaveBeenCalled();
        });
    });

    describe('height', () => {
        it('sizes the frame to the height it reports', () => {
            sendFromFrame({ type: 'height', px: 842 });

            expect(frame().style.height).toBe('842px');
        });

        it('clamps a height that is hostile rather than long', () => {
            sendFromFrame({ type: 'height', px: 10_000_000 });

            expect(frame().style.height).toBe('50000px');
        });

        it.each([
            { case: 'not a number', px: 'tall' },
            { case: 'infinite', px: Number.POSITIVE_INFINITY },
            { case: 'NaN', px: Number.NaN },
            { case: 'negative', px: -100 },
            { case: 'zero', px: 0 },
        ])('ignores a height that is $case', ({ px }) => {
            sendFromFrame({ type: 'height', px });

            expect(frame().style.height).toBe('');
        });

        it('starts collapsed rather than at the browser default, so the pane does not jump', () => {
            expect(frame().style.height).toBe('');
        });
    });

    describe('links', () => {
        it('opens a link that is in the statement, without handing over the opener', () => {
            const open = vi.spyOn(window, 'open').mockReturnValue(null);
            fixture.componentRef.setInput('linkTargets', ['https://example.org/docs']);

            sendFromFrame({ type: 'link', href: 'https://example.org/docs' });

            expect(open).toHaveBeenCalledWith('https://example.org/docs', '_blank', 'noopener,noreferrer');
        });

        it('refuses a link the statement does not contain', () => {
            const open = vi.spyOn(window, 'open').mockReturnValue(null);
            fixture.componentRef.setInput('linkTargets', ['https://example.org/docs']);

            sendFromFrame({ type: 'link', href: 'https://evil.example/steal' });

            expect(open).not.toHaveBeenCalled();
        });

        it('refuses a javascript url even when the statement contains it', () => {
            const open = vi.spyOn(window, 'open').mockReturnValue(null);
            fixture.componentRef.setInput('linkTargets', ['javascript:alert(1)']);

            sendFromFrame({ type: 'link', href: 'javascript:alert(1)' });

            expect(open).not.toHaveBeenCalled();
        });
    });

    describe('what it tells the frame', () => {
        it('says nothing until the frame reports that it is loaded', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();

            expect(posted).toHaveLength(0);
        });

        it('names the interactive tasks with their labels already translated', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();

            announceReady();

            expect(posted.at(-1)).toMatchObject({ type: 'interactive', tasks: [{ index: 0, label: expect.stringContaining('Task 0') }] });
        });

        it('names no task while a feedback dialog cannot be opened', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', false);
            fixture.detectChanges();

            announceReady();

            expect(posted.at(-1)).toMatchObject({ tasks: [] });
        });

        it('leaves out a task that has no test ids, because there is no feedback to show', () => {
            fixture.componentRef.setInput('tasks', [task(0, []), task(1, [2])]);
            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();

            announceReady();

            expect((posted.at(-1)?.tasks as { index: number }[]).map((entry) => entry.index)).toEqual([1]);
        });

        it('updates the gating without reloading the frame when only the interactivity changes', () => {
            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.componentRef.setInput('interactive', false);
            fixture.detectChanges();
            announceReady();
            const srcdocBefore = frame().srcdoc;

            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();

            expect(frame().srcdoc).toBe(srcdocBefore);
            expect((posted.at(-1)?.tasks as unknown[]).length).toBe(1);
        });

        it('hands the focused task back after the document is replaced', () => {
            fixture.componentRef.setInput('tasks', [task(0), task(1)]);
            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();
            announceReady();
            sendFromFrame({ type: 'focus', index: 1 });

            // A result arrives, the statement is re-rendered, and the new document announces itself.
            announceReady();

            expect(posted.at(-1)).toMatchObject({ focusIndex: 1 });
        });

        it('does not offer a focus index the new statement no longer has', () => {
            fixture.componentRef.setInput('tasks', [task(0), task(1)]);
            fixture.componentRef.setInput('interactive', true);
            fixture.detectChanges();
            announceReady();
            sendFromFrame({ type: 'focus', index: 1 });

            fixture.componentRef.setInput('tasks', [task(0)]);
            fixture.detectChanges();
            announceReady();

            expect(posted.at(-1)?.focusIndex).toBeUndefined();
        });
    });
});

/**
 * The other half of the contract, executed as the string that actually ships rather than a stand-in for it.
 */
describe('the sandboxed frame script', () => {
    const statement = (body: string) => `<div class="artemis-problem-statement">${body}</div>`;
    const taskSpan = (name: string) => `<span class="artemis-task" data-task-name="${name}">${name}</span>`;

    it('announces itself so the parent knows when it may start talking', () => {
        const harness = runFrameScript(statement('<p>x</p>'));

        expect(harness.posted.map((message) => message.type)).toContain('ready');
    });

    it('stamps every message with the generation it was built for', () => {
        const harness = runFrameScript(statement('<p>x</p>'));

        expect(harness.posted.every((message) => message.gen === 'test-generation')).toBe(true);
    });

    it('marks a named task as a button and leaves the others alone', () => {
        const harness = runFrameScript(statement(taskSpan('A') + taskSpan('B')));

        harness.sendFromParent({ type: 'interactive', tasks: [{ index: 0, label: 'A: all tests passing' }] });

        const [first, second] = [...harness.document.querySelectorAll('.artemis-task')];
        expect(first.getAttribute('role')).toBe('button');
        expect(first.getAttribute('tabindex')).toBe('0');
        expect(first.getAttribute('aria-label')).toBe('A: all tests passing');
        expect(first.classList.contains(INTERACTIVE_TASK_CLASS)).toBe(true);
        expect(second.getAttribute('role')).toBeNull();
        expect(second.classList.contains(INTERACTIVE_TASK_CLASS)).toBe(false);
    });

    it('takes the button role away again when the parent stops naming the task', () => {
        const harness = runFrameScript(statement(taskSpan('A')));
        harness.sendFromParent({ type: 'interactive', tasks: [{ index: 0, label: 'A' }] });

        harness.sendFromParent({ type: 'interactive', tasks: [] });

        const element = harness.document.querySelector('.artemis-task')!;
        expect(element.getAttribute('role')).toBeNull();
        expect(element.getAttribute('aria-label')).toBeNull();
        expect(element.classList.contains(INTERACTIVE_TASK_CLASS)).toBe(false);
    });

    it('ignores a message from anyone other than its parent', () => {
        const harness = runFrameScript(statement(taskSpan('A')));

        harness.sendFromStranger({ type: 'interactive', tasks: [{ index: 0, label: 'A' }] });

        expect(harness.document.querySelector('.artemis-task')?.getAttribute('role')).toBeNull();
    });

    it('reports the position of a clicked task, not its name, because names are not unique', () => {
        const harness = runFrameScript(statement(taskSpan('Same') + taskSpan('Same')));

        harness.document.querySelectorAll('.artemis-task')[1].dispatchEvent(new harness.view.MouseEvent('click', { bubbles: true }));

        expect(harness.posted.at(-1)).toMatchObject({ type: 'task', index: 1 });
    });

    it('reports a click on something inside a task, not only on the task itself', () => {
        const harness = runFrameScript(statement('<span class="artemis-task"><em>nested</em></span>'));

        harness.document.querySelector('em')!.dispatchEvent(new harness.view.MouseEvent('click', { bubbles: true }));

        expect(harness.posted.at(-1)).toMatchObject({ type: 'task', index: 0 });
    });

    it('reports nothing for a click outside any task', () => {
        const harness = runFrameScript(statement('<p id="plain">text</p>'));
        const before = harness.posted.length;

        harness.document.querySelector('#plain')!.dispatchEvent(new harness.view.MouseEvent('click', { bubbles: true }));

        expect(harness.posted).toHaveLength(before);
    });

    it('hands a clicked link to the parent instead of navigating itself out of existence', () => {
        const harness = runFrameScript(statement('<a href="https://example.org/docs">docs</a>'));

        harness.document.querySelector('a')!.dispatchEvent(new harness.view.MouseEvent('click', { bubbles: true }));

        expect(harness.posted.at(-1)).toMatchObject({ type: 'link', href: 'https://example.org/docs' });
    });

    it('treats a link inside a task as the task, since that is what the reader is pointing at', () => {
        const harness = runFrameScript(statement('<span class="artemis-task"><a href="https://example.org">A</a></span>'));

        harness.document.querySelector('a')!.dispatchEvent(new harness.view.MouseEvent('click', { bubbles: true }));

        expect(harness.posted.at(-1)).toMatchObject({ type: 'task', index: 0 });
    });

    it('reports the focused task so the parent can restore it after a re-render', () => {
        const harness = runFrameScript(statement(taskSpan('A') + taskSpan('B')));

        harness.document.querySelectorAll('.artemis-task')[1].dispatchEvent(new harness.view.FocusEvent('focusin', { bubbles: true }));

        expect(harness.posted.at(-1)).toMatchObject({ type: 'focus', index: 1 });
    });

    it('focuses the task the parent asks it to', () => {
        const harness = runFrameScript(statement(taskSpan('A') + taskSpan('B')));

        harness.sendFromParent({ type: 'interactive', tasks: [{ index: 1, label: 'B' }], focusIndex: 1 });

        expect(harness.document.activeElement).toBe(harness.document.querySelectorAll('.artemis-task')[1]);
    });
});
