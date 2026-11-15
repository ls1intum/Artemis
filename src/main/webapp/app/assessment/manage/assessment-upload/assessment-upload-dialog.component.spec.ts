import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AssessmentUploadDialogComponent } from 'app/assessment/manage/assessment-upload/assessment-upload-dialog.component';
import { AssessmentUploadResult } from 'app/openapi/model/assessment-upload-result';
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
    let http: HttpTestingController;
    let alertError: ReturnType<typeof vi.spyOn>;
    let alertSuccess: ReturnType<typeof vi.spyOn>;

    const zipFile = new File(['dummy'], 'assessments.zip', { type: 'application/zip' });
    const uploadUrl = '/api/assessment/exercises/7/manual-assessments';
    const templateUrl = '/api/assessment/exercises/7/manual-assessments/template';

    const expectUpload = () => {
        const request = http.expectOne(uploadUrl);
        expect(request.request.method).toBe('POST');
        expect((request.request.body as FormData).get('file')).toBe(zipFile);
        return request;
    };

    beforeEach(async () => {
        vi.mocked(downloadZipFileFromResponse).mockClear();

        await TestBed.configureTestingModule({
            imports: [AssessmentUploadDialogComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting(), MockProvider(AlertService)],
        }).compileComponents();

        http = TestBed.inject(HttpTestingController);
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
        const uploadedSpy = vi.fn();
        component.uploaded.subscribe(uploadedSpy);

        selectFileAndConfirmOverwrite(component, zipFile);
        component.upload();
        expectUpload().flush(result);

        expect(component['errors']()).toHaveLength(0);
        expect(alertSuccess).toHaveBeenCalledWith('artemisApp.assessmentUpload.success', { count: 2 });
        expect(uploadedSpy).toHaveBeenCalledWith(2);
        expect(component.visible()).toBe(false);
    });

    it('should show the validation errors, emit no completion event, and stay open when the upload is rejected', () => {
        const result: AssessmentUploadResult = { numberOfCreatedAssessments: 0, errors: [{ identifier: '1-a', type: 'MISSING_TEXT_FILE' }] };
        const uploadedSpy = vi.fn();
        component.uploaded.subscribe(uploadedSpy);

        selectFileAndConfirmOverwrite(component, zipFile);
        component.upload();
        expectUpload().flush(result);

        expect(component['errors']()).toEqual(result.errors);
        expect(alertSuccess).not.toHaveBeenCalled();
        expect(uploadedSpy).not.toHaveBeenCalled();
        expect(component.visible()).toBe(true);
    });

    it('should leave the dialog ready for another attempt after an HTTP error', () => {
        selectFileAndConfirmOverwrite(component, zipFile);

        component.upload();
        expect(component['isUploading']()).toBe(true);

        expectUpload().flush(null, { status: 400, statusText: 'Bad Request' });

        expect(component['isUploading']()).toBe(false);
        expect(component['selectedFile']()).toBe(zipFile);
        expect(component['errors']()).toEqual([]);
        expect(component.visible()).toBe(true);
    });

    it.each([
        ['assessmentNotPossibleExamRunning', 'artemisApp.assessment.error.assessmentNotPossibleExamRunning'],
        ['assessmentNotPossibleTestsPending', 'artemisApp.assessment.error.assessmentNotPossibleTestsPending'],
    ])('should report %s, which the server marks skipAlert so nothing else would', (errorKey, expectedTranslationKey) => {
        // The exam guard sends the date as an ISO string and suppresses the global alert so it can be localized here. Without its own handling the dialog would just stop
        // the spinner and leave the instructor without a reason or the date assessment becomes possible.
        const date = '2026-10-20T10:00:00Z';
        selectFileAndConfirmOverwrite(component, zipFile);

        component.upload();
        expectUpload().flush({ errorKey, params: { date }, skipAlert: true }, { status: 403, statusText: 'Forbidden' });

        expect(component['isUploading']()).toBe(false);
        expect(alertError).toHaveBeenCalledOnce();
        const [translationKey, params] = alertError.mock.calls[0];
        expect(translationKey).toContain(expectedTranslationKey.split('.').pop());
        expect(params.date).toBeTruthy();
        expect(component.visible()).toBe(true);
    });

    it('should prevent closing and starting another request while uploading', () => {
        selectFileAndConfirmOverwrite(component, zipFile);

        component.upload();
        component.close();
        component.upload();

        expect(component.visible()).toBe(true);
        expectUpload();
    });

    it('should not upload before the instructor confirms that existing assessments are overwritten', () => {
        component.onFileInputChange(fileInputEvent(zipFile));

        component.upload();

        http.expectNone(uploadUrl);
        expect(component.visible()).toBe(true);

        component['overwriteConfirmed'].set(true);
        component.upload();

        expectUpload();
    });

    it('should download the template for the bound exercise and trigger the file download', () => {
        component.downloadTemplate();
        const request = http.expectOne(templateUrl);
        expect(request.request.method).toBe('GET');
        expect(request.request.responseType).toBe('blob');
        expect(component['isDownloadingTemplate']()).toBe(true);

        const template = new Blob(['template']);
        request.flush(template, { headers: { filename: 'assessment-template-7.zip' } });

        expect(downloadZipFileFromResponse).toHaveBeenCalledOnce();
        const response = vi.mocked(downloadZipFileFromResponse).mock.calls[0][0];
        expect(response.body).toEqual(template);
        expect(response.headers.get('filename')).toBe('assessment-template-7.zip');
        expect(component['isDownloadingTemplate']()).toBe(false);
    });

    it('should reset the template download state and not download on an HTTP error', () => {
        component.downloadTemplate();
        expect(component['isDownloadingTemplate']()).toBe(true);

        http.expectOne(templateUrl).flush(null, { status: 500, statusText: 'Server Error' });

        expect(component['isDownloadingTemplate']()).toBe(false);
        expect(downloadZipFileFromResponse).not.toHaveBeenCalled();
    });

    it('should not start a second template download while one is in flight', () => {
        component.downloadTemplate();
        component.downloadTemplate();

        http.expectOne(templateUrl);
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
