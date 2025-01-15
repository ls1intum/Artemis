import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { TextExercise } from 'app/entities/text/text-exercise.model';
import { TextSubmission } from 'app/entities/text/text-submission.model';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { ArtemisTestModule } from '../../../../test.module';
import dayjs from 'dayjs/esm';
import { ExerciseSaveButtonComponent } from 'app/exam/participate/exercises/exercise-save-button/exercise-save-button.component';

describe('TextExamSubmissionComponent', () => {
    let fixture: ComponentFixture<TextExamSubmissionComponent>;
    let component: TextExamSubmissionComponent;

    let textSubmission: TextSubmission;
    let exercise: TextExercise;

    beforeEach(() => {
        textSubmission = new TextSubmission();
        exercise = new TextExercise(new Course(), new ExerciseGroup());

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextExamSubmissionComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        component.exercise = exercise;
        textSubmission.text = 'Hello World';
        component.studentSubmission = textSubmission;

        component.onActivate();
        fixture.detectChanges();
        component.onDeactivate();

        expect(component.answer).toBe('Hello World');
        expect(component.wordCount).toBe(2);
        expect(component.characterCount).toBe(11);
        expect(component.getExerciseId()).toEqual(exercise.id);
        expect(component.getSubmission()).toEqual(textSubmission);
    });

    it('should initialize with empty answer', () => {
        component.exercise = exercise;
        component.studentSubmission = textSubmission;

        fixture.detectChanges();

        expect(component.answer).toBe('');
        expect(component.wordCount).toBe(0);
        expect(component.characterCount).toBe(0);
    });

    it('should return the negation of student submission isSynced value', () => {
        component.exercise = exercise;
        component.studentSubmission = textSubmission;
        component.studentSubmission.isSynced = false;

        fixture.detectChanges();

        expect(component.hasUnsavedChanges()).toBeTrue();
    });

    it('should update text of the submission', () => {
        component.exercise = exercise;
        textSubmission.text = 'Text';
        component.studentSubmission = textSubmission;

        fixture.detectChanges();
        component.updateSubmissionFromView();

        expect(component.studentSubmission.text).toBe('Text');
    });

    it('should update problem statement of the exercise', () => {
        component.exercise = exercise;
        component.exercise.problemStatement = 'old problem statement';
        const newProblemStatement = 'new problem statement';

        component.updateProblemStatement(newProblemStatement);

        expect(component.problemStatementHtml).toBe(newProblemStatement);
    });

    it('should trigger text editor events', fakeAsync(() => {
        component.exercise = exercise;
        textSubmission.text = 'Hello World';
        component.studentSubmission = textSubmission;

        fixture.detectChanges();
        fixture.whenStable().then(() => {
            expect(component.answer).toBe('Hello World');
            const textareaDebugElement = fixture.debugElement.query(By.css('#text-editor'));
            expect(textareaDebugElement).not.toBeNull();
            const textarea = textareaDebugElement.nativeElement;
            textarea.value = 'Test';
            textarea.dispatchEvent(new Event('input'));

            textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }));
            textarea.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            expect(textarea.value).toBe('Test\t');
            expect(component.studentSubmission.isSynced).toBeFalse();
        });
    }));

    it('should update the answer if the submission version changes', () => {
        const submissionVersion = { id: 1, content: 'submission version', submission: textSubmission, createdDate: dayjs('2021-01-01') };
        component.setSubmissionVersion(submissionVersion);
        expect(component.answer).toBe('submission version');
        expect(component.submissionVersion).toBe(submissionVersion);
    });

    it('should call triggerSave if save exercise button is clicked', () => {
        component.exercise = exercise;
        textSubmission.text = 'Hello World';
        component.studentSubmission = textSubmission;
        fixture.detectChanges();
        const saveExerciseSpy = jest.spyOn(component, 'notifyTriggerSave');
        const saveButton = fixture.debugElement.query(By.directive(ExerciseSaveButtonComponent));
        saveButton.triggerEventHandler('save', null);
        expect(saveExerciseSpy).toHaveBeenCalledOnce();
    });
});
