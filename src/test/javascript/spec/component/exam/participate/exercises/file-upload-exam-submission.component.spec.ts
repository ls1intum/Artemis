import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { By, SafeHtml } from '@angular/platform-browser';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { FullscreenComponent } from 'app/shared/fullscreen/fullscreen.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import * as chai from 'chai';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockTranslateService, TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../../test.module';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadExamSubmissionComponent } from 'app/exam/participate/exercises/file-upload/file-upload-exam-submission.component';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { stringifyCircular } from 'app/shared/util/utils';
import { createFileUploadSubmission } from '../../../../helpers/mocks/service/mock-file-upload-submission.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';
import { JhiAlertService } from 'ng-jhipster';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('FileUploadExamSubmissionComponent', () => {
    let fixture: ComponentFixture<FileUploadExamSubmissionComponent>;
    let comp: FileUploadExamSubmissionComponent;
    let jhiAlertService: JhiAlertService;
    let mockSubmission: FileUploadSubmission;
    let mockExercise: FileUploadExercise;
    let fileUploadSubmissionService: FileUploadSubmissionService;

    const resetComponent = () => {
        if (comp) {
            mockSubmission = { filePath: 'path/to/the/file/file1.png' } as FileUploadSubmission;
            const course = new Course();
            course.isAtLeastInstructor = true;
            const exerciseGroup = { id: 4 } as ExerciseGroup;
            mockExercise = new FileUploadExercise(undefined, exerciseGroup);
            mockExercise.filePattern = 'png,pdf';
            mockExercise.problemStatement = 'Test Problem Statement';
            comp.exercise = mockExercise;
            comp.studentSubmission = mockSubmission;
        }
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                FileUploadExamSubmissionComponent,
                FullscreenComponent,
                ResizeableContainerComponent,
                TranslatePipeMock,
                MockPipe(HtmlForMarkdownPipe, (markdown) => markdown as SafeHtml),
                MockDirective(NgbTooltip),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(ExamExerciseUpdateHighlighterComponent),
            ],
            providers: [MockProvider(ChangeDetectorRef), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadExamSubmissionComponent);
                comp = fixture.componentInstance;
                jhiAlertService = TestBed.inject(JhiAlertService);
                fileUploadSubmissionService = fixture.debugElement.injector.get(FileUploadSubmissionService);
            });
    });

    describe('With exercise', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should initialize', () => {
            fixture.detectChanges();
            expect(FileUploadExamSubmissionComponent).to.be.ok;
        });

        it('should show exercise title if any', () => {
            comp.exercise.title = 'Test Title';
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent === comp.exercise.title);
            expect(el).to.exist;
        });

        it('should show exercise max score if any', () => {
            const maxScore = 30;
            comp.exercise.maxPoints = maxScore;
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent.includes(`[${maxScore} artemisApp.examParticipation.points]`));
            expect(el).to.exist;
        });

        it('should show exercise bonus score if any', () => {
            const maxScore = 40;
            comp.exercise.maxPoints = maxScore;
            const bonusPoints = 55;
            comp.exercise.bonusPoints = bonusPoints;
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) =>
                de.nativeElement.textContent.includes(`[${maxScore} artemisApp.examParticipation.points, ${bonusPoints} artemisApp.examParticipation.bonus]`),
            );
            expect(el).to.exist;
        });
        it('should show problem statement if there is any', () => {
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent === mockExercise.problemStatement);
            expect(el).to.exist;
        });
    });

    describe('ngOnInit', () => {
        beforeEach(() => {
            resetComponent();
        });
        afterEach(() => {
            sinon.restore();
        });
        it('should call updateViewFromSubmission', () => {
            const updateViewStub = sinon.stub(comp, 'updateViewFromSubmission');
            comp.ngOnInit();
            expect(updateViewStub).to.have.been.called;
        });
    });

    describe('getSubmission', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return student submission', () => {
            expect(comp.getSubmission()).to.deep.equal(mockSubmission);
        });
    });

    describe('getExercise', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return exercise', () => {
            expect(comp.getExercise()).to.deep.equal(mockExercise);
        });
    });

    it('should update problem statement', () => {
        resetComponent();
        const newProblemStatement = 'new problem statement';
        comp.updateProblemStatement(newProblemStatement);
        expect(comp.getExercise().problemStatement).to.equal(newProblemStatement);
    });

    describe('updateSubmissionFromView', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            sinon.restore();
        });
        it('should do nothing', () => {
            const jsonOfComponent = stringifyCircular(comp);
            comp.updateSubmissionFromView();
            expect(stringifyCircular(comp)).to.deep.equal(jsonOfComponent);
        });
    });

    describe('hasUnsavedChanges', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return true if isSynced false', () => {
            comp.studentSubmission.isSynced = false;
            expect(comp.hasUnsavedChanges()).to.equal(true);
        });
        it('should return false if isSynced true', () => {
            comp.studentSubmission.isSynced = true;
            expect(comp.hasUnsavedChanges()).to.equal(false);
        });
    });

    describe('updateViewFromSubmission', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            sinon.restore();
        });
        it('should do nothing if isSynced is false', () => {
            comp.studentSubmission.isSynced = false;
            comp.submissionFile = new File([], 'file2');
            comp.updateViewFromSubmission();
            expect(comp.submissionFile).to.not.equal(undefined);
        });
        it('should set submitted filename and file extension', () => {
            comp.studentSubmission.isSynced = true;
            comp.submissionFile = new File([], 'file2');
            comp.updateViewFromSubmission();
            expect(comp.submittedFileName).to.equal('file1.png');
            expect(comp.submittedFileExtension).to.equal('png');
            expect(comp.submissionFile).to.equal(undefined);
        });
    });

    it('Too big file can not be submitted', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();
        resetComponent();
        fixture.detectChanges();
        tick();

        const submissionFile = new File([''], 'exampleSubmission.png');
        Object.defineProperty(submissionFile, 'size', { value: MAX_SUBMISSION_FILE_SIZE + 1, writable: false });
        comp.studentSubmission = createFileUploadSubmission();
        const jhiErrorSpy = sinon.spy(jhiAlertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy.callCount).to.be.equal(1);
        expect(comp.submissionFile).to.be.undefined;
        expect(comp.studentSubmission!.filePath).to.be.undefined;

        // check if fileUploadInput is available
        const fileUploadInput = fixture.debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).to.exist;
        expect(fileUploadInput.nativeElement.disabled).to.be.false;
        expect(fileUploadInput.nativeElement.value).to.be.equal('');
        sinon.restore();
    }));

    it('Incorrect file type can not be submitted', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();
        resetComponent();
        fixture.detectChanges();
        tick();

        // Only png and pdf types are allowed
        const submissionFile = new File([''], 'exampleSubmission.jpg');
        comp.studentSubmission = createFileUploadSubmission();
        const jhiErrorSpy = sinon.spy(jhiAlertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy.callCount).to.be.equal(1);
        expect(comp.submissionFile).to.be.undefined;
        expect(comp.studentSubmission!.filePath).to.be.undefined;

        // check if fileUploadInput is available
        const fileUploadInput = fixture.debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).to.exist;
        expect(fileUploadInput.nativeElement.disabled).to.be.false;
        expect(fileUploadInput.nativeElement.value).to.be.equal('');

        tick();
        fixture.destroy();
        flush();
        sinon.restore();
    }));

    describe('saveUploadedFile', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            sinon.restore();
        });
        it('should just return if submissionFile is undefined', () => {
            const updateStub = sinon.stub(fileUploadSubmissionService, 'update');
            comp.saveUploadedFile();
            expect(updateStub).to.not.have.been.called;
        });

        it('should save if submissionFile is defined', () => {
            const newFilePath = 'new/path/image.png';
            const updateStub = sinon.stub(fileUploadSubmissionService, 'update').returns(of(new HttpResponse({ body: { id: 1, filePath: newFilePath } })));
            comp.submissionFile = new File([], 'name.png');
            expect(comp.studentSubmission.filePath).to.not.equal(newFilePath);
            comp.saveUploadedFile();
            expect(updateStub).to.have.been.called;
            expect(comp.studentSubmission.filePath).to.deep.equal(newFilePath);
        });
    });
});
