import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import dayjs from 'dayjs';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/exercises/programming/shared/instructions-render/step-wizard/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExerciseInstructionService } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Task } from 'app/exercises/programming/shared/instructions-render/task/programming-exercise-task.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseInstructionStepWizardComponent', () => {
    let comp: ProgrammingExerciseInstructionStepWizardComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionStepWizardComponent>;
    let debugElement: DebugElement;

    const stepWizardStep = '.stepwizard-step';

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, NgbModule],
            declarations: [ProgrammingExerciseInstructionStepWizardComponent],
            providers: [ProgrammingExerciseInstructionService],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructionStepWizardComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    afterEach(() => {});

    it('Should load the expected number of steps according to the provided tests', () => {
        const result = {
            id: 1,
            completionDate: dayjs('2019-01-06T22:15:29.203+02:00'),
            feedbacks: [{ text: 'testBubbleSort', detail_text: 'lorem ipsum' }],
        } as any;
        const tasks = [
            { completeString: '[task][Implement BubbleSort](testBubbleSort)', taskName: 'Implement BubbleSort', tests: ['testBubbleSort'] } as Task,
            { completeString: '[task][Implement MergeSort](testMergeSort)', taskName: 'Implement MergeSort', tests: ['testMergeSort'] } as Task,
        ];
        comp.latestResult = result;
        comp.tasks = tasks;

        triggerChanges(comp, { property: 'tasks', currentValue: tasks }, { property: 'latestResult', currentValue: result });
        fixture.detectChanges();

        const steps = debugElement.queryAll(By.css(stepWizardStep));
        expect(steps).to.have.lengthOf(2);

        // BubbleSort has a failed icon.
        expect(steps[0].query(By.css('.text-danger'))).to.exist;
        // MergeSort has a success icon.
        expect(steps[1].query(By.css('.text-success'))).to.exist;
    });

    it('Should not show any icons for empty tasks list', () => {
        const result = {
            id: 1,
            completionDate: dayjs('2019-01-06T22:15:29.203+02:00'),
            feedbacks: [{ text: 'testBubbleSort', detail_text: 'lorem ipsum' }],
        } as any;
        comp.latestResult = result;
        comp.tasks = [];

        triggerChanges(comp, { property: 'tasks', currentValue: [] }, { property: 'latestResult', currentValue: result });
        fixture.detectChanges();

        const steps = debugElement.queryAll(By.css(stepWizardStep));
        expect(steps).to.have.lengthOf(0);
    });
});
