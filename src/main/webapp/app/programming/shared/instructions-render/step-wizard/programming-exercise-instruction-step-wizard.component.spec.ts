import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/programming/shared/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionService } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { Task } from 'app/programming/shared/instructions-render/task/programming-exercise-task.model';
import { MockModule, MockPipe } from 'ng-mocks';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ProgrammingExerciseInstructionStepWizardComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseInstructionStepWizardComponent>;
    let debugElement: DebugElement;

    const stepWizardStep = '.stepwizard-step';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule), ProgrammingExerciseInstructionStepWizardComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [ProgrammingExerciseInstructionService, { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseInstructionStepWizardComponent);
        debugElement = fixture.debugElement;
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
        fixture.componentRef.setInput('latestResult', result);
        fixture.componentRef.setInput('tasks', tasks);
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
        fixture.componentRef.setInput('latestResult', result);
        fixture.componentRef.setInput('tasks', []);
        fixture.detectChanges();

        const steps = debugElement.queryAll(By.css(stepWizardStep));
        expect(steps).toHaveLength(0);
    });
});
