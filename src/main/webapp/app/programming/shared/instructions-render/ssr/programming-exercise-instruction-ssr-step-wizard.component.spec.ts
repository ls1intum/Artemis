import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExerciseInstructionSsrStepWizardComponent } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr-step-wizard.component';
import { SsrTask } from 'app/programming/shared/instructions-render/ssr/programming-exercise-instruction-ssr.component';

describe('ProgrammingExerciseInstructionSsrStepWizardComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseInstructionSsrStepWizardComponent>;
    let comp: ProgrammingExerciseInstructionSsrStepWizardComponent;

    // Indices deliberately do not match array position: a click on the middle circle must emit the task's own
    // server-assigned index (7), not the array position (1) — the assertion below would pass under a buggy
    // `track $index` implementation if these were sequential, since 0/1/2 are indistinguishable from position.
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
        // The clicked circle sits at array position 1, but its server-assigned index is 9 — asserting the
        // property directly proves the emission carries the task's own index, not the iteration position.
        expect(emitted.mock.calls[0][0].index).toBe(9);
    });

    it('emits the selected task on click', () => {
        const emitted = vi.fn();
        comp.taskSelected.subscribe(emitted);
        fixture.componentRef.setInput('tasks', tasks);
        fixture.detectChanges();

        // Middle circle is at array position 1, whose task carries index 7 (not 1).
        fixture.nativeElement.querySelectorAll('.stepwizard-circle')[1].click();

        expect(emitted).toHaveBeenCalledWith(tasks[1]);
        expect(emitted.mock.calls[0][0].index).toBe(7);
    });

    it('renders nothing without tasks', () => {
        fixture.componentRef.setInput('tasks', []);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelectorAll('.stepwizard-step')).toHaveLength(0);
    });
});
