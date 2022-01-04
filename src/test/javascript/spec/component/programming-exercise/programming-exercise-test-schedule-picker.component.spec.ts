import dayjs from 'dayjs/esm';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-test-schedule-date-picker.component';
import { NgModel } from '@angular/forms';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';

describe('ProgrammingExerciseTestSchedulePickerComponent', () => {
    let comp: ProgrammingExerciseLifecycleComponent;
    let fixture: ComponentFixture<ProgrammingExerciseLifecycleComponent>;

    const nextDueDate = dayjs().add(5, 'days');
    const afterDueDate = dayjs().add(7, 'days');
    const exercise = { id: 42, dueDate: nextDueDate, buildAndTestStudentSubmissionsAfterDueDate: afterDueDate } as ProgrammingExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ProgrammingExerciseLifecycleComponent,
                MockComponent(ProgrammingExerciseTestScheduleDatePickerComponent),
                MockComponent(HelpIconComponent),
                MockDirective(NgModel),
                TranslatePipeMock,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseLifecycleComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should do nothing if the release date is set to null', () => {
        comp.exercise = exercise;
        comp.updateReleaseDate(undefined);

        expect(comp.exercise.dueDate).toEqual(nextDueDate);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).toEqual(afterDueDate);
    });

    it('should only reset the due date if the release date is between the due date and the after due date', () => {
        comp.exercise = exercise;
        const newRelease = dayjs().add(6, 'days');
        comp.updateReleaseDate(newRelease);

        expect(comp.exercise.dueDate).toEqual(newRelease);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).toEqual(afterDueDate);
    });

    it('should reset both the due date and the after due date if the new release is after both dates', () => {
        comp.exercise = exercise;
        const newRelease = dayjs().add(8, 'days');
        comp.updateReleaseDate(newRelease);

        expect(comp.exercise.dueDate).toEqual(newRelease);
        expect(comp.exercise.buildAndTestStudentSubmissionsAfterDueDate).toEqual(newRelease);
    });
});
