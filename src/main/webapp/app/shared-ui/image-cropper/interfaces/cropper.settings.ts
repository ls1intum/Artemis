import { CropperOptions, OutputFormat } from './cropper-options.interface';
import { ImageTransform } from './image-transform.interface';
import { SimpleChanges } from '@angular/core';

export class CropperSettings implements CropperOptions {
    // From options
    format: OutputFormat = 'png';
    maintainAspectRatio = true;
    transform: ImageTransform = {};
    aspectRatio = 1;
    resizeToWidth = 0;
    resizeToHeight = 0;
    cropperMinWidth = 0;
    cropperMinHeight = 0;
    cropperMaxHeight = 0;
    cropperMaxWidth = 0;
    cropperStaticWidth = 0;
    cropperStaticHeight = 0;
    canvasRotation = 0;
    initialStepSize = 3;
    roundCropper = false;
    onlyScaleDown = false;
    imageQuality = 92;
    autoCrop = true;
    backgroundColor?: string = undefined;
    containWithinAspectRatio = false;
    hideResizeSquares = false;
    alignImage: 'left' | 'center' = 'center';

    // Internal
    cropperScaledMinWidth = 20;
    cropperScaledMinHeight = 20;
    cropperScaledMaxWidth = 20;
    cropperScaledMaxHeight = 20;
    stepSize = this.initialStepSize;

    /**
     * Updates the properties of the target object with the values from the source object.
     * This method only updates the properties that exist in both the source and target objects.
     *
     * @template T - A type extending CropperOptions, representing the shape of the objects being updated.
     * @param {Partial<T>} source - The source object containing the new property values. Only properties that are defined will be considered.
     * @param {T} target - The target object that will be updated with values from the source. It should be a complete object of type T.
     *
     * @remarks
     * This method is used to update an instance of an object with new values from a partial object,
     * such as updating configuration settings or applying changes. It ensures that only properties
     * common to both the source and target are updated, preserving other properties of the target.
     * After applying the new options, it validates the updated configuration to ensure
     * all constraints and rules are maintained.
     *
     * @example
     * const sourceOptions = { resizeToWidth: 200, imageQuality: 90 };
     * const cropperSettings = new CropperSettings();
     * updateProperties(sourceOptions, cropperSettings);
     * // cropperSettings.resizeToWidth is now 200
     * // cropperSettings.imageQuality is now 90
     */
    private updateProperties<T extends CropperOptions>(source: Partial<T>, target: Partial<T>) {
        Object.assign(target, source);
        this.validateOptions();
    }

    /**
     * Sets the cropper settings options by updating the current instance with new values.
     * This method applies new configuration settings from a partial `CropperOptions` object
     * to the existing instance, preserving any unspecified properties.
     *
     * @param {Partial<CropperOptions>} options - An object containing the new option values to be applied.
     * Only properties specified in this object will be updated.
     *
     * @returns {void} This method does not return a value.
     *
     * @remarks
     * This method is intended to be used for updating the cropper settings dynamically.
     *
     * @example
     * const newOptions = { format: 'jpeg', aspectRatio: 16/9 };
     * cropperSettings.setOptions(newOptions);
     * // cropperSettings.format is now 'jpeg'
     * // cropperSettings.aspectRatio is now 16/9
     * // Other settings remain unchanged.
     */
    setOptions(options: Partial<CropperOptions>): void {
        this.updateProperties(options, this);
    }

    /**
     * Updates the cropper settings using changes detected in Angular's `SimpleChanges`.
     * This method extracts the current values from the `SimpleChanges` object and applies
     * them to the current instance of cropper settings.
     *
     * @param {SimpleChanges} changes - An object containing the changes detected by Angular,
     * typically from an `ngOnChanges` lifecycle hook. Each change includes the previous and current values.
     *
     * @returns {void} This method does not return a value.
     *
     * @remarks
     * This method is particularly useful when using Angular's two-way data binding and input properties.
     * It efficiently updates the cropper settings based on changes to input properties, ensuring
     * that the cropper configuration remains consistent with the external state.
     *
     * @example
     * ngOnChanges(changes: SimpleChanges) {
     *   this.cropperSettings.setOptionsFromChanges(changes);
     * }
     * // Any changes to input properties bound to cropper settings will be applied automatically.
     */
    setOptionsFromChanges(changes: SimpleChanges): void {
        const entries = Object.entries(changes).map(([key, change]) => [key, change.currentValue]);
        const changedValues = Object.fromEntries(entries) as Partial<CropperOptions>;
        this.updateProperties(changedValues, this);
    }

    private validateOptions(): void {
        if (this.maintainAspectRatio && !this.aspectRatio) {
            throw new Error('`aspectRatio` should > 0 when `maintainAspectRatio` is enabled');
        }
    }
}
