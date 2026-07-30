import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExerciseInstructionSsrStepWizardComponent } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr-step-wizard.component';
import { SsrTask } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr.component';

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

    it('renders duplicate task names as separate steps', () => {
        const duplicates: SsrTask[] = [
            { index: 5, taskName: 'Same', testIds: [1], status: 'success', authoredCount: 1, notExecutedCount: 0 },
            { index: 9, taskName: 'Same', testIds: [2], status: 'fail', authoredCount: 1, notExecutedCount: 0 },
        ];
        const emitted = vi.fn();
        comp.taskSelected.subscribe(emitted);
        fixture.componentRef.setInput('tasks', duplicates);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelectorAll('.stepwizard-step')).toHaveLength(2);
        fixture.nativeElement.querySelectorAll('.stepwizard-circle')[1].click();
        expect(emitted).toHaveBeenCalledWith(duplicates[1]);
    });

    it('emits the selected task on click', () => {
        const emitted = vi.fn();
        comp.taskSelected.subscribe(emitted);
        fixture.componentRef.setInput('tasks', tasks);
        fixture.detectChanges();

        fixture.nativeElement.querySelectorAll('.stepwizard-circle')[1].click();

        expect(emitted).toHaveBeenCalledWith(tasks[1]);
    });

    it('renders nothing without tasks', () => {
        fixture.componentRef.setInput('tasks', []);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelectorAll('.stepwizard-step')).toHaveLength(0);
    });

    // A click-and-assert test can never distinguish `track task.index` from `track $index`: whichever task
    // object backs a given array position in the *current* render is emitted regardless of the track key — the
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
