import * as chai from 'chai';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Course } from 'app/entities/course.model';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { JhiAlertService } from 'ng-jhipster';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { FileUploadExamSubmissionComponent } from 'app/exam/participate/exercises/file-upload/file-upload-exam-submission.component';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { HttpClientModule } from '@angular/common/http';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

chai.use(sinonChai);
const expect = chai.expect;

describe('FileUploadExamSubmissionComponent', () => {
    let fixture: ComponentFixture<FileUploadExamSubmissionComponent>;
    let component: FileUploadExamSubmissionComponent;

    let fileUploadSubmission: FileUploadSubmission;
    let exercise: FileUploadExercise;

    beforeEach(() => {
        fileUploadSubmission = new FileUploadSubmission();
        exercise = new FileUploadExercise(new Course(), new ExerciseGroup());

        return TestBed.configureTestingModule({
            imports: [
                RouterTestingModule.withRoutes([]),
                MockModule(ArtemisQuizQuestionTypesModule),
                MockModule(NgbModule),
                MockModule(FormsModule),
                MockModule(FontAwesomeModule),
                MockModule(ArtemisSharedModule),
                HttpClientTestingModule,
                SessionStorageService,
            ],
            declarations: [FileUploadExamSubmissionComponent, MockPipe(TranslatePipe), MockPipe(HtmlForMarkdownPipe), MockComponent(IncludedInScoreBadgeComponent)],
            providers: [MockProvider(JhiAlertService), MockProvider(TranslateService), MockProvider(ArtemisMarkdownService), SessionStorageService],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadExamSubmissionComponent);
                component = fixture.componentInstance;
            });
    });
    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        component.exercise = exercise;
        fileUploadSubmission.filePath = 'some/link/file.png';
        component.studentSubmission = fileUploadSubmission;

        component.onActivate();
        fixture.detectChanges();
        component.onDeactivate();

        expect(component.submittedFileName).to.equal(undefined);
        expect(component.filePath).to.equal('some/link/file.png');
        expect(component.getExercise()).to.equal(exercise);
        expect(component.getSubmission()).to.equal(fileUploadSubmission);
    });

    it('should initialize with no filePath', () => {
        component.exercise = exercise;
        component.studentSubmission = fileUploadSubmission;

        fixture.detectChanges();

        expect(component.filePath).to.equal(undefined);
    });

    it('should return the negation of student submission isSynced value', () => {
        component.exercise = exercise;
        component.studentSubmission = fileUploadSubmission;
        component.studentSubmission.isSynced = false;

        fixture.detectChanges();

        expect(component.hasUnsavedChanges()).to.equal(true);
    });
    /* this needs to be adapted so that the event of adding the file is tested
    it('should trigger upload event', () => {
        component.exercise = exercise;

        component.studentSubmission = fileUploadSubmission;

        fixture.detectChanges();
        fixture.whenStable().then(() => {
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

     */
});
