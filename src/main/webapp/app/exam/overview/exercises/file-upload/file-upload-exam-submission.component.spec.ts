import { ChangeDetectorRef } from '@angular/core';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { FileUploadExamSubmissionComponent } from 'app/exam/overview/exercises/file-upload/file-upload-exam-submission.component';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { createFileUploadSubmission } from 'test/helpers/mocks/service/mock-file-upload-submission.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/foundation/constants/input.constants';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { FileUploadSubmissionService } from 'app/fileupload/overview/file-upload-submission.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/overview/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { htmlForMarkdown } from 'app/foundation/util/markdown.conversion.util';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { addPublicFilePrefix } from 'app/app.constants';

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
            fixture.componentRef.setInput('exercise', mockExercise);
            fixture.componentRef.setInput('studentSubmission', mockSubmission);
        }
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                FileUploadExamSubmissionComponent,
                MockDirective(MarkdownDirective),
                TranslatePipeMock,
                MockComponent(ExamExerciseUpdateHighlighterComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                MockProvider(ChangeDetectorRef),
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(AlertService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadExamSubmissionComponent);
                comp = fixture.componentInstance;
                alertService = TestBed.inject(AlertService);
                fileUploadSubmissionService = TestBed.inject(FileUploadSubmissionService);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('With exercise', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should initialize', () => {
            fixture.detectChanges();
            expect(FileUploadExamSubmissionComponent).not.toBeNull();
        });

        it('should show static text in header', () => {
            fixture.componentRef.setInput('examTimeline', false);
            fixture.detectChanges();
            const el = fixture.debugElement.query(By.css('.exercise-title'));
            expect(el).not.toBeNull();
        });

        it('should show exercise max score if any', () => {
            const maxScore = 30;
            comp.exercise().maxPoints = maxScore;
            fixture.detectChanges();
            const el = fixture.debugElement.query(By.directive(TranslateDirective));
            expect(el).not.toBeNull();

            const directiveInstance = el.injector.get(TranslateDirective);
            expect(directiveInstance.jhiTranslate()).toBe('artemisApp.examParticipation.points');
            expect(directiveInstance.translateValues()).toEqual({ points: maxScore, bonusPoints: 0 });
        });

        it('should show exercise bonus score if any', () => {
            const maxScore = 40;
            comp.exercise().maxPoints = maxScore;
            const bonusPoints = 55;
            comp.exercise().bonusPoints = bonusPoints;
            fixture.detectChanges();
            const el = fixture.debugElement.query(By.directive(TranslateDirective));
            expect(el).not.toBeNull();

            const directiveInstance = el.injector.get(TranslateDirective);
            expect(directiveInstance.jhiTranslate()).toBe('artemisApp.examParticipation.bonus');
            expect(directiveInstance.translateValues()).toEqual({ points: maxScore, bonusPoints: bonusPoints });
        });

        it('should show problem statement if there is any', () => {
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent === mockExercise.problemStatement);
            expect(el).not.toBeNull();
        });
    });

    describe('upload controls', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });

        it('should provide a prominent selection area that uploads automatically', () => {
            expect(fixture.nativeElement.querySelector('[data-testid="file-upload-field"]')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('[data-testid="file-upload-submit"]')).toBeNull();
        });

        it('should automatically upload a valid selected file exactly once', () => {
            const pending = new Subject<HttpResponse<FileUploadSubmission>>();
            const update = vi.spyOn(fileUploadSubmissionService, 'update').mockReturnValue(pending);
            const file = new File(['image'], 'solution.PNG');
            comp.setFileSubmissionForExercise({ target: { files: [file] } } as unknown as Event);
            expect(update).toHaveBeenCalledOnce();
            comp.saveUploadedFile();
            expect(update).toHaveBeenCalledExactlyOnceWith(comp.studentSubmission(), mockExercise.id, file);
            expect(comp.studentSubmission().isSynced).toBe(false);
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('[data-testid="exam-file-upload-input"]').disabled).toBe(true);
            pending.next(new HttpResponse({ body: { filePath: 'new/solution.PNG' } }));
            pending.complete();
            fixture.detectChanges();
            expect(comp.studentSubmission().isSynced).toBe(true);
            expect(fixture.nativeElement.querySelector('[data-testid="file-upload-preview"]').getAttribute('src')).toBe(addPublicFilePrefix('new/solution.PNG'));
        });

        it('should keep an in-flight upload associated with its original submission', () => {
            const pending = new Subject<HttpResponse<FileUploadSubmission>>();
            vi.spyOn(fileUploadSubmissionService, 'update').mockReturnValue(pending);
            comp.setFileSubmissionForExercise({ target: { files: [new File(['image'], 'solution.png')] } } as unknown as Event);
            const originalSubmission = comp.studentSubmission();
            const otherSubmission = { isSynced: true } as FileUploadSubmission;
            fixture.componentRef.setInput('studentSubmission', otherSubmission);
            fixture.detectChanges();
            pending.next(new HttpResponse({ body: { filePath: 'original/solution.png' } }));
            pending.complete();
            expect(originalSubmission.filePath).toBe('original/solution.png');
            expect(otherSubmission.filePath).toBeUndefined();
            expect(TestBed.inject(ExamParticipationService).isSubmissionSaving(originalSubmission)).toBe(false);
        });

        it('should retain the selected file for retry after an upload error', () => {
            const update = vi.spyOn(fileUploadSubmissionService, 'update').mockReturnValue(throwError(() => new Error('offline')));
            const file = new File(['image'], 'solution.png');
            comp.setFileSubmissionForExercise({ target: { files: [file] } } as unknown as Event);
            fixture.detectChanges();
            expect(comp.submissionFile).toBe(file);
            expect(comp.studentSubmission().isSynced).toBe(false);
            expect(fixture.nativeElement.querySelector('[data-testid="file-upload-error"]')).not.toBeNull();
            update.mockReturnValue(of(new HttpResponse({ body: { filePath: 'new/solution.png' } })));
            (fixture.nativeElement.querySelector('[data-testid="file-upload-retry"]') as HTMLButtonElement).click();
            expect(update).toHaveBeenCalledTimes(2);
            expect(comp.studentSubmission().isSynced).toBe(true);
        });
    });

    describe('ngOnInit', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should call updateViewFromSubmission', () => {
            const updateViewStub = vi.spyOn(comp, 'updateViewFromSubmission');
            comp.ngOnInit();
            expect(updateViewStub).toHaveBeenCalledOnce();
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
            expect(comp.getExerciseId()).toEqual(mockExercise.id);
        });
    });

    describe('updateProblemStatement', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should update problem statement', () => {
            const newProblemStatement = 'new problem statement';
            comp.updateProblemStatement(TestBed.inject(ArtemisMarkdownService).safeHtmlForMarkdown(newProblemStatement));
            expect((comp.problemStatementHtml() as any).changingThisBreaksApplicationSecurity).toEqual(htmlForMarkdown(newProblemStatement));
        });
    });

    describe('updateSubmissionFromView', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            vi.restoreAllMocks();
        });
        it('should do nothing', () => {
            const submissionBefore = comp.getSubmission();
            comp.updateSubmissionFromView();
            expect(comp.getSubmission()).toBe(submissionBefore);
        });
    });

    describe('hasUnsavedChanges', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return true if isSynced false', () => {
            comp.studentSubmission().isSynced = false;
            expect(comp.hasUnsavedChanges()).toBe(true);
        });
        it('should return false if isSynced true', () => {
            comp.studentSubmission().isSynced = true;
            expect(comp.hasUnsavedChanges()).toBe(false);
        });
    });

    describe('updateViewFromSubmission', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            vi.restoreAllMocks();
        });
        it('should do nothing if isSynced is false', () => {
            comp.studentSubmission().isSynced = false;
            comp.submissionFile = new File([], 'file2');
            comp.updateViewFromSubmission();
            expect(comp.submissionFile).toBeDefined();
        });
        it('should set submitted filename and file extension', () => {
            comp.studentSubmission().isSynced = true;
            comp.submissionFile = new File([], 'file2');
            comp.updateViewFromSubmission();
            expect(comp.submittedFileName()).toBe('file1.png');
            expect(comp.submittedFileExtension()).toBe('png');
            expect(comp.submissionFile).toBeUndefined();
        });
    });

    describe('exam timeline', () => {
        beforeEach(() => {
            resetComponent();
            fixture.componentRef.setInput('examTimeline', true);
        });

        it('should show the submitted file with a download link although the submission has no filePathUrl', () => {
            const downloadSpy = vi.spyOn(comp, 'downloadFile').mockImplementation(() => {});
            expect(comp.studentSubmission().filePathUrl).toBeUndefined();

            fixture.detectChanges();

            const expectedUrl = addPublicFilePrefix('path/to/the/file/file1.png');
            expect(comp.submittedFileName()).toBe('file1.png');
            expect(comp.submittedFileUrl()).toBe(expectedUrl);
            // the input submission is not modified, the url is derived state of the component
            expect(comp.studentSubmission().filePathUrl).toBeUndefined();
            const downloadLink = fixture.nativeElement.querySelector('button[jhiTranslate="artemisApp.fileUploadSubmission.download"]') as HTMLElement;
            expect(downloadLink).not.toBeNull();
            downloadLink.click();
            expect(downloadSpy).toHaveBeenCalledWith(expectedUrl);
        });

        it('should show the not submitted hint instead of a file if the submission has no file', () => {
            fixture.componentRef.setInput('studentSubmission', {} as FileUploadSubmission);

            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('button[jhiTranslate="artemisApp.fileUploadSubmission.download"]')).toBeNull();
            expect(fixture.nativeElement.querySelector('h6[jhiTranslate="artemisApp.timeline.fileUploadNotSubmitted"]')).not.toBeNull();
        });
    });

    it('Too big file can not be submitted', async () => {
        // Ignore console errors
        console.error = vi.fn();
        resetComponent();
        fixture.detectChanges();
        await Promise.resolve();

        const submissionFile = new File([''], 'exampleSubmission.png');
        Object.defineProperty(submissionFile, 'size', { value: MAX_SUBMISSION_FILE_SIZE + 1, writable: false });
        const studentSubmission = createFileUploadSubmission();
        fixture.componentRef.setInput('studentSubmission', studentSubmission);
        const jhiErrorSpy = vi.spyOn(alertService, 'error');
        const event = { target: { files: [submissionFile] } } as unknown as Event;
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy).toHaveBeenCalledOnce();
        expect(comp.submissionFile).toBeUndefined();
        expect(comp.studentSubmission().filePath).toBeUndefined();

        // check if fileUploadInput is available
        const fileUploadInput = fixture.debugElement.query(By.css('[data-testid="exam-file-upload-input"]'));
        expect(fileUploadInput).not.toBeNull();
        expect(fileUploadInput.nativeElement.disabled).toBe(false);
        expect(fileUploadInput.nativeElement.value).toBe('');
        vi.restoreAllMocks();
    });

    it('Incorrect file type can not be submitted', async () => {
        // Ignore console errors
        console.error = vi.fn();
        resetComponent();
        fixture.detectChanges();
        await Promise.resolve();

        // Only png and pdf types are allowed
        const submissionFile = new File([''], 'exampleSubmission.jpg');
        const studentSubmission = createFileUploadSubmission();
        fixture.componentRef.setInput('studentSubmission', studentSubmission);
        const jhiErrorSpy = vi.spyOn(alertService, 'error');
        const event = { target: { files: [submissionFile] } } as unknown as Event;
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy).toHaveBeenCalledOnce();
        expect(comp.submissionFile).toBeUndefined();
        expect(comp.studentSubmission().filePath).toBeUndefined();

        // check if fileUploadInput is available
        const fileUploadInput = fixture.debugElement.query(By.css('[data-testid="exam-file-upload-input"]'));
        expect(fileUploadInput).not.toBeNull();
        expect(fileUploadInput.nativeElement.disabled).toBe(false);
        expect(fileUploadInput.nativeElement.value).toBe('');

        await Promise.resolve();
        fixture.destroy();
        vi.restoreAllMocks();
    });

    describe('saveUploadedFile', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            vi.restoreAllMocks();
        });
        it('should just return if submissionFile is undefined', () => {
            const updateStub = vi.spyOn(fileUploadSubmissionService, 'update');
            comp.saveUploadedFile();
            expect(updateStub).not.toHaveBeenCalled();
        });

        it('should save if submissionFile is defined', () => {
            const newFilePath = 'new/path/image.png';
            const updateStub = vi.spyOn(fileUploadSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: { id: 1, filePath: newFilePath } })));
            comp.submissionFile = new File([], 'name.png');
            expect(comp.studentSubmission().filePath).not.toEqual(newFilePath);
            comp.saveUploadedFile();
            expect(updateStub).toHaveBeenCalledOnce();
            expect(comp.studentSubmission().filePath).toEqual(newFilePath);
        });
    });

    it('should notify the sync-state version on every isSynced transition', () => {
        // `isSynced` is mutated in place, so under zoneless change detection the exam navigation sidebar and
        // exercise overview only re-evaluate their saved/unsaved icons if the service-wide version signal is
        // bumped. Without these notifications those indicators stay stale for file-upload exam exercises.
        resetComponent();
        fixture.detectChanges();
        const examParticipationService = TestBed.inject(ExamParticipationService);
        const notify = vi.spyOn(examParticipationService, 'notifySubmissionSyncStateChanged');

        const pending = new Subject<HttpResponse<FileUploadSubmission>>();
        vi.spyOn(fileUploadSubmissionService, 'update').mockReturnValue(pending);

        // Selecting a valid file marks the submission unsaved.
        const submissionFile = new File([''], 'exampleSubmission.png');
        comp.setFileSubmissionForExercise({ target: { files: [submissionFile] } } as unknown as Event);
        expect(comp.studentSubmission().isSynced).toBe(false);
        expect(notify).toHaveBeenCalledOnce();

        // A successful upload marks it saved again.
        pending.next(new HttpResponse({ body: { filePath: 'path/to/file.png' } }));
        pending.complete();
        expect(comp.studentSubmission().isSynced).toBe(true);
        expect(notify).toHaveBeenCalledTimes(2);
    });
});
