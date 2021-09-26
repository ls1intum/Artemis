import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import dayjs from 'dayjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-test-schedule-date-picker.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseTestSchedulePickerComponent', () => {
    let comp: ProgrammingExerciseLifecycleComponent;
    let fixture: ComponentFixture<ProgrammingExerciseLifecycleComponent>;

    const nextDueDate = dayjs().add(5, 'days');
    const afterDueDate = dayjs().add(7, 'days');
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
            });
    });

    it('should do nothing if the release date is set to null', () => {
        comp.exercise = exercise;
        comp.updateReleaseDate(undefined);

        expect(comp.exercise.dueDate).to.be.equal(nextDueDate);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).to.be.equal(afterDueDate);
    });

    it('should only reset the due date if the release date is between the due date and the after due date', () => {
        comp.exercise = exercise;
        const newRelease = dayjs().add(6, 'days');
        comp.updateReleaseDate(newRelease);

        expect(comp.exercise.dueDate).to.be.equal(newRelease);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).to.be.equal(afterDueDate);
    });

    it('should reset both the due date and the after due date if the new release is after both dates', () => {
        comp.exercise = exercise;
        const newRelease = dayjs().add(8, 'days');
        comp.updateReleaseDate(newRelease);

        expect(comp.exercise.dueDate).to.be.equal(newRelease);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).to.be.equal(newRelease);
    });
});
