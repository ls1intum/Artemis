import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, OnChanges, OnInit, SimpleChanges, inject, input, output, viewChild } from '@angular/core';
import { DomSanitizer, SafeStyle, SafeUrl } from '@angular/platform-browser';
import { OutputFormat } from '../interfaces/cropper-options.interface';
import { CropperSettings } from '../interfaces/cropper.settings';
import { MoveStart, MoveTypes } from '../interfaces/move-start.interface';
import { CropService } from '../services/crop.service';
import { CropperPositionService } from '../services/cropper-position.service';
import { LoadImageService } from '../services/load-image.service';
import { getEventForKey, getInvertedPositionForKey, getPositionForKey } from '../utils/keyboard.utils';
import { LoadedImage } from 'app/shared-ui/image-cropper/interfaces/loaded-image.interface';
import { Dimensions } from 'app/shared-ui/image-cropper/interfaces/dimensions.interface';
import { ImageTransform } from 'app/shared-ui/image-cropper/interfaces/image-transform.interface';
import { CropperPosition } from 'app/shared-ui/image-cropper/interfaces/cropper-position.interface';
import { ImageCroppedEvent } from 'app/shared-ui/image-cropper/interfaces/image-cropped-event.interface';
import { captureException } from '@sentry/angular';

// Note: this component and all files in the image-cropper folder were taken from https://github.com/Mawi137/ngx-image-cropper because the framework was not maintained anymore
// Note: Partially adapted to fit Artemis needs

const defaultCropperPosition = (): CropperPosition => ({ x1: -100, y1: -100, x2: 10000, y2: 10000 });
const defaultSettings = new CropperSettings();

@Component({
    selector: 'image-cropper',
    templateUrl: './image-cropper.component.html',
    styleUrls: ['./image-cropper.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: {
        '[style.text-align]': 'alignImage()',
        '[class.disabled]': 'disabled()',
        '(window:resize)': 'onResize()',
        '(document:mousemove)': 'moveImg($event)',
        '(document:touchmove)': 'moveImg($event)',
        '(document:mouseup)': 'moveStop()',
        '(document:touchend)': 'moveStop()',
    },
})
export class ImageCropperComponent implements OnChanges, OnInit {
    private cropService = inject(CropService);
    private cropperPositionService = inject(CropperPositionService);
    private loadImageService = inject(LoadImageService);
    private sanitizer = inject(DomSanitizer);
    private changeDetector = inject(ChangeDetectorRef);

    settings = new CropperSettings();
    setImageMaxSizeRetries = 0;
    moveStart: MoveStart;
    loadedImage?: LoadedImage;

    safeImgDataUrl: SafeUrl | string;
    safeTransformStyle: SafeStyle | string;
    marginLeft: SafeStyle | string = '0px';
    maxSize: Dimensions;
    moveTypes = MoveTypes;
    imageVisible = false;

    /** Mutable internal cropper position. Synced from the {@link cropperInput} on each ngOnChanges run. */
    cropper: CropperPosition = defaultCropperPosition();

    readonly wrapper = viewChild.required<ElementRef<HTMLDivElement>>('wrapper');
    readonly sourceImage = viewChild<ElementRef<HTMLDivElement>>('sourceImage');

    readonly imageChangedEvent = input<Event | undefined>();
    readonly imageURL = input<string | undefined>();
    readonly imageBase64 = input<string | undefined>();
    readonly imageFile = input<File | undefined>();

    readonly format = input<OutputFormat>(defaultSettings.format);
    readonly transform = input<ImageTransform>({});
    readonly maintainAspectRatio = input<boolean>(defaultSettings.maintainAspectRatio);
    readonly aspectRatio = input<number>(defaultSettings.aspectRatio);
    readonly resizeToWidth = input<number>(defaultSettings.resizeToWidth);
    readonly resizeToHeight = input<number>(defaultSettings.resizeToHeight);
    readonly cropperMinWidth = input<number>(defaultSettings.cropperMinWidth);
    readonly cropperMinHeight = input<number>(defaultSettings.cropperMinHeight);
    readonly cropperMaxHeight = input<number>(defaultSettings.cropperMaxHeight);
    readonly cropperMaxWidth = input<number>(defaultSettings.cropperMaxWidth);
    readonly cropperStaticWidth = input<number>(defaultSettings.cropperStaticWidth);
    readonly cropperStaticHeight = input<number>(defaultSettings.cropperStaticHeight);
    readonly canvasRotation = input<number>(defaultSettings.canvasRotation);
    readonly initialStepSize = input<number>(defaultSettings.initialStepSize);
    readonly roundCropper = input<boolean>(defaultSettings.roundCropper);
    readonly onlyScaleDown = input<boolean>(defaultSettings.onlyScaleDown);
    readonly imageQuality = input<number>(defaultSettings.imageQuality);
    readonly autoCrop = input<boolean>(defaultSettings.autoCrop);
    readonly backgroundColor = input<string | undefined>(defaultSettings.backgroundColor);
    readonly containWithinAspectRatio = input<boolean>(defaultSettings.containWithinAspectRatio);
    readonly hideResizeSquares = input<boolean>(defaultSettings.hideResizeSquares);
    readonly cropperInput = input<CropperPosition>(defaultCropperPosition(), { alias: 'cropper' }); // eslint-disable-line @angular-eslint/no-input-rename
    readonly alignImage = input<'left' | 'center'>(defaultSettings.alignImage);
    readonly disabled = input<boolean>(false);

    readonly imageCropped = output<ImageCroppedEvent>();
    readonly startCropImage = output<void>();
    readonly imageLoaded = output<LoadedImage>();
    readonly cropperReady = output<Dimensions>();
    readonly loadImageFailed = output<void>();

    constructor() {
        this.reset();
    }

    ngOnInit(): void {
        this.settings.stepSize = this.initialStepSize();
    }

    ngOnChanges(changes: SimpleChanges): void {
        // Keep the internal mutable cropper in sync with the input.
        if (changes.cropperInput) {
            this.cropper = { ...this.cropperInput() };
        }

        this.onChangesUpdateSettings(changes);
        this.onChangesInputImage(changes);

        if (this.loadedImage?.original.image.complete && (changes.containWithinAspectRatio || changes.canvasRotation)) {
            this.loadImageService
                .transformLoadedImage(this.loadedImage, this.settings)
                .then((res) => this.setLoadedImage(res))
                .catch((err: Error) => this.loadImageError(err));
        }
        if (changes.cropperInput || changes.maintainAspectRatio || changes.aspectRatio) {
            this.setMaxSize();
            this.setCropperScaledMinSize();
            this.setCropperScaledMaxSize();
            if (this.maintainAspectRatio() && (changes.maintainAspectRatio || changes.aspectRatio)) {
                this.resetCropperPosition();
            } else if (changes.cropperInput) {
                this.checkCropperPosition(false);
                this.doAutoCrop();
            }
            this.changeDetector.markForCheck();
        }
        if (changes.transform) {
            this.setCssTransform();
            this.doAutoCrop();
        }
    }

    private onChangesUpdateSettings(changes: SimpleChanges) {
        // Translate signal-input change keys into the option names CropperSettings expects.
        const optionChanges: SimpleChanges = { ...changes };
        if (changes.cropperInput) {
            optionChanges.cropper = changes.cropperInput;
            delete optionChanges.cropperInput;
        }
        this.settings.setOptionsFromChanges(optionChanges);

        if (this.settings.cropperStaticHeight && this.settings.cropperStaticWidth) {
            this.settings.setOptions({
                hideResizeSquares: true,
                cropperMinWidth: this.settings.cropperStaticWidth,
                cropperMinHeight: this.settings.cropperStaticHeight,
                cropperMaxHeight: this.settings.cropperStaticHeight,
                cropperMaxWidth: this.settings.cropperStaticWidth,
                maintainAspectRatio: false,
            });
        }
    }

    private onChangesInputImage(changes: SimpleChanges): void {
        if (changes.imageChangedEvent || changes.imageURL || changes.imageBase64 || changes.imageFile) {
            this.reset();
        }
        if (changes.imageChangedEvent && this.isValidImageChangedEvent()) {
            const element = this.imageChangedEvent()?.currentTarget as HTMLInputElement;
            if (element.files) {
                this.loadImageFile(element.files[0]);
            }
        }
        const imageURL = this.imageURL();
        if (changes.imageURL && imageURL) {
            this.loadImageFromURL(imageURL);
        }
        const imageBase64 = this.imageBase64();
        if (changes.imageBase64 && imageBase64) {
            this.loadBase64Image(imageBase64);
        }
        const imageFile = this.imageFile();
        if (changes.imageFile && imageFile) {
            this.loadImageFile(imageFile);
        }
    }

    private isValidImageChangedEvent(): boolean {
        const event = this.imageChangedEvent();
        if (!event?.currentTarget) {
            return false;
        }
        const element = event.currentTarget as HTMLInputElement;
        return !!element.files?.length;
    }

    private setCssTransform() {
        // Defend against parents binding `[transform]="undefined"`, which overrides the input default.
        const transform = this.transform() ?? {};
        this.safeTransformStyle = this.sanitizer.bypassSecurityTrustStyle(
            'scaleX(' +
                (transform.scale || 1) * (transform.flipH ? -1 : 1) +
                ')' +
                'scaleY(' +
                (transform.scale || 1) * (transform.flipV ? -1 : 1) +
                ')' +
                'rotate(' +
                (transform.rotate || 0) +
                'deg)',
        );
    }

    private reset(): void {
        this.imageVisible = false;
        this.loadedImage = undefined;
        this.safeImgDataUrl = 'data:image/png;base64,iVBORw0KGg' + 'oAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVQYV2NgAAIAAAU' + 'AAarVyFEAAAAASUVORK5CYII=';
        this.moveStart = {
            active: false,
            type: undefined,
            position: undefined,
            x1: 0,
            y1: 0,
            x2: 0,
            y2: 0,
            clientX: 0,
            clientY: 0,
        };
        this.maxSize = {
            width: 0,
            height: 0,
        };
        Object.assign(this.cropper, defaultCropperPosition());
    }

    private loadImageFile(file: File): void {
        this.loadImageService
            .loadImageFile(file, this.settings)
            .then((res) => this.setLoadedImage(res))
            .catch((err) => this.loadImageError(err));
    }

    private loadBase64Image(imageBase64: string): void {
        this.loadImageService
            .loadBase64Image(imageBase64, this.settings)
            .then((res) => this.setLoadedImage(res))
            .catch((err) => this.loadImageError(err));
    }

    private loadImageFromURL(url: string): void {
        this.loadImageService
            .loadImageFromURL(url, this.settings)
            .then((res) => this.setLoadedImage(res))
            .catch((err) => this.loadImageError(err));
    }

    private setLoadedImage(loadedImage: LoadedImage): void {
        this.loadedImage = loadedImage;
        this.safeImgDataUrl = this.sanitizer.bypassSecurityTrustResourceUrl(loadedImage.transformed!.base64);
        this.changeDetector.markForCheck();
    }

    private loadImageError(error: Error): void {
        this.loadImageFailed.emit();
        captureException(error);
    }

    imageLoadedInView(): void {
        if (this.loadedImage != undefined) {
            this.imageLoaded.emit(this.loadedImage);
            this.setImageMaxSizeRetries = 0;
            setTimeout(() => this.checkImageMaxSizeRecursively());
        }
    }

    private checkImageMaxSizeRecursively(): void {
        if (this.setImageMaxSizeRetries > 40) {
            this.loadImageFailed.emit();
        } else if (this.sourceImageLoaded()) {
            this.setMaxSize();
            this.setCropperScaledMinSize();
            this.setCropperScaledMaxSize();
            this.resetCropperPosition();
            this.cropperReady.emit({ ...this.maxSize });
            this.changeDetector.markForCheck();
        } else {
            this.setImageMaxSizeRetries++;
            setTimeout(() => this.checkImageMaxSizeRecursively(), 50);
        }
    }

    private sourceImageLoaded(): boolean {
        const sourceImage = this.sourceImage();
        return (sourceImage?.nativeElement?.offsetWidth ?? 0) > 0;
    }

    onResize(): void {
        if (!this.loadedImage) {
            return;
        }
        this.resizeCropperPosition();
        this.setMaxSize();
        this.setCropperScaledMinSize();
        this.setCropperScaledMaxSize();
    }

    private resizeCropperPosition(): void {
        const sourceImage = this.sourceImage();
        if (!sourceImage) {
            return;
        }
        const sourceImageElement = sourceImage.nativeElement;
        if (this.maxSize.width !== sourceImageElement.offsetWidth || this.maxSize.height !== sourceImageElement.offsetHeight) {
            this.cropper.x1 = (this.cropper.x1 * sourceImageElement.offsetWidth) / this.maxSize.width;
            this.cropper.x2 = (this.cropper.x2 * sourceImageElement.offsetWidth) / this.maxSize.width;
            this.cropper.y1 = (this.cropper.y1 * sourceImageElement.offsetHeight) / this.maxSize.height;
            this.cropper.y2 = (this.cropper.y2 * sourceImageElement.offsetHeight) / this.maxSize.height;
        }
    }

    resetCropperPosition(): void {
        const sourceImage = this.sourceImage();
        if (sourceImage) {
            this.cropperPositionService.resetCropperPosition(sourceImage, this.cropper, this.settings);
        }
        this.doAutoCrop();
        this.imageVisible = true;
    }

    keyboardAccess(event: KeyboardEvent) {
        this.changeKeyboardStepSize(event);
        this.keyboardMoveCropper(event);
    }

    private changeKeyboardStepSize(event: KeyboardEvent): void {
        const key = +event.key;
        if (key >= 1 && key <= 9) {
            this.settings.stepSize = key;
        }
    }

    private keyboardMoveCropper(event: KeyboardEvent) {
        const keyboardWhiteList: string[] = ['ArrowUp', 'ArrowDown', 'ArrowRight', 'ArrowLeft'];
        if (!keyboardWhiteList.includes(event.key)) {
            return;
        }
        const moveType = event.shiftKey ? MoveTypes.Resize : MoveTypes.Move;
        const position = event.altKey ? getInvertedPositionForKey(event.key) : getPositionForKey(event.key);
        const moveEvent = getEventForKey(event.key, this.settings.stepSize);
        event.preventDefault();
        event.stopPropagation();
        this.startMove({ clientX: 0, clientY: 0 } as MouseEvent, moveType, position);
        this.moveImg(moveEvent);
        this.moveStop();
    }

    startMove(event: MouseEvent | TouchEvent, moveType: MoveTypes, position?: string): void {
        if (this.moveStart?.active && this.moveStart?.type === MoveTypes.Pinch) {
            return;
        }
        if (event.preventDefault) {
            event.preventDefault();
        }
        this.moveStart = {
            active: true,
            type: moveType,
            position,
            clientX: this.cropperPositionService.getClientX(event),
            clientY: this.cropperPositionService.getClientY(event),
            ...this.cropper,
        };
    }

    moveImg(event: MouseEvent | TouchEvent): void {
        if (this.moveStart.active) {
            if (event.stopPropagation) {
                event.stopPropagation();
            }
            if (event.preventDefault) {
                event.preventDefault();
            }
            if (this.moveStart.type === MoveTypes.Move) {
                this.cropperPositionService.move(event, this.moveStart, this.cropper);
                this.checkCropperPosition(true);
            } else if (this.moveStart.type === MoveTypes.Resize) {
                if (!this.cropperStaticWidth() && !this.cropperStaticHeight()) {
                    this.cropperPositionService.resize(event, this.moveStart, this.cropper, this.maxSize, this.settings);
                }
                this.checkCropperPosition(false);
            }
            this.changeDetector.detectChanges();
        }
    }

    private setMaxSize(): void {
        const sourceImage = this.sourceImage();
        if (sourceImage) {
            const sourceImageElement = sourceImage.nativeElement;
            this.maxSize.width = sourceImageElement.offsetWidth;
            this.maxSize.height = sourceImageElement.offsetHeight;
            this.marginLeft = this.sanitizer.bypassSecurityTrustStyle('calc(50% - ' + this.maxSize.width / 2 + 'px)');
        }
    }

    private setCropperScaledMinSize(): void {
        if (this.loadedImage?.transformed?.image) {
            this.setCropperScaledMinWidth();
            this.setCropperScaledMinHeight();
        } else {
            this.settings.cropperScaledMinWidth = 20;
            this.settings.cropperScaledMinHeight = 20;
        }
    }

    private setCropperScaledMinWidth(): void {
        const cropperMinWidth = this.cropperMinWidth();
        this.settings.cropperScaledMinWidth = cropperMinWidth > 0 ? Math.max(20, (cropperMinWidth / this.loadedImage!.transformed!.image.width) * this.maxSize.width) : 20;
    }

    private setCropperScaledMinHeight(): void {
        const cropperMinHeight = this.cropperMinHeight();
        if (this.maintainAspectRatio()) {
            this.settings.cropperScaledMinHeight = Math.max(20, this.settings.cropperScaledMinWidth / this.aspectRatio());
        } else if (cropperMinHeight > 0) {
            this.settings.cropperScaledMinHeight = Math.max(20, (cropperMinHeight / this.loadedImage!.transformed!.image.height) * this.maxSize.height);
        } else {
            this.settings.cropperScaledMinHeight = 20;
        }
    }

    private setCropperScaledMaxSize(): void {
        if (this.loadedImage?.transformed?.image) {
            const ratio = this.loadedImage.transformed.size.width / this.maxSize.width;
            const cropperMaxWidth = this.cropperMaxWidth();
            const cropperMaxHeight = this.cropperMaxHeight();
            this.settings.cropperScaledMaxWidth = cropperMaxWidth > 20 ? cropperMaxWidth / ratio : this.maxSize.width;
            this.settings.cropperScaledMaxHeight = cropperMaxHeight > 20 ? cropperMaxHeight / ratio : this.maxSize.height;
            if (this.maintainAspectRatio()) {
                const aspectRatio = this.aspectRatio();
                if (this.settings.cropperScaledMaxWidth > this.settings.cropperScaledMaxHeight * aspectRatio) {
                    this.settings.cropperScaledMaxWidth = this.settings.cropperScaledMaxHeight * aspectRatio;
                } else if (this.settings.cropperScaledMaxWidth < this.settings.cropperScaledMaxHeight * aspectRatio) {
                    this.settings.cropperScaledMaxHeight = this.settings.cropperScaledMaxWidth / aspectRatio;
                }
            }
        } else {
            this.settings.cropperScaledMaxWidth = this.maxSize.width;
            this.settings.cropperScaledMaxHeight = this.maxSize.height;
        }
    }

    private checkCropperPosition(maintainSize = false): void {
        if (this.cropper.x1 < 0) {
            this.cropper.x2 -= maintainSize ? this.cropper.x1 : 0;
            this.cropper.x1 = 0;
        }
        if (this.cropper.y1 < 0) {
            this.cropper.y2 -= maintainSize ? this.cropper.y1 : 0;
            this.cropper.y1 = 0;
        }
        if (this.cropper.x2 > this.maxSize.width) {
            this.cropper.x1 -= maintainSize ? this.cropper.x2 - this.maxSize.width : 0;
            this.cropper.x2 = this.maxSize.width;
        }
        if (this.cropper.y2 > this.maxSize.height) {
            this.cropper.y1 -= maintainSize ? this.cropper.y2 - this.maxSize.height : 0;
            this.cropper.y2 = this.maxSize.height;
        }
    }

    moveStop(): void {
        if (this.moveStart.active) {
            this.moveStart.active = false;
            this.doAutoCrop();
        }
    }

    private doAutoCrop(): void {
        if (this.autoCrop()) {
            this.crop();
        }
    }

    crop(): ImageCroppedEvent | undefined {
        if (this.loadedImage?.transformed?.image == undefined) {
            return undefined;
        }
        // Match the original behaviour: emit `startCropImage` whenever the loaded image is ready,
        // even if the source image element has not yet been attached to the DOM.
        this.startCropImage.emit();
        const sourceImage = this.sourceImage();
        if (!sourceImage) {
            return undefined;
        }
        const output = this.cropService.crop(sourceImage, this.loadedImage, this.cropper, this.settings);
        if (output != undefined) {
            this.imageCropped.emit(output);
        }
        return output;
    }
}
