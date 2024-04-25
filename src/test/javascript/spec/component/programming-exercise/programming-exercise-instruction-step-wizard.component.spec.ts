import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/exercises/programming/shared/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionService } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { Task } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';
import { MockModule, MockPipe } from 'ng-mocks';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

describe('ProgrammingExerciseInstructionStepWizardComponent', () => {
    let comp: ProgrammingExerciseInstructionStepWizardComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionStepWizardComponent>;
    let debugElement: DebugElement;

    const stepWizardStep = '.stepwizard-step';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgbTooltipModule)],
            declarations: [ProgrammingExerciseInstructionStepWizardComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [ProgrammingExerciseInstructionService],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructionStepWizardComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    afterEach(() => {});

    it('should load the expected number of steps according to the provided tests', () => {
        const result: Result = {
            id: 1,
            completionDate: dayjs('2022-01-06T22:15:29.203+02:00'),
            feedbacks: [
                { testCase: { testName: 'testBubbleSort', id: 1 }, positive: false, detailText: 'lorem ipsum' },
                { testCase: { testName: 'testMergeSort', id: 2 }, positive: true, detailText: 'lorem ipsum' },
            ],
        };
        const tasks: Task[] = [
            { id: 1, completeString: '[task][Implement BubbleSort](1)', taskName: 'Implement BubbleSort', testIds: [1] },
            { id: 2, completeString: '[task][Implement MergeSort](2)', taskName: 'Implement MergeSort', testIds: [2] },
        ];
        comp.latestResult = result;
        comp.tasks = tasks;

        triggerChanges(comp, { property: 'tasks', currentValue: tasks }, { property: 'latestResult', currentValue: result });
        fixture.detectChanges();

        const steps = debugElement.queryAll(By.css(stepWizardStep));
        expect(steps).toHaveLength(2);

        // BubbleSort has a failed icon.
        expect(steps[0].query(By.css('.text-danger'))).not.toBeNull();
        // MergeSort has a success icon.
        expect(steps[1].query(By.css('.text-success'))).not.toBeNull();
    });

    it('should not show any icons for empty tasks list', () => {
        const result: Result = {
            id: 1,
            completionDate: dayjs('2022-01-06T22:15:29.203+02:00'),
            feedbacks: [{ testCase: { testName: 'testBubbleSort' }, detailText: 'lorem ipsum' }],
        };
        comp.latestResult = result;
        comp.tasks = [];

        triggerChanges(comp, { property: 'tasks', currentValue: [] }, { property: 'latestResult', currentValue: result });
        fixture.detectChanges();

        const steps = debugElement.queryAll(By.css(stepWizardStep));
        expect(steps).toHaveLength(0);
    });
});
