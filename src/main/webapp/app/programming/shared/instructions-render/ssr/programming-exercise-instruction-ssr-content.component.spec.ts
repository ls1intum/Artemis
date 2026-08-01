import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import katex from 'katex';
import { compile } from 'sass';
import hljs from 'app/foundation/util/highlight-languages.util';
import { ProgrammingExerciseInstructionSsrContentComponent } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr-content.component';
import { SsrTask, SsrTaskStatus } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

// jsdom cannot lay out math, so KaTeX is mocked. The mock still writes into the passed element so the spec can assert
// that the component handed the live placeholder node (and not, say, a detached copy) to the renderer.
vi.mock('katex', () => ({
    default: {
        render: vi.fn((formula: string, element: HTMLElement) => {
            element.innerHTML = `<span class="katex">${formula}</span>`;
        }),
    },
}));

const taskSpan = (name: string, testIds: string) => `<span class="artemis-task" data-task-name="${name}" data-test-ids="${testIds}">${name}</span>`;

const task = (index: number, taskName: string, testIds: number[], status: SsrTaskStatus = 'success'): SsrTask => ({
    index,
    taskName,
    testIds,
    status,
    authoredCount: testIds.length,
    notExecutedCount: 0,
});

const codeBlock = (code: string, language?: string) =>
    `<div class="artemis-problem-statement"><pre><code${language ? ` class="language-${language}"` : ''}>${code}</code></pre></div>`;

/**
 * Spied rather than stubbed: the assertions are about which branch the component takes, while the markup assertions
 * still look at the real highlighting. Created through a factory so the spy types can be inferred: naming
 * `typeof hljs.highlight` in an annotation resolves to that method's deprecated overload.
 */
const spyOnHighlighting = () => ({ highlight: vi.spyOn(hljs, 'highlight'), highlightAuto: vi.spyOn(hljs, 'highlightAuto') });

describe('ProgrammingExerciseInstructionSsrContentComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInstructionSsrContentComponent>;
    let highlight: ReturnType<typeof spyOnHighlighting>['highlight'];
    let highlightAuto: ReturnType<typeof spyOnHighlighting>['highlightAuto'];

    afterEach(() => {
        highlight.mockRestore();
        highlightAuto.mockRestore();
    });

    beforeEach(async () => {
        vi.mocked(katex.render).mockClear();
        ({ highlight, highlightAuto } = spyOnHighlighting());
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseInstructionSsrContentComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingExerciseInstructionSsrContentComponent);
    });

    const shadowRoot = (): ShadowRoot => fixture.nativeElement.shadowRoot;
    const taskElements = () => shadowRoot().querySelectorAll<HTMLElement>('.artemis-task');

    const render = (html: string, tasks: SsrTask[], interactive = false) => {
        fixture.componentRef.setInput('html', html);
        fixture.componentRef.setInput('tasks', tasks);
        fixture.componentRef.setInput('interactive', interactive);
        fixture.detectChanges();
    };

    it('injects the server html into a real shadow root', () => {
        render(`<style>.artemis-task{color:red}</style><div class="artemis-problem-statement">${taskSpan('A', '1')}</div>`, [task(0, 'A', [1])]);

        expect(shadowRoot()).toBeTruthy();
        // The server's own stylesheet must land inside the shadow root, which is the whole point of the encapsulation.
        expect(shadowRoot().querySelector('style')).toBeTruthy();
        expect(shadowRoot().querySelector('.artemis-task')?.textContent).toBe('A');
        // The markup must not leak into the light DOM.
        expect(fixture.nativeElement.querySelector('.artemis-task')).toBeNull();
    });

    it('renders the inert katex placeholders emitted by the server', () => {
        render(`<div class="artemis-problem-statement"><span class="katex-formula" data-formula="a^2" data-display-mode="false"></span></div>`, []);

        const placeholder = shadowRoot().querySelector('.katex-formula')!;
        expect(katex.render).toHaveBeenCalledOnce();
        expect(vi.mocked(katex.render).mock.calls[0][0]).toBe('a^2');
        expect(vi.mocked(katex.render).mock.calls[0][1]).toBe(placeholder);
        expect(vi.mocked(katex.render).mock.calls[0][2]?.displayMode).toBe(false);
        expect(placeholder.innerHTML).toContain('katex');
    });

    it('passes the display mode through for a block formula', () => {
        render(`<div class="artemis-problem-statement"><span class="katex-formula" data-formula="b^2" data-display-mode="true"></span></div>`, []);

        expect(vi.mocked(katex.render).mock.calls[0][2]?.displayMode).toBe(true);
    });

    it('marks tasks as interactive only when a feedback dialog can be opened', () => {
        const tasks = [task(0, 'A', [1])];
        render(`<div class="artemis-problem-statement">${taskSpan('A', '1')}</div>`, tasks);

        const nonInteractive = taskElements()[0];
        expect(nonInteractive.getAttribute('role')).toBeNull();
        expect(nonInteractive.getAttribute('tabindex')).toBeNull();
        // ARIA prohibits aria-label on role=generic, so a non-interactive task must not carry one.
        expect(nonInteractive.getAttribute('aria-label')).toBeNull();

        render(`<div class="artemis-problem-statement">${taskSpan('A', '1')}${taskSpan('B', '2')}</div>`, [task(0, 'A', [1]), task(1, 'B', [2])], true);

        const interactive = taskElements()[0];
        expect(interactive.getAttribute('role')).toBe('button');
        expect(interactive.getAttribute('tabindex')).toBe('0');
        expect(interactive.getAttribute('aria-label')).toBe('A: artemisApp.programmingExercise.problemStatement.taskStatus.success');
    });

    it('leaves a task without test ids non-interactive', () => {
        render(`<div class="artemis-problem-statement">${taskSpan('A', '')}</div>`, [task(0, 'A', [])], true);

        expect(taskElements()[0].getAttribute('role')).toBeNull();
    });

    it('re-applies the accessibility attributes when only the interactivity changes', () => {
        const html = `<div class="artemis-problem-statement">${taskSpan('A', '1')}</div>`;
        const tasks = [task(0, 'A', [1])];
        render(html, tasks);
        const before = taskElements()[0];
        expect(before.getAttribute('role')).toBeNull();

        // Same html: the DOM is deliberately not replaced, but the gating changed and must take effect anyway.
        fixture.componentRef.setInput('interactive', true);
        fixture.detectChanges();

        expect(taskElements()[0]).toBe(before);
        expect(before.getAttribute('role')).toBe('button');
        expect(before.getAttribute('aria-label')).toBe('A: artemisApp.programmingExercise.problemStatement.taskStatus.success');
        // Unchanged html must not re-run the formula pass.
        expect(katex.render).not.toHaveBeenCalled();
    });

    it('restores focus to the task at the same index after a re-render', () => {
        render(`<div class="artemis-problem-statement">${taskSpan('A', '1')}${taskSpan('B', '2')}</div>`, [task(0, 'A', [1]), task(1, 'B', [2])], true);

        const secondTask = taskElements()[1];
        secondTask.focus();
        expect(shadowRoot().activeElement).toBe(secondTask);

        render(`<div class="artemis-problem-statement">${taskSpan('A', '1')}${taskSpan('B', '2')}<!-- rerender --></div>`, [task(0, 'A', [1]), task(1, 'B', [2])], true);

        const reRenderedSecondTask = taskElements()[1];
        expect(reRenderedSecondTask).not.toBe(secondTask);
        expect(shadowRoot().activeElement).toBe(reRenderedSecondTask);
    });

    it('emits the document position of a clicked task, not its name', () => {
        const activated: number[] = [];
        fixture.componentInstance.taskActivated.subscribe((index) => activated.push(index));
        // Two tasks with the same name but different test ids.
        render(`<div class="artemis-problem-statement">${taskSpan('A', '1')}${taskSpan('A', '2,3')}</div>`, [task(0, 'A', [1]), task(1, 'A', [2, 3])], true);

        taskElements()[1].dispatchEvent(new MouseEvent('click', { bubbles: true, composed: true }));

        expect(activated).toEqual([1]);
    });

    it('emits on keyboard activation of a task', () => {
        const activated: number[] = [];
        fixture.componentInstance.taskActivated.subscribe((index) => activated.push(index));
        render(`<div class="artemis-problem-statement">${taskSpan('A', '1')}</div>`, [task(0, 'A', [1])], true);

        taskElements()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, composed: true }));

        expect(activated).toEqual([0]);
    });

    it('does not emit an activation while the tasks are not interactive', () => {
        const activated: number[] = [];
        fixture.componentInstance.taskActivated.subscribe((index) => activated.push(index));
        render(`<div class="artemis-problem-statement">${taskSpan('A', '1')}</div>`, [task(0, 'A', [1])]);

        // Retained markup stays clickable in the browser no matter what role and tabindex say, so the emission
        // itself has to be gated: after a context change the statement on screen belongs to other inputs.
        taskElements()[0].dispatchEvent(new MouseEvent('click', { bubbles: true, composed: true }));

        expect(activated).toEqual([]);
    });

    it('ignores activations outside a task', () => {
        const activated: number[] = [];
        fixture.componentInstance.taskActivated.subscribe((index) => activated.push(index));
        render(`<div class="artemis-problem-statement"><p id="prose">text</p>${taskSpan('A', '1')}</div>`, [task(0, 'A', [1])], true);

        shadowRoot()
            .querySelector('#prose')!
            .dispatchEvent(new MouseEvent('click', { bubbles: true, composed: true }));

        expect(activated).toEqual([]);
    });

    it('highlights a code block whose language is registered', () => {
        render(codeBlock('class Example {}', 'java'), []);

        const code = shadowRoot().querySelector('pre code')!;
        expect(code.classList.contains('hljs')).toBe(true);
        expect(code.querySelectorAll('span[class^="hljs-"]').length).toBeGreaterThan(0);
        // The highlighting only wraps the source, it never rewrites it.
        expect(code.textContent).toBe('class Example {}');
        expect(highlight).toHaveBeenCalledOnce();
        expect(highlight.mock.calls[0][1]).toEqual({ language: 'java', ignoreIllegals: true });
        expect(highlightAuto).not.toHaveBeenCalled();
    });

    it('leaves a code block with an unregistered language escaped, but still marks it', () => {
        // Mirrors markdown.conversion.util.ts:42-44: an explicit language highlight.js does not know falls back to the
        // escaped source, and gets the `hljs` class anyway. `hljs.highlightElement()` would auto-detect here instead.
        render(codeBlock('if (a &lt; b) {}', 'brainfuck'), []);

        const code = shadowRoot().querySelector('pre code')!;
        expect(code.classList.contains('hljs')).toBe(true);
        expect(code.innerHTML).toBe('if (a &lt; b) {}');
        expect(code.querySelectorAll('span')).toHaveLength(0);
        expect(highlight).not.toHaveBeenCalled();
        expect(highlightAuto).not.toHaveBeenCalled();
    });

    it('auto-detects the language of a code block that declares none', () => {
        render(codeBlock('SELECT name FROM student;'), []);

        const code = shadowRoot().querySelector('pre code')!;
        expect(highlightAuto).toHaveBeenCalledExactlyOnceWith('SELECT name FROM student;');
        expect(highlight).not.toHaveBeenCalled();
        expect(code.classList.contains('hljs')).toBe(true);
    });

    it('does not highlight a code block twice when the pass runs again over retained markup', () => {
        render(codeBlock('class Example {}', 'java'), []);
        const code = shadowRoot().querySelector('pre code')!;
        const highlighted = code.innerHTML;
        expect(code.getAttribute('data-highlighted')).toBe('true');
        expect(shadowRoot().querySelectorAll('pre code:not([data-highlighted])')).toHaveLength(0);

        // Invoked directly because no public path reaches the pass twice today: `applyToDom` returns early while the
        // html is unchanged. The marker is what makes idempotence a property of the pass itself instead of a
        // consequence of that early return, so this test has to drive the pass, not the input. Re-highlighting would
        // nest a second layer of token spans inside the first.
        fixture.componentInstance['highlightCodeBlocks'](shadowRoot().querySelector<HTMLElement>('.artemis-problem-statement-host')!);

        expect(shadowRoot().querySelector('pre code')).toBe(code);
        expect(code.innerHTML).toBe(highlighted);
        expect(highlight).toHaveBeenCalledOnce();
    });

    it('keeps the rest of the statement working when a code block fails to highlight', () => {
        highlight.mockImplementation(() => {
            throw new Error('grammar exploded');
        });
        render(`<div class="artemis-problem-statement">${taskSpan('A', '1')}<pre><code class="language-java">class Example {}</code></pre></div>`, [task(0, 'A', [1])], true);

        // Degrades to the plain source, exactly like the formula pass degrades to the plain formula.
        expect(shadowRoot().querySelector('pre code')!.textContent).toBe('class Example {}');
        // The failure must not cost the statement its accessibility attributes, which are applied after this pass.
        expect(taskElements()[0].getAttribute('role')).toBe('button');
    });

    it('binds the visual studio palette on the statement container and the monokai palette on its dark variant', () => {
        // jsdom applies neither a shadow root's stylesheets nor var() substitution, so the component's stylesheet is
        // compiled and exercised in the light dom instead. That still covers what can actually go wrong here: which
        // palette a container resolves to, and whether a token the dark palette does not style is released rather
        // than left at its light value.
        const style = document.createElement('style');
        style.textContent = compile('src/main/webapp/app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr-content.component.scss').css;
        document.head.appendChild(style);
        const containers = document.createElement('div');
        containers.innerHTML = '<div id="light" class="artemis-problem-statement"></div><div id="dark" class="artemis-problem-statement artemis-problem-statement--dark"></div>';
        document.body.appendChild(containers);

        const light = getComputedStyle(document.getElementById('light')!);
        const dark = getComputedStyle(document.getElementById('dark')!);
        expect(style.textContent).toContain('color: var(--hljs-keyword, inherit)');
        expect(light.getPropertyValue('--hljs-keyword')).toBe('#00f');
        expect(dark.getPropertyValue('--hljs-keyword')).toBe('#f92672');
        // Monokai gives doctag no colour of its own, and both classes sit on the same element, so the dark palette has
        // to release the binding instead of overwriting it. An empty computed value is what `initial` resolves to, and
        // it is what makes the token rule fall through to its `inherit` fallback.
        expect(light.getPropertyValue('--hljs-doctag')).toBe('#808080');
        expect(dark.getPropertyValue('--hljs-doctag')).toBe('');
        // `hljs-title class_` is what highlight.js emits for a class name, and Monokai's rule for it outranks the
        // generic title rule in both palettes. Visual Studio has no such rule, so its class names have to keep the
        // title colour rather than fall through to `inherit`.
        expect(light.getPropertyValue('--hljs-class-title')).toBe('#a31515');
        expect(dark.getPropertyValue('--hljs-class-title')).toBe('#fff');

        style.remove();
        containers.remove();
    });
});
