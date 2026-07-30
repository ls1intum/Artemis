import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import katex from 'katex';
import { ProgrammingExerciseInstructionSsrContentComponent } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr-content.component';
import { SsrTask } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';
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

const task = (index: number, taskName: string, testIds: number[], status = 'success'): SsrTask => ({
    index,
    taskName,
    testIds,
    status,
    authoredCount: testIds.length,
    notExecutedCount: 0,
});

describe('ProgrammingExerciseInstructionSsrContentComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInstructionSsrContentComponent>;

    beforeEach(async () => {
        vi.mocked(katex.render).mockClear();
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

        // Same html — the DOM is deliberately not replaced, but the gating changed and must take effect anyway.
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

    it('ignores activations outside a task', () => {
        const activated: number[] = [];
        fixture.componentInstance.taskActivated.subscribe((index) => activated.push(index));
        render(`<div class="artemis-problem-statement"><p id="prose">text</p>${taskSpan('A', '1')}</div>`, [task(0, 'A', [1])], true);

        shadowRoot()
            .querySelector('#prose')!
            .dispatchEvent(new MouseEvent('click', { bubbles: true, composed: true }));

        expect(activated).toEqual([]);
    });
});
