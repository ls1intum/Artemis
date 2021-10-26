import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import * as chai from 'chai';
import { AlertService } from 'app/core/util/alert.service';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { ArtemisTestModule } from '../../../../test.module';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('TextExamSubmissionComponent', () => {
    let fixture: ComponentFixture<TextExamSubmissionComponent>;
    let component: TextExamSubmissionComponent;

    let textSubmission: TextSubmission;
    let exercise: TextExercise;

    beforeEach(() => {
        textSubmission = new TextSubmission();
        exercise = new TextExercise(new Course(), new ExerciseGroup());

        return TestBed.configureTestingModule({
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
            providers: [MockProvider(TextEditorService), MockProvider(AlertService), MockProvider(TranslateService), MockProvider(ArtemisMarkdownService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextExamSubmissionComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        component.exercise = exercise;
        textSubmission.text = 'Hello World';
        component.studentSubmission = textSubmission;

        component.onActivate();
        fixture.detectChanges();
        component.onDeactivate();

        expect(component.answer).to.equal('Hello World');
        expect(component.wordCount).to.equal(2);
        expect(component.characterCount).to.equal(11);
        expect(component.getExercise()).to.equal(exercise);
        expect(component.getSubmission()).to.equal(textSubmission);
    });

    it('should initialize with empty answer', () => {
        component.exercise = exercise;
        component.studentSubmission = textSubmission;

        fixture.detectChanges();

        expect(component.answer).to.equal('');
        expect(component.wordCount).to.equal(0);
        expect(component.characterCount).to.equal(0);
    });

    it('should return the negation of student submission isSynced value', () => {
        component.exercise = exercise;
        component.studentSubmission = textSubmission;
        component.studentSubmission.isSynced = false;

        fixture.detectChanges();

        expect(component.hasUnsavedChanges()).to.equal(true);
    });

    it('should update text of the submission', () => {
        component.exercise = exercise;
        textSubmission.text = 'Text';
        component.studentSubmission = textSubmission;

        fixture.detectChanges();
        component.updateSubmissionFromView();

        expect(component.studentSubmission.text).to.equal('Text');
    });

    it('should update problem statement of the exercise', () => {
        component.exercise = exercise;
        component.exercise.problemStatement = 'old problem statement';
        const newProblemStatement = 'new problem statement';

        component.updateProblemStatement(newProblemStatement);

        expect(component.exercise.problemStatement).to.equal(newProblemStatement);
    });

    it('should trigger text editor events', () => {
        component.exercise = exercise;
        textSubmission.text = 'Hello World';
        component.studentSubmission = textSubmission;

        fixture.detectChanges();
        fixture.whenStable().then(() => {
            expect(component.answer).to.equal('Hello World');
            const textareaDebugElement = fixture.debugElement.query(By.css('#text-editor-tab'));
            expect(textareaDebugElement).to.exist;
            const textarea = textareaDebugElement.nativeElement;
            textarea.value = 'Test';
            textarea.dispatchEvent(new Event('input'));

            textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }));
            textarea.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            expect(textarea.value).to.equal('Test\t');
            expect(component.studentSubmission.isSynced).to.be.false;
        });
    });
});
