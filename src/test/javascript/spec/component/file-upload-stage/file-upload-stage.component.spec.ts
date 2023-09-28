import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { FileUploadStageComponent } from 'app/exercises/file-upload/stage/file-upload-stage.component';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';
import { createFileUploadSubmission } from '../../helpers/mocks/service/mock-file-upload-submission.service';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';
import { routes } from 'app/exercises/file-upload/participate/file-upload-participation.route';
import { RouterTestingModule } from '@angular/router/testing';
import { FileService } from 'app/shared/http/file.service';
import { FileDetails } from 'app/entities/file-details.model';

describe('FileUploadStageComponent', () => {
    let comp: FileUploadStageComponent;
    let fixture: ComponentFixture<FileUploadStageComponent>;
    let debugElement: DebugElement;
    let router: Router;
    let alertService: AlertService;

    const allowedFileExtensions: string[] = ['png', 'pdf'];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), RouterTestingModule.withRoutes([routes[0]])],
            declarations: [FileUploadStageComponent, MockPipe(ArtemisTimeAgoPipe), MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: AlertService, useClass: MockAlertService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadStageComponent);
                comp = fixture.componentInstance;
                comp.allowsUploads = true;
                comp.allowedFileExtensions = allowedFileExtensions;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                fixture.ngZone!.run(() => {
                    router.initialNavigation();
                });

                alertService = TestBed.inject(AlertService);
            });
    });

    afterEach(fakeAsync(() => {
        tick();
        fixture.destroy();
        flush();
    }));

    it('Incorrect file type can not be submitted', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();

        fixture.detectChanges();
        tick();

        // Only png and pdf types are allowed
        const submissionFile = new File([''], 'exampleSubmission.jpg');
        comp.submission = createFileUploadSubmission();
        const jhiErrorSpy = jest.spyOn(alertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.stageFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy).toHaveBeenCalledOnce();
        expect(comp.stagedFiles.length === 0).toBeTrue();
        expect(comp.submission!.filePaths).toBeUndefined();

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).toBeDefined();
        expect(fileUploadInput.nativeElement.disabled).toBeFalse();
        expect(fileUploadInput.nativeElement.value).toBe('');

        tick();
        fixture.destroy();
        flush();
    }));

    it('Too big single file can not be submitted', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();

        fixture.detectChanges();
        tick();

        const submissionFile = new File([''], 'exampleSubmission.png');
        Object.defineProperty(submissionFile, 'size', { value: MAX_SUBMISSION_FILE_SIZE + 1, writable: false });

        comp.submission = createFileUploadSubmission();
        const jhiErrorSpy = jest.spyOn(alertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.stageFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy).toHaveBeenCalledOnce();
        expect(comp.stagedFiles).toHaveLength(0);
        expect(comp.submission!.filePaths).toBeUndefined();

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).toBeDefined();
        expect(fileUploadInput.nativeElement.disabled).toBeFalse();
        expect(fileUploadInput.nativeElement.value).toBe('');
    }));

    it('should download file', () => {
        const fileService = TestBed.inject(FileService);
        const fileServiceStub = jest.spyOn(fileService, 'downloadFile').mockImplementation();

        comp.downloadFile('');

        expect(fileServiceStub).toHaveBeenCalledOnce();
    });

    it('stagedFilesChanged gets triggered', fakeAsync(() => {
        fixture.detectChanges();
        tick();

        const stagedFilesChangedStub = jest.spyOn(comp.stagedFilesChanged, 'emit').mockImplementation();

        const submissionFile = new File([''], 'exampleSubmission.png');
        const event = { target: { files: [submissionFile] } };
        comp.stageFileSubmissionForExercise(event);

        expect(stagedFilesChangedStub).toHaveBeenCalledOnce();
    }));

    it('prefix files with index', fakeAsync(() => {
        fixture.detectChanges();
        tick();

        const stagedFilesChangedStub = jest.spyOn(comp.stagedFilesChanged, 'emit').mockImplementation();
        const numFiles: number = 8;

        for (let i = 0; i < numFiles; i++) {
            const submissionFile = new File([''], `exampleSubmission${i}.png`);
            const event = { target: { files: [submissionFile] } };
            comp.stageFileSubmissionForExercise(event);
        }

        expect(stagedFilesChangedStub).toHaveBeenCalledTimes(numFiles);

        const mockCalls = stagedFilesChangedStub.mock.calls;
        const lastMockCallArgs = mockCalls[mockCalls.length - 1];
        const prefixedFiles = lastMockCallArgs![0];

        expect(prefixedFiles).toBeDefined();
        expect(prefixedFiles).toHaveLength(numFiles);

        for (let i = 0; i < numFiles; i++) {
            const prefixedFile = prefixedFiles![i];
            expect(prefixedFile.name).toBe(`${i}_exampleSubmission${i}.png`);
        }
    }));

    describe('FileDetails name', () => {
        it('should get file name', () => {
            expect(FileDetails.getFileDetailsFromPath('this/is/a/filepath/4_file.png').name).toBe('file.png');
        });

        it('should get N/A if filepath is empty', () => {
            expect(FileDetails.getFileDetailsFromPath('').name).toBe('N/A');
        });
    });

    describe('FileDetails extension', () => {
        it('should get file extension', () => {
            expect(FileDetails.getFileDetailsFromPath('this/is/a/filepath/file.png').extension).toBe('png');
        });

        it('should get N/A if filepath is empty', () => {
            expect(FileDetails.getFileDetailsFromPath('').extension).toBe('N/A');
        });
    });
});
