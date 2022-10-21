import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import dayjs from 'dayjs/esm';
import { ArtemisTestModule } from '../../test.module';

describe('AssessmentWarningComponent', function () {
    let component: AssessmentWarningComponent;
    let fixture: ComponentFixture<AssessmentWarningComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [AssessmentWarningComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentWarningComponent);
                component = fixture.componentInstance;
            });
    });

    it('should not be before exercise due date if the exercise has no due date', () => {
        component.exercise = new ProgrammingExercise(undefined, undefined);

        component.ngOnChanges();

        expect(component.isBeforeExerciseDueDate).toBeFalse();
        expect(component.showWarning).toBeFalse();
    });

    it('should be before the exercise due date if the exercise due date is in the future', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.dueDate = dayjs().add(2, 'hours');
        component.exercise = exercise;

        component.ngOnChanges();

        expect(component.isBeforeExerciseDueDate).toBeTrue();
        expect(component.showWarning).toBeTrue();
    });

    it('should be before the latest due date if the exercise due date is in the past but individual due dates in the future', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.dueDate = dayjs().subtract(2, 'hours');

        const participation1 = new ProgrammingExerciseStudentParticipation();
        participation1.individualDueDate = dayjs().subtract(1, 'hours');
        const submission1 = new ProgrammingSubmission();
        submission1.participation = participation1;

        const participation2 = new ProgrammingExerciseStudentParticipation();
        participation2.individualDueDate = dayjs().add(1, 'hours');
        const submission2 = new ProgrammingSubmission();
        submission2.participation = participation2;

        const participation3 = new ProgrammingExerciseStudentParticipation();
        participation3.individualDueDate = undefined;
        const submission3 = new ProgrammingSubmission();
        submission3.participation = participation3;

        // a submission without participation should just be ignored and not change the result
        const submission4 = new ProgrammingSubmission();

        component.exercise = exercise;
        component.submissions = [submission2, submission4, submission3, submission1];
        component.ngOnChanges();

        expect(component.isBeforeExerciseDueDate).toBeFalse();
        expect(component.showWarning).toBeTrue();
    });
});
