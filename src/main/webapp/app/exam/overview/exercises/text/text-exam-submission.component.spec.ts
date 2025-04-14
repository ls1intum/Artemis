import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { TextExamSubmissionComponent } from 'app/exam/overview/exercises/text/text-exam-submission.component';
import dayjs from 'dayjs/esm';
import { ExerciseSaveButtonComponent } from 'app/exam/overview/exercises/exercise-save-button/exercise-save-button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('TextExamSubmissionComponent', () => {
    let fixture: ComponentFixture<TextExamSubmissionComponent>;
    let component: TextExamSubmissionComponent;

    let textSubmission: TextSubmission;
    let exercise: TextExercise;

    beforeEach(() => {
        textSubmission = new TextSubmission();
        exercise = new TextExercise(new Course(), new ExerciseGroup());

        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
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
        fixture.componentRef.setInput('exercise', exercise);
        textSubmission.text = 'Hello World';
        fixture.componentRef.setInput('studentSubmission', textSubmission);

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
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('studentSubmission', textSubmission);

        fixture.detectChanges();

        expect(component.answer).toBe('');
        expect(component.wordCount).toBe(0);
        expect(component.characterCount).toBe(0);
    });

    it('should return the negation of student submission isSynced value', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('studentSubmission', textSubmission);
        component.studentSubmission().isSynced = false;

        fixture.detectChanges();

        expect(component.hasUnsavedChanges()).toBeTrue();
    });

    it('should update text of the submission', () => {
        fixture.componentRef.setInput('exercise', exercise);
        textSubmission.text = 'Text';
        fixture.componentRef.setInput('studentSubmission', textSubmission);

        fixture.detectChanges();
        component.updateSubmissionFromView();

        expect(component.studentSubmission().text).toBe('Text');
    });

    it('should update problem statement of the exercise', () => {
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('studentSubmission', textSubmission);
        component.exercise().problemStatement = 'old problem statement';
        const newProblemStatement = 'new problem statement';

        component.updateProblemStatement(newProblemStatement);

        expect(component.problemStatementHtml).toBe(newProblemStatement);
    });

    it('should trigger text editor events', fakeAsync(() => {
        fixture.componentRef.setInput('exercise', exercise);
        textSubmission.text = 'Hello World';
        fixture.componentRef.setInput('studentSubmission', textSubmission);

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
            expect(component.studentSubmission().isSynced).toBeFalse();
        });
    }));

    it('should update the answer if the submission version changes', () => {
        const submissionVersion = { id: 1, content: 'submission version', submission: textSubmission, createdDate: dayjs('2021-01-01') };
        component.setSubmissionVersion(submissionVersion);
        expect(component.answer).toBe('submission version');
        expect(component.submissionVersion).toBe(submissionVersion);
    });

    it('should call triggerSave if save exercise button is clicked', () => {
        fixture.componentRef.setInput('exercise', exercise);
        textSubmission.text = 'Hello World';
        fixture.componentRef.setInput('studentSubmission', textSubmission);
        fixture.detectChanges();
        const saveExerciseSpy = jest.spyOn(component, 'notifyTriggerSave');
        const saveButton = fixture.debugElement.query(By.directive(ExerciseSaveButtonComponent));
        saveButton.triggerEventHandler('save', null);
        expect(saveExerciseSpy).toHaveBeenCalledOnce();
    });
});
