import * as chai from 'chai';
import { MockModule, MockPipe, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Course } from 'app/entities/course.model';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { JhiAlertService } from 'ng-jhipster';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisSharedModule } from 'app/shared/shared.module';

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
            imports: [
                RouterTestingModule.withRoutes([]),
                MockModule(ArtemisQuizQuestionTypesModule),
                MockModule(NgbModule),
                MockModule(FormsModule),
                MockModule(FontAwesomeModule),
                MockModule(ArtemisSharedModule),
            ],
            declarations: [TextExamSubmissionComponent, MockPipe(TranslatePipe), MockPipe(HtmlForMarkdownPipe)],
            providers: [MockProvider(TextEditorService), MockProvider(JhiAlertService), MockProvider(TranslateService), MockProvider(ArtemisMarkdownService)],
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

        fixture.detectChanges();

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
});
