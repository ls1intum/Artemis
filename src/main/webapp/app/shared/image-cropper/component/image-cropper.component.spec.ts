import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange, SimpleChanges } from '@angular/core';
import { ImageCropperComponent } from 'app/shared/image-cropper/component/image-cropper.component';
import { CropService } from 'app/shared/image-cropper/services/crop.service';
import { CropperPositionService } from 'app/shared/image-cropper/services/cropper-position.service';
import { LoadImageService } from 'app/shared/image-cropper/services/load-image.service';
import { MockProvider } from 'ng-mocks';
import { MoveTypes } from 'app/shared/image-cropper/interfaces/move-start.interface';

describe('ImageCropperComponent', () => {
    let fixture: ComponentFixture<ImageCropperComponent>;
    let comp: ImageCropperComponent;
    let cropService: CropService;
    let cropperPositionService: CropperPositionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ImageCropperComponent],
            providers: [MockProvider(CropService), MockProvider(CropperPositionService), MockProvider(LoadImageService)],
        }).compileComponents();

        fixture = TestBed.createComponent(ImageCropperComponent);
        comp = fixture.componentInstance;
        cropService = TestBed.inject(CropService);
        cropperPositionService = TestBed.inject(CropperPositionService);
    });

    it('should create', () => {
        expect(comp).toBeTruthy();
    });

    describe('ngOnInit', () => {
        it('should set initial step size', () => {
            comp.initialStepSize = 5;

            comp.ngOnInit();

            expect(comp.settings.stepSize).toBe(5);
        });
    });

    describe('ngOnChanges', () => {
        it('should reset when imageChangedEvent changes', () => {
            const changes: SimpleChanges = {
                imageChangedEvent: new SimpleChange(null, {} as Event, true),
            };

            comp.ngOnChanges(changes);

            expect(comp.imageVisible).toBeFalse();
        });

        it('should reset when imageURL changes', () => {
            const changes: SimpleChanges = {
                imageURL: new SimpleChange(null, 'http://test.com/image.png', true),
            };

            comp.ngOnChanges(changes);

            expect(comp.imageVisible).toBeFalse();
        });

        it('should reset when imageBase64 changes', () => {
            const changes: SimpleChanges = {
                imageBase64: new SimpleChange(null, 'data:image/png;base64,abc', true),
            };

            comp.ngOnChanges(changes);

            expect(comp.imageVisible).toBeFalse();
        });

        it('should reset when imageFile changes', () => {
            const changes: SimpleChanges = {
                imageFile: new SimpleChange(null, new File([], 'test.png'), true),
            };

            comp.ngOnChanges(changes);

            expect(comp.imageVisible).toBeFalse();
        });

        it('should set css transform when transform changes', () => {
            comp.transform = { scale: 2, rotate: 90 };
            const changes: SimpleChanges = {
                transform: new SimpleChange(null, { scale: 2, rotate: 90 }, false),
            };

            comp.ngOnChanges(changes);

            expect(comp.safeTransformStyle).toBeDefined();
        });
    });

    describe('startMove', () => {
        it('should start move action', () => {
            const event = { clientX: 100, clientY: 100, preventDefault: jest.fn() } as any;
            jest.spyOn(cropperPositionService, 'getClientX').mockReturnValue(100);
            jest.spyOn(cropperPositionService, 'getClientY').mockReturnValue(100);

            comp.startMove(event, MoveTypes.Move);

            expect(comp.moveStart.active).toBeTrue();
            expect(comp.moveStart.type).toBe(MoveTypes.Move);
        });

        it('should not start move when pinch is active', () => {
            comp.moveStart = { active: true, type: MoveTypes.Pinch } as any;
            const event = { clientX: 100, clientY: 100, preventDefault: jest.fn() } as any;

            comp.startMove(event, MoveTypes.Move);

            expect(comp.moveStart.type).toBe(MoveTypes.Pinch);
        });

        it('should call preventDefault if available', () => {
            const preventDefault = jest.fn();
            const event = { clientX: 100, clientY: 100, preventDefault } as any;
            jest.spyOn(cropperPositionService, 'getClientX').mockReturnValue(100);
            jest.spyOn(cropperPositionService, 'getClientY').mockReturnValue(100);

            comp.startMove(event, MoveTypes.Move);

            expect(preventDefault).toHaveBeenCalled();
        });
    });

    describe('moveImg', () => {
        it('should not move when not active', () => {
            comp.moveStart = { active: false } as any;
            const moveSpy = jest.spyOn(cropperPositionService, 'move');

            comp.moveImg({ stopPropagation: jest.fn(), preventDefault: jest.fn() } as any);

            expect(moveSpy).not.toHaveBeenCalled();
        });

        it('should call move for Move type', () => {
            comp.moveStart = { active: true, type: MoveTypes.Move } as any;
            comp.cropper = { x1: 0, y1: 0, x2: 100, y2: 100 };
            const moveSpy = jest.spyOn(cropperPositionService, 'move');
            const event = { stopPropagation: jest.fn(), preventDefault: jest.fn() } as any;

            comp.moveImg(event);

            expect(moveSpy).toHaveBeenCalled();
        });

        it('should call resize for Resize type', () => {
            comp.moveStart = { active: true, type: MoveTypes.Resize } as any;
            comp.cropper = { x1: 0, y1: 0, x2: 100, y2: 100 };
            comp.maxSize = { width: 200, height: 200 };
            const resizeSpy = jest.spyOn(cropperPositionService, 'resize');
            const event = { stopPropagation: jest.fn(), preventDefault: jest.fn() } as any;

            comp.moveImg(event);

            expect(resizeSpy).toHaveBeenCalled();
        });
    });

    describe('moveStop', () => {
        it('should stop move and auto crop', () => {
            comp.moveStart = { active: true } as any;
            comp.autoCrop = true;
            const cropSpy = jest.spyOn(comp, 'crop').mockReturnValue(undefined);

            comp.moveStop();

            expect(comp.moveStart.active).toBeFalse();
            expect(cropSpy).toHaveBeenCalled();
        });

        it('should not auto crop when disabled', () => {
            comp.moveStart = { active: true } as any;
            comp.autoCrop = false;
            const cropSpy = jest.spyOn(comp, 'crop');

            comp.moveStop();

            expect(cropSpy).not.toHaveBeenCalled();
        });
    });

    describe('crop', () => {
        it('should return undefined when no loaded image', () => {
            comp.loadedImage = undefined;

            const result = comp.crop();

            expect(result).toBeUndefined();
        });

        it('should emit events when image is loaded', () => {
            comp.loadedImage = {
                transformed: {
                    image: {} as HTMLImageElement,
                },
            } as any;
            const startSpy = jest.spyOn(comp.startCropImage, 'emit');
            const croppedSpy = jest.spyOn(comp.imageCropped, 'emit');
            const mockOutput = { base64: 'test' };
            jest.spyOn(cropService, 'crop').mockReturnValue(mockOutput as any);

            const result = comp.crop();

            expect(startSpy).toHaveBeenCalled();
            expect(croppedSpy).toHaveBeenCalledWith(mockOutput);
            expect(result).toEqual(mockOutput);
        });
    });

    describe('onResize', () => {
        it('should return early when no loaded image', () => {
            comp.loadedImage = undefined;
            const resizeSpy = jest.spyOn<any, any>(comp, 'resizeCropperPosition');

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

    describe('resetCropperPosition', () => {
        it('should reset position and make image visible', () => {
            const resetSpy = jest.spyOn(cropperPositionService, 'resetCropperPosition');
            comp.autoCrop = false;

            comp.resetCropperPosition();

            expect(resetSpy).toHaveBeenCalled();
            expect(comp.imageVisible).toBeTrue();
        });
    });

    describe('imageLoadedInView', () => {
        it('should emit imageLoaded when loadedImage exists', () => {
            comp.loadedImage = { original: {}, transformed: {} } as any;
            const emitSpy = jest.spyOn(comp.imageLoaded, 'emit');

            comp.imageLoadedInView();

            expect(emitSpy).toHaveBeenCalledWith(comp.loadedImage);
            expect(comp.setImageMaxSizeRetries).toBe(0);
        });

        it('should not emit when loadedImage is undefined', () => {
            comp.loadedImage = undefined;
            const emitSpy = jest.spyOn(comp.imageLoaded, 'emit');

            comp.imageLoadedInView();

            expect(emitSpy).not.toHaveBeenCalled();
        });
    });

    describe('inputs', () => {
        it('should have default format', () => {
            expect(comp.format).toBe(comp.settings.format);
        });

        it('should have default maintainAspectRatio', () => {
            expect(comp.maintainAspectRatio).toBe(comp.settings.maintainAspectRatio);
        });

        it('should have default aspectRatio', () => {
            expect(comp.aspectRatio).toBe(comp.settings.aspectRatio);
        });

        it('should have disabled as false by default', () => {
            expect(comp.disabled).toBeFalse();
        });

        it('should have alignImage as center by default', () => {
            expect(comp.alignImage).toBe(comp.settings.alignImage);
        });
    });

    describe('outputs', () => {
        it('should have imageCropped output', () => {
            expect(comp.imageCropped).toBeDefined();
        });

        it('should have startCropImage output', () => {
            expect(comp.startCropImage).toBeDefined();
        });

        it('should have imageLoaded output', () => {
            expect(comp.imageLoaded).toBeDefined();
        });

        it('should have cropperReady output', () => {
            expect(comp.cropperReady).toBeDefined();
        });

        it('should have loadImageFailed output', () => {
            expect(comp.loadImageFailed).toBeDefined();
        });
    });
});
