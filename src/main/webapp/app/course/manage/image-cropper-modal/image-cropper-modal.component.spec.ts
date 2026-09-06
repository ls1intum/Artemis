import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ImageCropperModalComponent } from 'app/course/manage/image-cropper-modal/image-cropper-modal.component';
import { ImageCropperComponent } from 'app/shared-ui/image-cropper/component/image-cropper.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ImageCropperModalComponent', () => {
    let component: ImageCropperModalComponent;
    let fixture: ComponentFixture<ImageCropperModalComponent>;
    let cropped: ReturnType<typeof vi.fn<(image: string) => void>>;
    let cancelled: ReturnType<typeof vi.fn<() => void>>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ImageCropperComponent, ImageCropperModalComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ImageCropperModalComponent);
        component = fixture.componentInstance;
        cropped = vi.fn<(image: string) => void>();
        cancelled = vi.fn<() => void>();
        component.cropped.subscribe(cropped);
        component.cancelled.subscribe(cancelled);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('renders no heading of its own, because the hosting dialog supplies the title', () => {
        expect(fixture.nativeElement.querySelector('h1, h2, h3, h4, h5, h6')).toBeNull();
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

    it('reports a cancellation to its host when onCancel is called', () => {
        component.onCancel();
        expect(cancelled).toHaveBeenCalled();
        expect(cropped).not.toHaveBeenCalled();
    });

    it('hands the cropped image to its host when onSave is called', () => {
        component.croppedImage.set('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAU...');
        component.onSave();
        expect(cropped).toHaveBeenCalledWith(component.croppedImage());
    });

    it('emits nothing when saving before anything has been cropped', () => {
        component.onSave();
        expect(cropped).not.toHaveBeenCalled();
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
