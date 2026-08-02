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

function fileInputEvent(file: File): Event {
    return { target: { files: [file], value: 'x' } } as unknown as Event;
}

function dropEvent(file: File): DragEvent {
    return { preventDefault: () => {}, stopPropagation: () => {}, dataTransfer: { files: [file] } } as unknown as DragEvent;
}

describe('AssessmentUploadDialogComponent', () => {
    let component: AssessmentUploadDialogComponent;
    let fixture: ComponentFixture<AssessmentUploadDialogComponent>;
    let uploadSpy: ReturnType<typeof vi.fn>;
    let alertError: ReturnType<typeof vi.spyOn>;
    let alertSuccess: ReturnType<typeof vi.spyOn>;

    const zipFile = new File(['dummy'], 'assessments.zip', { type: 'application/zip' });

    beforeEach(async () => {
        uploadSpy = vi.fn();

        await TestBed.configureTestingModule({
            imports: [AssessmentUploadDialogComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AssessmentUploadService, useValue: { uploadManualAssessments: uploadSpy } },
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

    it('should upload for the bound exercise id, show a success alert, and close the dialog', () => {
        const result: AssessmentUploadResult = { numberOfCreatedAssessments: 2, createdStudentIdentifiers: ['1-a', '2-b'] };
        uploadSpy.mockReturnValue(of(new HttpResponse({ body: result })));

        component.onFileInputChange(fileInputEvent(zipFile));
        component.upload();

        expect(uploadSpy).toHaveBeenCalledWith(7, zipFile);
        expect(component['errors']()).toHaveLength(0);
        expect(alertSuccess).toHaveBeenCalledWith('artemisApp.assessmentUpload.success', { count: 2 });
        expect(component.visible()).toBe(false);
    });

    it('should show the validation errors and no success when the upload is rejected', () => {
        const result: AssessmentUploadResult = { numberOfCreatedAssessments: 0, errors: [{ identifier: '1-a', type: 'MISSING_TEXT_FILE' }] };
        uploadSpy.mockReturnValue(of(new HttpResponse({ body: result })));

        component.onFileInputChange(fileInputEvent(zipFile));
        component.upload();

        expect(component['errors']()).toEqual(result.errors);
        expect(alertSuccess).not.toHaveBeenCalled();
        expect(component.visible()).toBe(true);
    });

    it('should leave the dialog ready for another attempt after an HTTP error', () => {
        const upload = new Subject<HttpResponse<AssessmentUploadResult>>();
        uploadSpy.mockReturnValue(upload);
        component.onFileInputChange(fileInputEvent(zipFile));

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
        component.onFileInputChange(fileInputEvent(zipFile));

        component.upload();
        component.close();
        component.upload();

        expect(component.visible()).toBe(true);
        expect(uploadSpy).toHaveBeenCalledOnce();
    });

    it('should clear the selected file and previous errors when reset', () => {
        component.onFileInputChange(fileInputEvent(zipFile));
        component['errors'].set([{ identifier: '1-a', type: 'MISSING_TEXT_FILE' }]);

        component.resetState();

        expect(component['selectedFile']()).toBeUndefined();
        expect(component['errors']()).toEqual([]);
        expect(component['isUploading']()).toBe(false);
    });

    it('should hide the dialog on cancel', () => {
        component.close();
        expect(component.visible()).toBe(false);
    });
});
