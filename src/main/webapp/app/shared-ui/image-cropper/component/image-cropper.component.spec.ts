import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ImageCropperComponent } from 'app/shared-ui/image-cropper/component/image-cropper.component';
import { CropperPositionService } from 'app/shared-ui/image-cropper/services/cropper-position.service';
import { CropService } from 'app/shared-ui/image-cropper/services/crop.service';
import { LoadedImage } from 'app/shared-ui/image-cropper/interfaces/loaded-image.interface';
import { MockProvider } from 'ng-mocks';
import { LoadImageService } from 'app/shared-ui/image-cropper/services/load-image.service';
import { MoveStart, MoveTypes } from 'app/shared-ui/image-cropper/interfaces/move-start.interface';
import { CropperPosition } from 'app/shared-ui/image-cropper/interfaces/cropper-position.interface';
import { CropperSettings } from 'app/shared-ui/image-cropper/interfaces/cropper.settings';
import { ElementRef } from '@angular/core';
import { ImageCroppedEvent } from 'app/shared-ui/image-cropper/interfaces/image-cropped-event.interface';

describe('ImageCropperComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ImageCropperComponent>;
    let comp: ImageCropperComponent;
    let cropperPositionService: CropperPositionService;
    let cropService: CropService;
    let loadImageService: LoadImageService;
    let resetCropperPositionSpy: ReturnType<typeof vi.spyOn>;
    let componentCropSpy: ReturnType<typeof vi.spyOn>;
    let startCropImageSpy: ReturnType<typeof vi.spyOn>;
    let cropServiceCropSpy: ReturnType<typeof vi.spyOn>;
    let loadImageFileSpy: ReturnType<typeof vi.spyOn>;
    let loadBase64ImageSpy: ReturnType<typeof vi.spyOn>;
    let loadImageFromURLSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [MockProvider(CropService), MockProvider(CropperPositionService), MockProvider(LoadImageService)],
        }).compileComponents();

        fixture = TestBed.createComponent(ImageCropperComponent);
        comp = fixture.componentInstance;
        cropperPositionService = TestBed.inject(CropperPositionService);
        cropService = TestBed.inject(CropService);
        loadImageService = TestBed.inject(LoadImageService);
        resetCropperPositionSpy = vi.spyOn(cropperPositionService, 'resetCropperPosition');
        componentCropSpy = vi.spyOn(comp, 'crop');
        startCropImageSpy = vi.spyOn(comp.startCropImage, 'emit');
        cropServiceCropSpy = vi.spyOn(cropService, 'crop');
        loadImageFileSpy = vi.spyOn(loadImageService, 'loadImageFile');
        loadBase64ImageSpy = vi.spyOn(loadImageService, 'loadBase64Image');
        loadImageFromURLSpy = vi.spyOn(loadImageService, 'loadImageFromURL');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(comp).toBeTruthy();
    });

    describe('ngOnInit', () => {
        it('should set initial step size from the input', () => {
            fixture.componentRef.setInput('initialStepSize', 5);
            comp.ngOnInit();
            expect(comp.settings.stepSize).toBe(5);
        });
    });

    describe('reset cropper position', () => {
        let cropper: CropperPosition;
        let settings: CropperSettings;
        let sourceImage: ElementRef<HTMLDivElement> | undefined;

        beforeEach(() => {
            cropper = comp.cropper;
            settings = comp.settings;
            sourceImage = comp.sourceImage();
            comp.imageVisible = false;
        });

        it('should reset cropper position without auto crop', () => {
            fixture.componentRef.setInput('autoCrop', false);

            comp.resetCropperPosition();

            // Without a sourceImage view child, cropperPositionService.resetCropperPosition is not invoked.
            expect(resetCropperPositionSpy).not.toHaveBeenCalled();
            expect(componentCropSpy).not.toHaveBeenCalled();
            expect(comp.imageVisible).toBeTruthy();
            expect(cropper).toBe(comp.cropper);
            expect(settings).toBe(comp.settings);
            expect(sourceImage).toBeUndefined();
        });

        it('should reset cropper position with auto crop', () => {
            fixture.componentRef.setInput('autoCrop', true);

            comp.resetCropperPosition();

            expect(resetCropperPositionSpy).not.toHaveBeenCalled();
            expect(componentCropSpy).toHaveBeenCalledOnce();
            expect(comp.imageVisible).toBeTruthy();
        });
    });

    describe('crop', () => {
        it('should not crop without loaded image', () => {
            comp.loadedImage = undefined;
            expect(comp.crop()).toBeUndefined();

            comp.loadedImage = { transformed: {} } as LoadedImage;
            expect(comp.crop()).toBeUndefined();
        });

        it('emits startCropImage but skips cropService when sourceImage view child is missing', () => {
            const loadedImage = { transformed: { base64: 'base64', image: new Image(), size: { width: 100, height: 100 } } } as LoadedImage;
            comp.loadedImage = loadedImage;
            cropServiceCropSpy.mockReturnValue({ base64: 'base64' } as ImageCroppedEvent);

            const res = comp.crop();

            expect(res).toBeUndefined();
            // Preserve original behaviour: parents listening for `startCropImage` are still notified.
            expect(startCropImageSpy).toHaveBeenCalledOnce();
            // The expensive cropService call is skipped to avoid passing undefined to it.
            expect(cropServiceCropSpy).not.toHaveBeenCalled();
        });

        it('crops and emits when the loaded image and sourceImage view child are present', () => {
            const loadedImage = { transformed: { base64: 'base64', image: new Image(), size: { width: 100, height: 100 } } } as LoadedImage;
            comp.loadedImage = loadedImage;
            const sourceImage = new ElementRef(document.createElement('div'));
            vi.spyOn(comp, 'sourceImage').mockReturnValue(sourceImage as ElementRef<HTMLDivElement>);
            const croppedEvent = { base64: 'cropped-base64' } as ImageCroppedEvent;
            cropServiceCropSpy.mockReturnValue(croppedEvent);
            const imageCroppedSpy = vi.spyOn(comp.imageCropped, 'emit');

            const res = comp.crop();

            expect(startCropImageSpy).toHaveBeenCalledOnce();
            expect(cropServiceCropSpy).toHaveBeenCalledWith(sourceImage, loadedImage, comp.cropper, comp.settings);
            expect(imageCroppedSpy).toHaveBeenCalledWith(croppedEvent);
            expect(res).toBe(croppedEvent);
        });

        it('crops but does not emit when cropService returns no output', () => {
            const loadedImage = { transformed: { base64: 'base64', image: new Image(), size: { width: 100, height: 100 } } } as LoadedImage;
            comp.loadedImage = loadedImage;
            const sourceImage = new ElementRef(document.createElement('div'));
            vi.spyOn(comp, 'sourceImage').mockReturnValue(sourceImage as ElementRef<HTMLDivElement>);
            cropServiceCropSpy.mockReturnValue(undefined);
            const imageCroppedSpy = vi.spyOn(comp.imageCropped, 'emit');

            const res = comp.crop();

            expect(cropServiceCropSpy).toHaveBeenCalledOnce();
            expect(imageCroppedSpy).not.toHaveBeenCalled();
            expect(res).toBeUndefined();
        });
    });

    describe('input change reset paths', () => {
        it('resets imageVisible when imageURL changes', () => {
            loadImageFromURLSpy.mockImplementation(() => Promise.resolve({ transformed: {} } as LoadedImage));
            comp.imageVisible = true;
            fixture.componentRef.setInput('imageURL', 'http://example.com/image.png');
            fixture.detectChanges();
            expect(comp.imageVisible).toBeFalsy();
        });

        it('resets imageVisible when imageBase64 changes', () => {
            loadBase64ImageSpy.mockImplementation(() => Promise.resolve({ transformed: {} } as LoadedImage));
            comp.imageVisible = true;
            fixture.componentRef.setInput('imageBase64', 'data:image/png;base64,abc');
            fixture.detectChanges();
            expect(comp.imageVisible).toBeFalsy();
        });

        it('resets imageVisible when imageChangedEvent changes', () => {
            loadImageFileSpy.mockImplementation(() => Promise.resolve({ transformed: {} } as LoadedImage));
            comp.imageVisible = true;
            const file = new File([], 'test');
            fixture.componentRef.setInput('imageChangedEvent', { currentTarget: { files: [file] } });
            fixture.detectChanges();
            expect(comp.imageVisible).toBeFalsy();
        });

        it('updates safeTransformStyle when transform changes', () => {
            fixture.componentRef.setInput('transform', { scale: 2, rotate: 90 });
            fixture.detectChanges();
            expect(comp.safeTransformStyle).toBeDefined();
        });

        it('does not throw when transform is bound to undefined', () => {
            fixture.componentRef.setInput('transform', undefined);
            expect(() => fixture.detectChanges()).not.toThrow();
        });

        it('reacts to cropper input changes', () => {
            // Avoid the cropper being clamped to (0,0) by the post-sync `checkCropperPosition` call.
            comp.maxSize = { width: 1000, height: 1000 };
            fixture.componentRef.setInput('cropper', { x1: 10, y1: 20, x2: 30, y2: 40 });
            fixture.detectChanges();
            expect(comp.cropper).toEqual({ x1: 10, y1: 20, x2: 30, y2: 40 });
        });
    });

    it('should reset when removing image', async () => {
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

        expect(comp.imageVisible).toBeFalsy();
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

        expect(comp.imageVisible).toBeTruthy();
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
            const settings = comp.settings;
            fixture.componentRef.setInput('imageBase64', base64String);
            fixture.detectChanges();

            expect(loadBase64ImageSpy).toHaveBeenCalledOnce();
            expect(loadBase64ImageSpy).toHaveBeenCalledWith(base64String, settings);
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

    describe('startMove', () => {
        it('should start move action', () => {
            const event = { clientX: 100, clientY: 100, preventDefault: vi.fn() } as any;
            vi.spyOn(cropperPositionService, 'getClientX').mockReturnValue(100);
            vi.spyOn(cropperPositionService, 'getClientY').mockReturnValue(100);

            comp.startMove(event, MoveTypes.Move);

            expect(comp.moveStart.active).toBeTruthy();
            expect(comp.moveStart.type).toBe(MoveTypes.Move);
        });

        it('should not start move when pinch is active', () => {
            comp.moveStart = { active: true, type: MoveTypes.Pinch } as MoveStart;
            const event = { clientX: 100, clientY: 100, preventDefault: vi.fn() } as any;

            comp.startMove(event, MoveTypes.Move);

            expect(comp.moveStart.type).toBe(MoveTypes.Pinch);
        });

        it('should call preventDefault if available', () => {
            const preventDefault = vi.fn();
            const event = { clientX: 100, clientY: 100, preventDefault } as any;
            vi.spyOn(cropperPositionService, 'getClientX').mockReturnValue(100);
            vi.spyOn(cropperPositionService, 'getClientY').mockReturnValue(100);

            comp.startMove(event, MoveTypes.Move);

            expect(preventDefault).toHaveBeenCalled();
        });
    });

    describe('moveImg', () => {
        it('should not move when not active', () => {
            comp.moveStart = { active: false } as MoveStart;
            const moveSpy = vi.spyOn(cropperPositionService, 'move');

            comp.moveImg({ stopPropagation: vi.fn(), preventDefault: vi.fn() } as any);

            expect(moveSpy).not.toHaveBeenCalled();
        });

        it('should call move for Move type', () => {
            comp.moveStart = { active: true, type: MoveTypes.Move } as MoveStart;
            comp.cropper = { x1: 0, y1: 0, x2: 100, y2: 100 };
            const moveSpy = vi.spyOn(cropperPositionService, 'move');
            const event = { stopPropagation: vi.fn(), preventDefault: vi.fn() } as any;

            comp.moveImg(event);

            expect(moveSpy).toHaveBeenCalled();
        });

        it('should call resize for Resize type', () => {
            comp.moveStart = { active: true, type: MoveTypes.Resize } as MoveStart;
            comp.cropper = { x1: 0, y1: 0, x2: 100, y2: 100 };
            comp.maxSize = { width: 200, height: 200 };
            const resizeSpy = vi.spyOn(cropperPositionService, 'resize');
            const event = { stopPropagation: vi.fn(), preventDefault: vi.fn() } as any;

            comp.moveImg(event);

            expect(resizeSpy).toHaveBeenCalled();
        });
    });

    describe('moveStop', () => {
        it('should stop move and auto crop', () => {
            comp.moveStart = { active: true } as MoveStart;
            fixture.componentRef.setInput('autoCrop', true);
            componentCropSpy.mockReturnValue(undefined);

            comp.moveStop();

            expect(comp.moveStart.active).toBeFalsy();
            expect(componentCropSpy).toHaveBeenCalled();
        });

        it('should not auto crop when disabled', () => {
            comp.moveStart = { active: true } as MoveStart;
            fixture.componentRef.setInput('autoCrop', false);

            comp.moveStop();

            expect(componentCropSpy).not.toHaveBeenCalled();
        });
    });

    describe('onResize', () => {
        it('should return early when no loaded image', () => {
            comp.loadedImage = undefined;
            const resizeSpy = vi.spyOn<any, any>(comp, 'resizeCropperPosition');

            comp.onResize();

            expect(resizeSpy).not.toHaveBeenCalled();
        });
    });

    describe('keyboardAccess', () => {
        it('should change step size for number keys', () => {
            const event = { key: '5' } as KeyboardEvent;

            comp.keyboardAccess(event);

            expect(comp.settings.stepSize).toBe(5);
        });

        it('should not change step size for non-number keys', () => {
            comp.settings.stepSize = 1;
            const event = { key: 'a' } as KeyboardEvent;

            comp.keyboardAccess(event);

            expect(comp.settings.stepSize).toBe(1);
        });

        it('should not change step size for numbers outside 1-9', () => {
            comp.settings.stepSize = 1;
            const event = { key: '0' } as KeyboardEvent;

            comp.keyboardAccess(event);

            expect(comp.settings.stepSize).toBe(1);
        });
    });

    describe('imageLoadedInView', () => {
        it('should emit imageLoaded when loadedImage exists', () => {
            comp.loadedImage = { original: {}, transformed: {} } as LoadedImage;
            const emitSpy = vi.spyOn(comp.imageLoaded, 'emit');

            comp.imageLoadedInView();

            expect(emitSpy).toHaveBeenCalledWith(comp.loadedImage);
            expect(comp.setImageMaxSizeRetries).toBe(0);
        });

        it('should not emit when loadedImage is undefined', () => {
            comp.loadedImage = undefined;
            const emitSpy = vi.spyOn(comp.imageLoaded, 'emit');

            comp.imageLoadedInView();

            expect(emitSpy).not.toHaveBeenCalled();
        });
    });

    describe('inputs', () => {
        it('defaults format to png', () => {
            expect(comp.format()).toBe('png');
        });

        it('defaults maintainAspectRatio to true', () => {
            expect(comp.maintainAspectRatio()).toBeTruthy();
        });

        it('defaults aspectRatio to 1', () => {
            expect(comp.aspectRatio()).toBe(1);
        });

        it('defaults disabled to false', () => {
            expect(comp.disabled()).toBeFalsy();
        });

        it('defaults alignImage to center', () => {
            expect(comp.alignImage()).toBe('center');
        });
    });
});
