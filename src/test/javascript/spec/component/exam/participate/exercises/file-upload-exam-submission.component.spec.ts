import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { By, SafeHtml } from '@angular/platform-browser';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { FullscreenComponent } from 'app/shared/fullscreen/fullscreen.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
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
import { AlertService } from 'app/core/util/alert.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';

describe('FileUploadExamSubmissionComponent', () => {
    let fixture: ComponentFixture<FileUploadExamSubmissionComponent>;
    let comp: FileUploadExamSubmissionComponent;
    let alertService: AlertService;
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
                alertService = TestBed.inject(AlertService);
                fileUploadSubmissionService = fixture.debugElement.injector.get(FileUploadSubmissionService);
            });
    });

    describe('With exercise', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should initialize', () => {
            fixture.detectChanges();
            expect(FileUploadExamSubmissionComponent).not.toBeNull();
        });

        it('should show exercise title if any', () => {
            comp.exercise.title = 'Test Title';
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent === comp.exercise.title);
            expect(el).not.toBeNull();
        });

        it('should show exercise max score if any', () => {
            const maxScore = 30;
            comp.exercise.maxPoints = maxScore;
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent.includes(`[${maxScore} artemisApp.examParticipation.points]`));
            expect(el).not.toBeNull();
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
            expect(el).not.toBeNull();
        });
        it('should show problem statement if there is any', () => {
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent === mockExercise.problemStatement);
            expect(el).not.toBeNull();
        });
    });

    describe('ngOnInit', () => {
        beforeEach(() => {
            resetComponent();
        });
        afterEach(() => {
            jest.restoreAllMocks();
        });
        it('should call updateViewFromSubmission', () => {
            const updateViewStub = jest.spyOn(comp, 'updateViewFromSubmission');
            comp.ngOnInit();
            expect(updateViewStub).toHaveBeenCalled();
        });
    });

    describe('getSubmission', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return student submission', () => {
            expect(comp.getSubmission()).toEqual(mockSubmission);
        });
    });

    describe('getExercise', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return exercise', () => {
            expect(comp.getExercise()).toEqual(mockExercise);
        });
    });

    describe('updateProblemStatement', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should update problem statement', () => {
            const newProblemStatement = 'new problem statement';
            comp.updateProblemStatement(newProblemStatement);
            expect(comp.getExercise().problemStatement).toEqual(newProblemStatement);
        });
    });

    describe('updateSubmissionFromView', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            jest.restoreAllMocks();
        });
        it('should do nothing', () => {
            const jsonOfComponent = stringifyCircular(comp);
            comp.updateSubmissionFromView();
            expect(stringifyCircular(comp)).toEqual(jsonOfComponent);
        });
    });

    describe('hasUnsavedChanges', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return true if isSynced false', () => {
            comp.studentSubmission.isSynced = false;
            expect(comp.hasUnsavedChanges()).toBeTrue();
        });
        it('should return false if isSynced true', () => {
            comp.studentSubmission.isSynced = true;
            expect(comp.hasUnsavedChanges()).toBeFalse();
        });
    });

    describe('updateViewFromSubmission', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            jest.restoreAllMocks();
        });
        it('should do nothing if isSynced is false', () => {
            comp.studentSubmission.isSynced = false;
            comp.submissionFile = new File([], 'file2');
            comp.updateViewFromSubmission();
            expect(comp.submissionFile).toBeDefined();
        });
        it('should set submitted filename and file extension', () => {
            comp.studentSubmission.isSynced = true;
            comp.submissionFile = new File([], 'file2');
            comp.updateViewFromSubmission();
            expect(comp.submittedFileName).toBe('file1.png');
            expect(comp.submittedFileExtension).toBe('png');
            expect(comp.submissionFile).toBeUndefined();
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
        const jhiErrorSpy = jest.spyOn(alertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy).toHaveBeenCalledOnce();
        expect(comp.submissionFile).toBeUndefined();
        expect(comp.studentSubmission!.filePath).toBeUndefined();

        // check if fileUploadInput is available
        const fileUploadInput = fixture.debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).not.toBeNull();
        expect(fileUploadInput.nativeElement.disabled).toBeFalse();
        expect(fileUploadInput.nativeElement.value).toBe('');
        jest.restoreAllMocks();
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
        const jhiErrorSpy = jest.spyOn(alertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy).toHaveBeenCalledOnce();
        expect(comp.submissionFile).toBeUndefined();
        expect(comp.studentSubmission!.filePath).toBeUndefined();

        // check if fileUploadInput is available
        const fileUploadInput = fixture.debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).not.toBeNull();
        expect(fileUploadInput.nativeElement.disabled).toBeFalse();
        expect(fileUploadInput.nativeElement.value).toBe('');

        tick();
        fixture.destroy();
        flush();
        jest.restoreAllMocks();
    }));

    describe('saveUploadedFile', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            jest.restoreAllMocks();
        });
        it('should just return if submissionFile is undefined', () => {
            const updateStub = jest.spyOn(fileUploadSubmissionService, 'update');
            comp.saveUploadedFile();
            expect(updateStub).toHaveBeenCalledTimes(0);
        });

        it('should save if submissionFile is defined', () => {
            const newFilePath = 'new/path/image.png';
            const updateStub = jest.spyOn(fileUploadSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: { id: 1, filePath: newFilePath } })));
            comp.submissionFile = new File([], 'name.png');
            expect(comp.studentSubmission.filePath).not.toEqual(newFilePath);
            comp.saveUploadedFile();
            expect(updateStub).toHaveBeenCalled();
            expect(comp.studentSubmission.filePath).toEqual(newFilePath);
        });
    });
});
