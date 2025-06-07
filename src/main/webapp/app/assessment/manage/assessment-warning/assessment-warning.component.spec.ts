import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { AssessmentWarningComponent } from 'app/assessment/manage/assessment-warning/assessment-warning.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';

describe('AssessmentWarningComponent', () => {
    let component: AssessmentWarningComponent;
    let fixture: ComponentFixture<AssessmentWarningComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({})
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentWarningComponent);
                component = fixture.componentInstance;
            });
    });

    it('should not be before exercise due date if the exercise has no due date', () => {
        fixture.componentRef.setInput('exercise', new ProgrammingExercise(undefined, undefined));

        component.ngOnChanges();

        expect(component.isBeforeExerciseDueDate).toBeFalse();
        expect(component.showWarning).toBeFalse();
    });

    it('should be before the exercise due date if the exercise due date is in the future', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.dueDate = dayjs().add(2, 'hours');
        fixture.componentRef.setInput('exercise', exercise);

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

        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('submissions', [submission2, submission4, submission3, submission1]);
        component.ngOnChanges();

        expect(component.isBeforeExerciseDueDate).toBeFalse();
        expect(component.showWarning).toBeTrue();
    });
});
