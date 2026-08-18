import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExerciseInstructionSsrStepWizardComponent } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr-step-wizard.component';
import { SsrTask } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';

describe('ProgrammingExerciseInstructionSsrStepWizardComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInstructionSsrStepWizardComponent>;
    let comp: ProgrammingExerciseInstructionSsrStepWizardComponent;

    // Indices deliberately do not match array position, so a reorder (see the tracking test below) moves a task
    // to a different position than its index would suggest.
    const tasks: SsrTask[] = [
        { index: 3, taskName: 'A', testIds: [1], status: 'success', authoredCount: 1, notExecutedCount: 0 },
        { index: 7, taskName: 'B', testIds: [2], status: 'fail', authoredCount: 1, notExecutedCount: 0 },
        { index: 11, taskName: 'C', testIds: [3], status: 'not-executed', authoredCount: 1, notExecutedCount: 1 },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseInstructionSsrStepWizardComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingExerciseInstructionSsrStepWizardComponent);
        comp = fixture.componentInstance;
    });

    it('renders one step per task with the server-provided status', () => {
        fixture.componentRef.setInput('tasks', tasks);
        fixture.detectChanges();

        const steps = fixture.nativeElement.querySelectorAll('.stepwizard-step');
        expect(steps).toHaveLength(3);
        expect(fixture.nativeElement.querySelectorAll('.stepwizard-step--success')).toHaveLength(1);
        expect(fixture.nativeElement.querySelectorAll('.stepwizard-step--failed')).toHaveLength(1);
        expect(fixture.nativeElement.querySelectorAll('.stepwizard-step--not-executed')).toHaveLength(1);
    });

    // Colour and icon shape carry the status, and neither reaches a screen reader. The key set is the one the
    // shadow-content path uses (ProgrammingExerciseInstructionSsrContentComponent.taskAriaLabel), so a task is
    // announced the same way whichever of the two activation paths the user reaches it through.
    it('names each circle with its task and status', () => {
        fixture.componentRef.setInput('tasks', tasks);
        fixture.detectChanges();

        const labels = [...fixture.nativeElement.querySelectorAll('.stepwizard-circle')].map((circle: HTMLElement) => circle.getAttribute('aria-label'));
        expect(labels).toEqual([
            'A: artemisApp.programmingExercise.problemStatement.taskStatus.success',
            'B: artemisApp.programmingExercise.problemStatement.taskStatus.fail',
            'C: artemisApp.programmingExercise.problemStatement.taskStatus.not-executed',
        ]);
    });

    it('renders duplicate task names as separate steps', () => {
        const duplicates: SsrTask[] = [
            { index: 5, taskName: 'Same', testIds: [1], status: 'success', authoredCount: 1, notExecutedCount: 0 },
            { index: 9, taskName: 'Same', testIds: [2], status: 'fail', authoredCount: 1, notExecutedCount: 0 },
        ];
        const emitted = vi.fn();
        comp.taskSelected.subscribe(emitted);
        fixture.componentRef.setInput('tasks', duplicates);
        fixture.componentRef.setInput('interactive', true);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelectorAll('.stepwizard-step')).toHaveLength(2);
        fixture.nativeElement.querySelectorAll('.stepwizard-circle')[1].click();
        expect(emitted).toHaveBeenCalledWith(duplicates[1]);
    });

    it('emits the selected task on click', () => {
        const emitted = vi.fn();
        comp.taskSelected.subscribe(emitted);
        fixture.componentRef.setInput('tasks', tasks);
        fixture.componentRef.setInput('interactive', true);
        fixture.detectChanges();

        const circles = fixture.nativeElement.querySelectorAll('.stepwizard-circle');
        expect([...circles].every((circle: HTMLButtonElement) => !circle.disabled)).toBe(true);
        circles[1].click();

        expect(emitted).toHaveBeenCalledWith(tasks[1]);
    });

    it('disables every step while no feedback dialog can be opened', () => {
        const emitted = vi.fn();
        comp.taskSelected.subscribe(emitted);
        fixture.componentRef.setInput('tasks', tasks);
        fixture.detectChanges();

        // The wizard is the second activation path into the feedback dialog; gating the shadow content alone would
        // still let a click here pair a stale result with the newly bound participation.
        const circles = fixture.nativeElement.querySelectorAll('.stepwizard-circle');
        expect([...circles].every((circle: HTMLButtonElement) => circle.disabled)).toBe(true);
        circles[1].click();

        expect(emitted).not.toHaveBeenCalled();
    });

    // The server emits an empty `data-test-ids` for two reachable cases: a task that authored no references at all
    // ("no-tests"), and a task whose name-only references resolved to nothing, which still shows a green circle when
    // the request declared that all tests passed. `openTaskFeedback` returns early for both, so an enabled button
    // would be a focusable control that does nothing.
    it.each([
        { status: 'no-tests' as const, case: 'a task that authored no test references' },
        { status: 'success' as const, case: 'an all-passed task whose name-only references did not resolve' },
    ])('disables a step without test ids while the rest stays interactive: $case', ({ status }) => {
        const emitted = vi.fn();
        comp.taskSelected.subscribe(emitted);
        fixture.componentRef.setInput('tasks', [{ index: 1, taskName: 'Nothing to show', testIds: [], status, authoredCount: 0, notExecutedCount: 0 }, tasks[0]]);
        fixture.componentRef.setInput('interactive', true);
        fixture.detectChanges();

        const circles = fixture.nativeElement.querySelectorAll('.stepwizard-circle');
        expect(circles[0].disabled).toBe(true);
        expect(circles[1].disabled).toBe(false);

        circles[0].click();
        expect(emitted).not.toHaveBeenCalled();

        circles[1].click();
        expect(emitted).toHaveBeenCalledExactlyOnceWith(tasks[0]);
    });

    it('renders nothing without tasks', () => {
        fixture.componentRef.setInput('tasks', []);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelectorAll('.stepwizard-step')).toHaveLength(0);
    });

    // A click-and-assert test can never distinguish `track task.index` from `track $index`: whichever task
    // object backs a given array position in the *current* render is emitted regardless of the track key. The
    // key only governs whether Angular reuses or recreates the underlying DOM node across a re-render. So this
    // test observes the one thing `track` actually controls: node identity across a reorder. Under
    // `track task.index`, moving a task to a new array position moves its existing button node with it; under
    // `track $index`, the node stays at its position and merely gets new content applied to it.
    it('keeps a task circle bound to its own DOM node when its array position changes', () => {
        fixture.componentRef.setInput('tasks', tasks);
        fixture.detectChanges();

        const firstNode = fixture.nativeElement.querySelectorAll('.stepwizard-circle')[0];

        // Swap the first two tasks: the task previously at position 0 (index 3) now sits at position 1.
        fixture.componentRef.setInput('tasks', [tasks[1], tasks[0], tasks[2]]);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelectorAll('.stepwizard-circle')[1]).toBe(firstNode);
    });
});
