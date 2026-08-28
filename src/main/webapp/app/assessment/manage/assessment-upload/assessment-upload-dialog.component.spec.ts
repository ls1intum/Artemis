import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, of } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AssessmentUploadDialogComponent } from 'app/assessment/manage/assessment-upload/assessment-upload-dialog.component';
import { AssessmentUploadResult, AssessmentUploadService } from 'app/assessment/manage/services/assessment-upload.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { downloadZipFileFromResponse } from 'app/foundation/util/download.util';

vi.mock('app/foundation/util/download.util', () => ({ downloadZipFileFromResponse: vi.fn() }));

function fileInputEvent(file: File): Event {
    return { target: { files: [file], value: 'x' } } as unknown as Event;
}

/** Brings the dialog into the state an upload requires: a selected file plus the explicit overwrite confirmation. */
function selectFileAndConfirmOverwrite(component: AssessmentUploadDialogComponent, file: File): void {
    component.onFileInputChange(fileInputEvent(file));
    component['overwriteConfirmed'].set(true);
}

function dropEvent(file: File): DragEvent {
    return { preventDefault: () => {}, stopPropagation: () => {}, dataTransfer: { files: [file] } } as unknown as DragEvent;
}

describe('AssessmentUploadDialogComponent', () => {
    let component: AssessmentUploadDialogComponent;
    let fixture: ComponentFixture<AssessmentUploadDialogComponent>;
    let uploadSpy: ReturnType<typeof vi.fn>;
    let downloadSpy: ReturnType<typeof vi.fn>;
    let alertError: ReturnType<typeof vi.spyOn>;
    let alertSuccess: ReturnType<typeof vi.spyOn>;

    const zipFile = new File(['dummy'], 'assessments.zip', { type: 'application/zip' });

    beforeEach(async () => {
        uploadSpy = vi.fn();
        downloadSpy = vi.fn();
        vi.mocked(downloadZipFileFromResponse).mockClear();

        await TestBed.configureTestingModule({
            imports: [AssessmentUploadDialogComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AssessmentUploadService, useValue: { uploadManualAssessments: uploadSpy, downloadTemplate: downloadSpy } },
                MockProvider(AlertService),
            ],
        }).compileComponents();

        const alertService = TestBed.inject(AlertService);
        alertError = vi.spyOn(alertService, 'error');
        alertSuccess = vi.spyOn(alertService, 'success');

        fixture = TestBed.createComponent(AssessmentUploadDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exerciseId', 7);
        // Open the dialog and render the real tum-ui template: this JIT-compiles and validates every tum-ui binding.
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
    });

    it('should render the opened dialog', () => {
        expect(component).toBeTruthy();
        expect(component.visible()).toBe(true);
    });

    it('should reject a non-zip file and not select it', () => {
        component.onFileInputChange(fileInputEvent(new File(['x'], 'grades.csv', { type: 'text/csv' })));

        expect(alertError).toHaveBeenCalledWith('artemisApp.assessmentUpload.error.fileTypeNotSupported');
        expect(component['selectedFile']()).toBeUndefined();
    });

    it('should accept a dropped zip file', () => {
        component.onDrop(dropEvent(zipFile));

        expect(component['selectedFile']()).toBe(zipFile);
        expect(alertError).not.toHaveBeenCalled();
    });

    it('should reset the file input after selecting a file', () => {
        const event = fileInputEvent(zipFile);

        component.onFileInputChange(event);

        expect((event.target as HTMLInputElement).value).toBe('');
    });

    it('should update the drop-zone state and suppress the browser drag behavior', () => {
        const event = { preventDefault: vi.fn(), stopPropagation: vi.fn() } as unknown as DragEvent;

        component.onDragOver(event);

        expect(event.preventDefault).toHaveBeenCalledOnce();
        expect(event.stopPropagation).toHaveBeenCalledOnce();
        expect(component['isDragOver']()).toBe(true);

        component.onDragLeave(event);

        expect(event.preventDefault).toHaveBeenCalledTimes(2);
        expect(event.stopPropagation).toHaveBeenCalledTimes(2);
        expect(component['isDragOver']()).toBe(false);
    });

    it('should upload for the bound exercise id, show a success alert, emit the completion event, and close the dialog', () => {
        const result: AssessmentUploadResult = { numberOfCreatedAssessments: 2, createdStudentIdentifiers: ['1-a', '2-b'] };
        uploadSpy.mockReturnValue(of(new HttpResponse({ body: result })));
        const uploadedSpy = vi.fn();
        component.uploaded.subscribe(uploadedSpy);

        selectFileAndConfirmOverwrite(component, zipFile);
        component.upload();

        expect(uploadSpy).toHaveBeenCalledWith(7, zipFile);
        expect(component['errors']()).toHaveLength(0);
        expect(alertSuccess).toHaveBeenCalledWith('artemisApp.assessmentUpload.success', { count: 2 });
        expect(uploadedSpy).toHaveBeenCalledWith(2);
        expect(component.visible()).toBe(false);
    });

    it('should show the validation errors, emit no completion event, and stay open when the upload is rejected', () => {
        const result: AssessmentUploadResult = { numberOfCreatedAssessments: 0, errors: [{ identifier: '1-a', type: 'MISSING_TEXT_FILE' }] };
        uploadSpy.mockReturnValue(of(new HttpResponse({ body: result })));
        const uploadedSpy = vi.fn();
        component.uploaded.subscribe(uploadedSpy);

        selectFileAndConfirmOverwrite(component, zipFile);
        component.upload();

        expect(component['errors']()).toEqual(result.errors);
        expect(alertSuccess).not.toHaveBeenCalled();
        expect(uploadedSpy).not.toHaveBeenCalled();
        expect(component.visible()).toBe(true);
    });

    it('should leave the dialog ready for another attempt after an HTTP error', () => {
        const upload = new Subject<HttpResponse<AssessmentUploadResult>>();
        uploadSpy.mockReturnValue(upload);
        selectFileAndConfirmOverwrite(component, zipFile);

        component.upload();
        expect(component['isUploading']()).toBe(true);

        upload.error(new HttpErrorResponse({ status: 400 }));

        expect(component['isUploading']()).toBe(false);
        expect(component['selectedFile']()).toBe(zipFile);
        expect(component['errors']()).toEqual([]);
        expect(component.visible()).toBe(true);
    });

    it('should prevent closing and starting another request while uploading', () => {
        const upload = new Subject<HttpResponse<AssessmentUploadResult>>();
        uploadSpy.mockReturnValue(upload);
        selectFileAndConfirmOverwrite(component, zipFile);

        component.upload();
        component.close();
        component.upload();

        expect(component.visible()).toBe(true);
        expect(uploadSpy).toHaveBeenCalledOnce();
    });

    it('should not upload before the instructor confirms that existing assessments are overwritten', () => {
        uploadSpy.mockReturnValue(of(new HttpResponse({ body: { numberOfCreatedAssessments: 1 } as AssessmentUploadResult })));
        component.onFileInputChange(fileInputEvent(zipFile));

        component.upload();

        expect(uploadSpy).not.toHaveBeenCalled();
        expect(component.visible()).toBe(true);

        component['overwriteConfirmed'].set(true);
        component.upload();

        expect(uploadSpy).toHaveBeenCalledWith(7, zipFile);
    });

    it('should download the template for the bound exercise and trigger the file download', () => {
        const response = new HttpResponse<Blob>({ body: new Blob(['template']) });
        downloadSpy.mockReturnValue(of(response));

        component.downloadTemplate();

        expect(downloadSpy).toHaveBeenCalledWith(7);
        expect(downloadZipFileFromResponse).toHaveBeenCalledWith(response);
        expect(component['isDownloadingTemplate']()).toBe(false);
    });

    it('should reset the template download state and not download on an HTTP error', () => {
        const download = new Subject<HttpResponse<Blob>>();
        downloadSpy.mockReturnValue(download);

        component.downloadTemplate();
        expect(component['isDownloadingTemplate']()).toBe(true);

        download.error(new HttpErrorResponse({ status: 500 }));

        expect(component['isDownloadingTemplate']()).toBe(false);
        expect(downloadZipFileFromResponse).not.toHaveBeenCalled();
    });

    it('should not start a second template download while one is in flight', () => {
        downloadSpy.mockReturnValue(new Subject<HttpResponse<Blob>>());

        component.downloadTemplate();
        component.downloadTemplate();

        expect(downloadSpy).toHaveBeenCalledOnce();
    });

    it('should clear the selected file, previous errors and the overwrite confirmation when reset', () => {
        selectFileAndConfirmOverwrite(component, zipFile);
        component['errors'].set([{ identifier: '1-a', type: 'MISSING_TEXT_FILE' }]);

        component.resetState();

        expect(component['selectedFile']()).toBeUndefined();
        expect(component['errors']()).toEqual([]);
        // The confirmation must not carry over to the next open, so every upload is confirmed again.
        expect(component['overwriteConfirmed']()).toBe(false);
        expect(component['isUploading']()).toBe(false);
    });

    it('should hide the dialog on cancel', () => {
        component.close();
        expect(component.visible()).toBe(false);
    });
});
