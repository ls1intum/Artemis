import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ImageCropperComponent } from 'app/shared/image-cropper/component/image-cropper.component';
import { CropperPositionService } from 'app/shared/image-cropper/services/cropper-position.service';
import { CropService } from 'app/shared/image-cropper/services/crop.service';
import { LoadedImage } from 'app/shared/image-cropper/interfaces/loaded-image.interface';
import { MockProvider } from 'ng-mocks';
import { LoadImageService } from 'app/shared/image-cropper/services/load-image.service';
import { MoveStart } from 'app/shared/image-cropper/interfaces/move-start.interface';
import { CropperPosition } from 'app/shared/image-cropper/interfaces/cropper-position.interface';
import { CropperSettings } from 'app/shared/image-cropper/interfaces/cropper.settings';
import { ElementRef } from '@angular/core';
import { ImageCroppedEvent } from 'app/shared/image-cropper/interfaces/image-cropped-event.interface';

describe('ImageCropperComponent', () => {
    let fixture: ComponentFixture<ImageCropperComponent>;
    let comp: ImageCropperComponent;
    let cropperPositionService: CropperPositionService;
    let cropService: CropService;
    let loadImageService: LoadImageService;
    let resetCropperPositionSpy: jest.SpyInstance;
    let componentCropSpy: jest.SpyInstance;
    let startCropImageSpy: jest.SpyInstance;
    let imageCroppedSpy: jest.SpyInstance;
    let cropServiceCropSpy: jest.SpyInstance;
    let loadImageFileSpy: jest.SpyInstance;
    let loadBase64ImageSpy: jest.SpyInstance;
    let loadImageFromURLSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [ImageCropperComponent],
            providers: [MockProvider(CropService), MockProvider(CropperPositionService), MockProvider(LoadImageService)],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ImageCropperComponent);
                comp = fixture.componentInstance;
                cropperPositionService = TestBed.inject(CropperPositionService);
                cropService = TestBed.inject(CropService);
                loadImageService = TestBed.inject(LoadImageService);
                resetCropperPositionSpy = jest.spyOn(cropperPositionService, 'resetCropperPosition');
                componentCropSpy = jest.spyOn(comp, 'crop');
                startCropImageSpy = jest.spyOn(comp.startCropImage, 'emit');
                imageCroppedSpy = jest.spyOn(comp.imageCropped, 'emit');
                cropServiceCropSpy = jest.spyOn(cropService, 'crop');
                loadImageFileSpy = jest.spyOn(loadImageService, 'loadImageFile');
                loadBase64ImageSpy = jest.spyOn(loadImageService, 'loadBase64Image');
                loadImageFromURLSpy = jest.spyOn(loadImageService, 'loadImageFromURL');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('reset cropper position', () => {
        let cropper: CropperPosition;
        let settings: CropperSettings;
        let sourceImage: ElementRef<HTMLDivElement>;

        beforeEach(() => {
            cropper = comp.cropper;
            settings = comp.settings;
            sourceImage = comp.sourceImage;
            comp.imageVisible = false;
        });

        it('should reset cropper position without auto crop', () => {
            comp.autoCrop = false;

            comp.resetCropperPosition();

            expect(resetCropperPositionSpy).toHaveBeenCalledOnce();
            expect(resetCropperPositionSpy).toHaveBeenCalledWith(sourceImage, cropper, settings);
            expect(componentCropSpy).not.toHaveBeenCalled();
            expect(comp.imageVisible).toBeTrue();
        });

        it('should reset cropper position with auto crop', () => {
            comp.autoCrop = true;

            comp.resetCropperPosition();

            expect(resetCropperPositionSpy).toHaveBeenCalledOnce();
            expect(resetCropperPositionSpy).toHaveBeenCalledWith(sourceImage, cropper, settings);
            expect(componentCropSpy).toHaveBeenCalledOnce();
            expect(comp.imageVisible).toBeTrue();
        });
    });

    it('should not crop without loaded image', () => {
        comp.loadedImage = undefined;
        let res = comp.crop();
        expect(res).toBeUndefined();

        comp.loadedImage = { transformed: {} } as LoadedImage;
        res = comp.crop();

        expect(res).toBeUndefined();
    });

    describe('crop', () => {
        let cropper: CropperPosition;
        let settings: CropperSettings;
        let sourceImage: ElementRef<HTMLDivElement>;
        let loadedImage: LoadedImage;
        let fakeEvent: ImageCroppedEvent;

        beforeEach(() => {
            cropper = comp.cropper;
            settings = comp.settings;
            sourceImage = comp.sourceImage;
            loadedImage = { transformed: { base64: 'base64', image: new Image(), size: { width: 100, height: 100 } } } as LoadedImage;
            fakeEvent = { base64: 'base64', width: 100, height: 100, cropperPosition: { x1: 42, y1: 42, x2: 42, y2: 42 }, imagePosition: { x1: 42, y1: 42, x2: 42, y2: 42 } };
        });

        it('should crop and emit with loaded image', () => {
            cropServiceCropSpy.mockReturnValue(fakeEvent);
            comp.loadedImage = loadedImage;

            const res = comp.crop();

            expect(startCropImageSpy).toHaveBeenCalledOnce();
            expect(startCropImageSpy).toHaveBeenCalledWith();
            expect(cropServiceCropSpy).toHaveBeenCalledOnce();
            expect(cropServiceCropSpy).toHaveBeenCalledWith(sourceImage, loadedImage, cropper, settings);
            expect(imageCroppedSpy).toHaveBeenCalledOnce();
            expect(imageCroppedSpy).toHaveBeenCalledWith(fakeEvent);
            expect(res).toBe(fakeEvent);
        });

        it('should crop but not emit without cropping output', () => {
            cropServiceCropSpy.mockReturnValue(undefined);
            comp.loadedImage = loadedImage;

            const res = comp.crop();

            expect(startCropImageSpy).toHaveBeenCalledOnce();
            expect(startCropImageSpy).toHaveBeenCalledWith();
            expect(cropServiceCropSpy).toHaveBeenCalledOnce();
            expect(cropServiceCropSpy).toHaveBeenCalledWith(sourceImage, loadedImage, cropper, settings);
            expect(imageCroppedSpy).not.toHaveBeenCalled();
            expect(res).toBeUndefined();
        });
    });

    it('should reset when removing image', () => {
        loadImageFileSpy.mockImplementation(() => Promise.resolve({ transformed: {} } as LoadedImage));
        fixture.componentRef.setInput('imageFile', new File([], 'test'));
        fixture.detectChanges();
        loadImageFileSpy.mockClear();

        comp.imageVisible = true;
        comp.loadedImage = { transformed: {} } as LoadedImage;
        comp.cropper = { x1: 42, y1: 42, x2: 42, y2: 42 };
        comp.maxSize = { width: 42, height: 42 };
        comp.moveStart = {} as MoveStart;

        fixture.componentRef.setInput('imageFile', undefined);
        fixture.detectChanges();

        expect(comp.imageVisible).toBeFalse();
        expect(comp.loadedImage).toBeUndefined();
        expect(comp.cropper).toEqual({ x1: -100, y1: -100, x2: 10000, y2: 10000 });
        expect(comp.maxSize).toEqual({ width: 0, height: 0 });
        expect(comp.moveStart).not.toEqual({});
    });

    it('should not reset when image does not get changed', () => {
        loadImageFileSpy.mockImplementation(() => Promise.resolve({ transformed: {} } as LoadedImage));
        fixture.componentRef.setInput('imageFile', new File([], 'test'));
        fixture.detectChanges();
        loadImageFileSpy.mockClear();

        const loadedImage = { transformed: {}, original: { image: { complete: true } } } as LoadedImage;
        const cropper = { x1: 42, y1: 42, x2: 42, y2: 42 };
        const maxSize = { width: 42, height: 42 };
        const moveStart = {} as MoveStart;
        comp.imageVisible = true;
        comp.loadedImage = loadedImage;
        comp.cropper = cropper;
        comp.maxSize = maxSize;
        comp.moveStart = moveStart;

        fixture.componentRef.setInput('autoCrop', false);
        fixture.detectChanges();

        expect(comp.imageVisible).toBeTrue();
        expect(comp.loadedImage).toBe(loadedImage);
        expect(comp.cropper).toBe(cropper);
        expect(comp.maxSize).toBe(maxSize);
        expect(comp.moveStart).toBe(moveStart);
    });

    describe('load image', () => {
        const base64String = Buffer.from('testContent').toString('base64');

        it('should load from file', () => {
            loadImageFileSpy.mockImplementation(() => Promise.resolve({ transformed: { base64: base64String } } as LoadedImage));
            const file = new File([], 'test');
            const settings = comp.settings;
            fixture.componentRef.setInput('imageFile', file);
            fixture.detectChanges();

            expect(loadImageFileSpy).toHaveBeenCalledOnce();
            expect(loadImageFileSpy).toHaveBeenCalledWith(file, settings);
            expect(loadImageFromURLSpy).not.toHaveBeenCalled();
            expect(loadBase64ImageSpy).not.toHaveBeenCalled();
        });

        it('should load from URL', () => {
            loadImageFromURLSpy.mockImplementation(() => Promise.resolve({ transformed: { base64: base64String } } as LoadedImage));
            const url = 'https://test.com/path_to_image.png';
            const settings = comp.settings;
            fixture.componentRef.setInput('imageURL', url);
            fixture.detectChanges();

            expect(loadImageFromURLSpy).toHaveBeenCalledOnce();
            expect(loadImageFromURLSpy).toHaveBeenCalledWith(url, settings);
            expect(loadImageFileSpy).not.toHaveBeenCalled();
            expect(loadBase64ImageSpy).not.toHaveBeenCalled();
        });

        it('should load from Base64', () => {
            loadBase64ImageSpy.mockImplementation(() => Promise.resolve({ transformed: { base64: base64String } } as LoadedImage));
            const content = base64String;
            const settings = comp.settings;
            fixture.componentRef.setInput('imageBase64', content);
            fixture.detectChanges();

            expect(loadBase64ImageSpy).toHaveBeenCalledOnce();
            expect(loadBase64ImageSpy).toHaveBeenCalledWith(content, settings);
            expect(loadImageFileSpy).not.toHaveBeenCalled();
            expect(loadImageFromURLSpy).not.toHaveBeenCalled();
        });

        it('should load from event', () => {
            loadImageFileSpy.mockImplementation(() => Promise.resolve({ transformed: { base64: base64String } } as LoadedImage));
            const file = new File([], 'test');
            const event = { currentTarget: { files: [file] } as unknown as HTMLInputElement };
            const settings = comp.settings;
            fixture.componentRef.setInput('imageChangedEvent', event);
            fixture.detectChanges();

            expect(loadImageFileSpy).toHaveBeenCalledOnce();
            expect(loadImageFileSpy).toHaveBeenCalledWith(file, settings);
        });
    });
});
