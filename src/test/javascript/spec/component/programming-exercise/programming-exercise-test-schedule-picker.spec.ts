import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExerciseLifecycleComponent } from 'app/entities/programming-exercise/programming-exercise-test-schedule-picker/programming-exercise-lifecycle.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise/programming-exercise.model';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/entities/programming-exercise/programming-exercise-test-schedule-picker/programming-exercise-test-schedule-date-picker.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseTestSchedulePickerComponent', () => {
    let comp: ProgrammingExerciseLifecycleComponent;
    let fixture: ComponentFixture<ProgrammingExerciseLifecycleComponent>;
    let debugElement: DebugElement;

    const nextDueDate = moment().add(5, 'days');
    const afterDueDate = moment().add(7, 'days');
    const exercise = { id: 42, dueDate: nextDueDate, buildAndTestStudentSubmissionsAfterDueDate: afterDueDate } as ProgrammingExercise;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            declarations: [ProgrammingExerciseLifecycleComponent, MockComponent(ProgrammingExerciseTestScheduleDatePickerComponent), MockComponent(HelpIconComponent)],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseLifecycleComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('should do nothing if the release date is set to null', () => {
        comp.exercise = exercise;
        comp.updateReleaseDate(null);

        expect(comp.exercise.dueDate).to.be.equal(nextDueDate);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).to.be.equal(afterDueDate);
    });

    it('should only reset the due date if the release date is between the due date and the after due date', () => {
        comp.exercise = exercise;
        const newRelease = moment().add(6, 'days');
        comp.updateReleaseDate(newRelease);

        expect(comp.exercise.dueDate).to.be.equal(newRelease);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).to.be.equal(afterDueDate);
    });

    it('should reset both the due date and the after due date if the new release is after both dates', () => {
        comp.exercise = exercise;
        const newRelease = moment().add(8, 'days');
        comp.updateReleaseDate(newRelease);

        expect(comp.exercise.dueDate).to.be.equal(newRelease);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).to.be.equal(newRelease);
    });
});
