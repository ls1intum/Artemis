import { Component, ElementRef, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { Observable, of } from 'rxjs';
import { map, switchMap } from 'rxjs/operators';
import { faCloudArrowUp } from '@fortawesome/free-solid-svg-icons/faCloudArrowUp';
import { faArrowRightToBracket } from '@fortawesome/free-solid-svg-icons/faArrowRightToBracket';
import { faEdit } from '@fortawesome/free-solid-svg-icons/faEdit';
import { faExclamation } from '@fortawesome/free-solid-svg-icons/faExclamation';
import { faTrash } from '@fortawesome/free-solid-svg-icons/faTrash';

import { MathOcrService } from './math-ocr.service';
import { ExpressionChildInput, ExpressionResult } from './input';

/**
 * The accepted file types for the file picker. The list is made
 * up of common file types accepted by OpenCV's imread function,
 * which is the base of the OCR service used.
 * See https://docs.opencv.org/4.x/d4/da8/group__imgcodecs.html#ga288b8b3da0892bd651fce07b3bbd3a56
 */
const supportedFileTypes = new Set(['image/bmp', 'image/jpeg', 'image/jpg', 'image/jpe', 'image/jp2', 'image/png', 'image/webp', 'image/tif', 'image/tiff']);

type DragItemListShape = { readonly length: number; [index: number]: { type: string } };

/**
 * Check if the given drag items are valid.
 * @param items
 */
const itemsValid = (items?: DragItemListShape | null) => items?.length === 1 && supportedFileTypes.has(items[0].type);

@Component({
    selector: 'jhi-math-task-expression-editor-image-input',
    templateUrl: './image-input.component.html',
    styleUrl: './image-input.component.scss',
})
export class ImageInputComponent implements ExpressionChildInput<File>, OnInit {
    protected readonly faCloudArrowUp = faCloudArrowUp;
    protected readonly faArrowRightToBracket = faArrowRightToBracket;
    protected readonly acceptedFileTypes = Array.from(supportedFileTypes).join(',');
    protected readonly faEdit = faEdit;
    protected readonly faTrash = faTrash;
    protected readonly faExclamation = faExclamation;
    protected previewImageData: string | null;

    /**
     * Reference to the file input element.
     * Used to programmatically open the file picker.
     * @protected
     */
    @ViewChild('fileInput', { static: false })
    protected fileInput: ElementRef<HTMLInputElement>;

    /**
     * Whether the user is currently dragging a file
     * over the drop zone.
     * @protected
     */
    protected dragActive: boolean = false;

    /**
     * Whether the currently dragged file is valid.
     * @protected
     */
    protected dragValid: boolean = false;

    /**
     * The currently selected file, if any.
     * @protected
     */
    protected get selectedFile(): File | null {
        return this.value;
    }

    /**
     * Set the currently selected file.
     * @param file the file to set
     * @protected
     */
    protected set selectedFile(file: File | null) {
        this.value = file;
        this.updatePreviewImage(file);
        this.valueChange.emit(file);
    }

    @Input()
    value: File | null;

    @Output()
    valueChange = new EventEmitter<File | null>();

    @Input()
    disabled: boolean;

    @Input()
    exerciseId: number;

    constructor(private service: MathOcrService) {}

    ngOnInit() {
        this.updatePreviewImage(this.selectedFile);
    }

    /**
     * Get the expression from the image data
     */
    getExpression(): Observable<ExpressionResult> {
        return of(this.selectedFile).pipe(
            switchMap((file) => (file ? this.service.processImage(this.exerciseId, file) : of(null))),
            map((response) => ({
                expression: response?.body?.text ?? null,
                error: response?.body?.error ?? null,
            })),
        );
    }

    private updatePreviewImage(file: File | null) {
        this.previewImageData = null;
        if (!file) return;

        const reader = new FileReader();
        reader.onload = () => {
            this.previewImageData = reader.result as string;
        };
        reader.readAsDataURL(file);
    }

    /**
     * Handles file changes.
     * @param file
     * @private
     */
    private handleFile(file: File) {
        this.selectedFile = file;
    }

    /**
     * Handles file picker click event.
     * @protected
     */
    protected onOpenFilePicker() {
        this.fileInput.nativeElement.click();
    }

    /**
     * Handles the remove file button click event.
     * @protected
     */
    protected onRemoveFile() {
        this.selectedFile = null;
        this.fileInput.nativeElement.value = '';
    }

    // file input

    /**
     * Handle the file input change event.
     * @param event
     * @protected
     */
    protected onFileInput(event: Event) {
        if (!(event.target instanceof HTMLInputElement)) {
            return;
        }

        if (!itemsValid(event.target.files)) {
            return;
        }

        this.handleFile(event.target.files![0]);
    }

    // drag and Drop

    /**
     * Handle the drop event.
     * @param event
     * @protected
     */
    protected onDrop(event: DragEvent) {
        event.preventDefault();
        event.stopPropagation();

        this.dragActive = false;
        this.dragValid = false;

        if (!itemsValid(event.dataTransfer?.items)) {
            return;
        }

        this.handleFile(event.dataTransfer!.files[0]);
    }

    /**
     * Handle the drag over event.
     * @param event
     * @protected
     */
    protected onDragOver(event: DragEvent) {
        event.preventDefault();
        event.stopPropagation();

        this.dragActive = true;
        this.dragValid = itemsValid(event.dataTransfer?.items);
    }

    /**
     * Handle the drag leave event.
     * @param event
     * @protected
     */
    protected onDragLeave(event: DragEvent) {
        event.preventDefault();
        event.stopPropagation();

        this.dragActive = false;
        this.dragValid = false;
    }
}
