import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { ImageCropperModalComponent } from 'app/core/course/manage/image-cropper-modal/image-cropper-modal.component';
import { ImageCropperComponent } from 'app/shared/image-cropper/component/image-cropper.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ImageCropperModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ImageCropperModalComponent;
    let fixture: ComponentFixture<ImageCropperModalComponent>;
    let dialogRef: DynamicDialogRef;

    beforeEach(async () => {
        const mockDialogRef = {
            close: vi.fn(),
        };

        const mockDialogConfig = {
            data: {
                uploadFile: undefined,
                roundCropper: true,
                fileFormat: 'png',
            },
        };

        await TestBed.configureTestingModule({
            imports: [ImageCropperComponent, ImageCropperModalComponent],
            providers: [
                { provide: DynamicDialogRef, useValue: mockDialogRef },
                { provide: DynamicDialogConfig, useValue: mockDialogConfig },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ImageCropperModalComponent);
        component = fixture.componentInstance;
        dialogRef = TestBed.inject(DynamicDialogRef);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call onCancel when cancel button is clicked', () => {
        vi.spyOn(component, 'onCancel');
        const cancelButton = fixture.debugElement.nativeElement.querySelector('#cancel-cropping-button');

        cancelButton.click();
        fixture.detectChanges();

        expect(component.onCancel).toHaveBeenCalled();
    });

    it('should call onSave when save button is clicked', () => {
        vi.spyOn(component, 'onSave');
        const saveButton = fixture.debugElement.nativeElement.querySelector('#save-cropping-button');

        saveButton.click();
        fixture.detectChanges();

        expect(component.onSave).toHaveBeenCalled();
    });

    it('should close the modal when onCancel is called', () => {
        component.onCancel();
        expect(dialogRef.close).toHaveBeenCalled();
    });

    it('should close the modal with cropped image when onSave is called', () => {
        component.croppedImage.set('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAU...');
        component.onSave();
        expect(dialogRef.close).toHaveBeenCalledWith(component.croppedImage());
    });

    it('should update croppedImage signal when imageCropped is called', () => {
        const mockEvent = {
            base64: 'data:image/png;base64,newImageData',
        };

        component.imageCropped(mockEvent as any);

        expect(component.croppedImage()).toBe('data:image/png;base64,newImageData');
    });

    it('should initialize with default values from config', () => {
        expect(component.roundCropper()).toBe(true);
        expect(component.fileFormat()).toBe('png');
        expect(component.uploadFile()).toBeUndefined();
    });
});
