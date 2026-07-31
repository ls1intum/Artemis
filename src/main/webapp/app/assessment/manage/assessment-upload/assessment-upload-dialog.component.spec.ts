import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
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

    it('should upload for the bound exercise id and store the success result', () => {
        const result: AssessmentUploadResult = { numberOfCreatedAssessments: 2, createdStudentIdentifiers: ['1-a', '2-b'] };
        uploadSpy.mockReturnValue(of(new HttpResponse({ body: result })));

        component.onFileInputChange(fileInputEvent(zipFile));
        component.upload();

        expect(uploadSpy).toHaveBeenCalledWith(7, zipFile);
        expect(component['successResult']()).toEqual(result);
        expect(component['errors']()).toHaveLength(0);
        expect(alertSuccess).toHaveBeenCalledWith('artemisApp.assessmentUpload.success', { count: 2 });
    });

    it('should show the validation errors and no success when the upload is rejected', () => {
        const result: AssessmentUploadResult = { numberOfCreatedAssessments: 0, errors: [{ identifier: '1-a', type: 'MISSING_TEXT_FILE' }] };
        uploadSpy.mockReturnValue(of(new HttpResponse({ body: result })));

        component.onFileInputChange(fileInputEvent(zipFile));
        component.upload();

        expect(component['errors']()).toEqual(result.errors);
        expect(component['successResult']()).toBeUndefined();
        expect(alertSuccess).not.toHaveBeenCalled();
    });

    it('should hide the dialog on cancel', () => {
        component.close();
        expect(component.visible()).toBe(false);
    });
});
