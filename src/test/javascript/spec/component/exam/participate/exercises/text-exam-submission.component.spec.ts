import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../../../test.module';

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
            declarations: [
                TextExamSubmissionComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockDirective(NgModel),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(ExamExerciseUpdateHighlighterComponent),
                MockComponent(ResizeableContainerComponent),
            ],
            providers: [MockProvider(TextEditorService), MockProvider(ArtemisMarkdownService)],
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
        expect(component.getExercise()).toEqual(exercise);
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

        expect(component.exercise.problemStatement).toBe(newProblemStatement);
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
});
