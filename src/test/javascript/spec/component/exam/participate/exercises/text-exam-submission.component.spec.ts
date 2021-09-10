import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { By } from '@angular/platform-browser';
import { RouterTestingModule } from '@angular/router/testing';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import * as chai from 'chai';
import { AlertService } from 'app/core/util/alert.service';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject } from 'rxjs';
import { ExamExerciseUpdate, ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { Exercise } from 'app/entities/exercise.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('TextExamSubmissionComponent', () => {
    let fixture: ComponentFixture<TextExamSubmissionComponent>;
    let component: TextExamSubmissionComponent;

    let textSubmission: TextSubmission;
    let exercise: TextExercise;

    const examExerciseIdAndProblemStatementSourceMock = new BehaviorSubject<ExamExerciseUpdate>({ exerciseId: -1, problemStatement: 'initialProblemStatementValue' });
    const mockExamExerciseUpdateService = {
        currentExerciseIdAndProblemStatement: examExerciseIdAndProblemStatementSourceMock.asObservable(),
    };

    beforeEach(() => {
        textSubmission = new TextSubmission();
        exercise = new TextExercise(new Course(), new ExerciseGroup());

        return TestBed.configureTestingModule({
            imports: [
                RouterTestingModule.withRoutes([]),
                MockModule(ArtemisQuizQuestionTypesModule),
                MockModule(NgbModule),
                MockModule(FormsModule),
                MockModule(FontAwesomeModule),
                MockModule(ArtemisSharedModule),
            ],
            declarations: [TextExamSubmissionComponent, MockPipe(ArtemisTranslatePipe), MockPipe(HtmlForMarkdownPipe), MockComponent(IncludedInScoreBadgeComponent)],
            providers: [
                MockProvider(TextEditorService),
                MockProvider(AlertService),
                MockProvider(TranslateService),
                MockProvider(ArtemisMarkdownService),
                { provide: ExamExerciseUpdateService, useValue: mockExamExerciseUpdateService },
            ],
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

    describe('TextExamSubmissionComponent', () => {
        const oldProblemStatement = 'problem statement with errors';
        const updatedProblemStatement = 'new updated ProblemStatement';
        const exerciseDummy = { id: 42, problemStatement: oldProblemStatement } as Exercise;

        beforeEach(() => {
            component.exercise = exerciseDummy;
            component.studentSubmission = textSubmission;
            const exerciseId = component.getExercise().id!;
            const update = { exerciseId, problemStatement: updatedProblemStatement };

            fixture.detectChanges();
            examExerciseIdAndProblemStatementSourceMock.next(update);
        });

        it('should update problem statement', () => {
            // not component.getExercise().problemStatement, due to inserted HTML via Diff-Highlighting
            const result = component.updatedProblemStatement;
            expect(result).to.equal(updatedProblemStatement);
            expect(result).not.to.equal(oldProblemStatement);
        });

        it('should highlight differences', () => {
            const result = component.getExercise().problemStatement;
            expect(result).to.equal(component.updatedProblemStatementWithHighlightedDifferences);
        });

        it('should display different problem statement after button was clicked', () => {
            const button = fixture.debugElement.nativeElement.querySelector('#highlightDiffButton');
            button.click();
            const result = component.getExercise().problemStatement;
            expect(result).to.equal(updatedProblemStatement);
            expect(result).not.to.equal(component.updatedProblemStatementWithHighlightedDifferences);
        });
    });
});
