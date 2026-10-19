import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { AssessmentWarningComponent } from 'app/assessment/manage/assessment-warning/assessment-warning.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Course } from 'app/course/shared/entities/course.model';
import { MockDirective, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';

describe('AssessmentWarningComponent', () => {
    let component: AssessmentWarningComponent;
    let fixture: ComponentFixture<AssessmentWarningComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(TranslateService)],
        })
            .overrideComponent(AssessmentWarningComponent, {
                remove: { imports: [TranslateDirective] },
                add: { imports: [MockDirective(TranslateDirective)] },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AssessmentWarningComponent);
                component = fixture.componentInstance;
            });
    });

    it('should not be before exercise due date if the exercise has no due date', async () => {
        fixture.componentRef.setInput('exercise', new ProgrammingExercise(undefined, undefined));

        await fixture.whenStable();

        expect(component.isBeforeExerciseDueDate()).toBe(false);
        expect(component.showWarning()).toBe(false);
    });

    it('should be before the exercise due date if the exercise due date is in the future', async () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.dueDate = dayjs().add(2, 'hours');
        fixture.componentRef.setInput('exercise', exercise);

        await fixture.whenStable();

        expect(component.isBeforeExerciseDueDate()).toBe(true);
        expect(component.showWarning()).toBe(true);
    });

    it('should be before the latest due date if the exercise due date is in the past but individual due dates in the future', async () => {
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
        await fixture.whenStable();

        expect(component.isBeforeExerciseDueDate()).toBe(false);
        expect(component.showWarning()).toBe(true);
    });

    it('should still show the warning before the due date when the course has formative Athena feedback enabled but the submission is not a feedback request', async () => {
        const exercise = new ProgrammingExercise(new Course(), undefined);
        exercise.dueDate = dayjs().add(2, 'hours');
        exercise.course!.athenaFormativeFeedbackEnabled = true;

        const submission = new ProgrammingSubmission();
        submission.results = [{ assessmentType: AssessmentType.MANUAL } as Result];

        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('submissions', [submission]);
        await fixture.whenStable();

        expect(component.showWarning()).toBe(true);
    });

    it('should not show the warning before the due date when the submission carries an AUTOMATIC_ATHENA feedback request result', async () => {
        const exercise = new ProgrammingExercise(new Course(), undefined);
        exercise.dueDate = dayjs().add(2, 'hours');

        const submission = new ProgrammingSubmission();
        submission.results = [{ assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result];

        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('submissions', [submission]);
        await fixture.whenStable();

        expect(component.showWarning()).toBe(false);
    });
});
