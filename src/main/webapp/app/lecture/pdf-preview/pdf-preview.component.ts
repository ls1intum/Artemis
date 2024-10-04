import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AttachmentService } from 'app/lecture/attachment.service';
import * as PDFJS from 'pdfjs-dist';
import 'pdfjs-dist/build/pdf.worker';
import { Attachment } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { Subject, Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { HttpErrorResponse } from '@angular/common/http';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { faFileImport, faSave, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { PDFDocument } from 'pdf-lib';
import dayjs from 'dayjs/esm';
import { objectToJsonBlob } from 'app/utils/blob-util';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';

type NavigationDirection = 'next' | 'prev';

@Component({
    selector: 'jhi-pdf-preview-component',
    templateUrl: './pdf-preview.component.html',
    styleUrls: ['./pdf-preview.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule],
})
export class PdfPreviewComponent implements OnInit, OnDestroy {
    @ViewChild('pdfContainer', { static: true }) pdfContainer: ElementRef<HTMLDivElement>;
    @ViewChild('enlargedCanvas') enlargedCanvas: ElementRef<HTMLCanvasElement>;
    @ViewChild('fileInput', { static: false }) fileInput: ElementRef<HTMLInputElement>;

    readonly DEFAULT_SLIDE_WIDTH = 250;
    course?: Course;
    attachment?: Attachment;
    attachmentUnit?: AttachmentUnit;
    isEnlargedView = false;
    currentPage = 1;
    totalPages = 0;
    attachmentSub: Subscription;
    attachmentUnitSub: Subscription;
    selectedPages: Set<number> = new Set();
    isPdfLoading = false;
    attachmentToBeEdited?: Attachment;
    currentPdfBlob: Blob | null = null;

    dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faFileImport = faFileImport;
    faSave = faSave;
    faTimes = faTimes;
    faTrash = faTrash;

    constructor(
        public route: ActivatedRoute,
        private attachmentService: AttachmentService,
        private attachmentUnitService: AttachmentUnitService,
        private alertService: AlertService,
        private router: Router,
    ) {}

    ngOnInit() {
        this.route.data.subscribe((data) => {
            this.course = data.course;

            if ('attachment' in data) {
                this.attachment = data.attachment;
                this.attachmentSub = this.attachmentService.getAttachmentFile(this.course!.id!, this.attachment!.id!).subscribe({
                    next: (blob: Blob) => this.handleBlob(blob),
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
            } else if ('attachmentUnit' in data) {
                this.attachmentUnit = data.attachmentUnit;
                this.attachmentUnitSub = this.attachmentUnitService.getAttachmentFile(this.course!.id!, this.attachmentUnit!.id!).subscribe({
                    next: (blob: Blob) => this.handleBlob(blob),
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
            }
        });
    }

    handleBlob(blob: Blob): void {
        this.currentPdfBlob = blob;
        const objectUrl = URL.createObjectURL(blob);
        this.loadOrAppendPdf(objectUrl).then(() => URL.revokeObjectURL(objectUrl));
    }

    ngOnDestroy() {
        this.attachmentSub?.unsubscribe();
        this.attachmentUnitSub?.unsubscribe();
    }

    /**
     * Handles navigation within the PDF viewer using keyboard arrow keys.
     * @param event - The keyboard event captured for navigation.
     */
    @HostListener('document:keydown', ['$event'])
    handleKeyboardEvents(event: KeyboardEvent) {
        if (this.isEnlargedView) {
            if (event.key === 'ArrowRight' && this.currentPage < this.totalPages) {
                this.navigatePages('next');
            } else if (event.key === 'ArrowLeft' && this.currentPage > 1) {
                this.navigatePages('prev');
            }
        }
    }

    /**
     * Adjusts the canvas size based on the window resize event to ensure proper display.
     */
    @HostListener('window:resize')
    resizeCanvasBasedOnContainer() {
        this.adjustCanvasSize();
    }

    /**
     * Loads or appends a PDF from a provided URL.
     * @param fileUrl The URL of the file to load or append.
     * @param append Whether the document should be appended to the existing one.
     * @returns A promise that resolves when the PDF is loaded.
     */
    async loadOrAppendPdf(fileUrl: string, append = false): Promise<void> {
        this.pdfContainer.nativeElement.querySelectorAll('.pdf-canvas-container').forEach((canvas) => canvas.remove());
        this.totalPages = 0;
        this.isPdfLoading = true;
        try {
            const loadingTask = PDFJS.getDocument(fileUrl);
            const pdf = await loadingTask.promise;
            this.totalPages = pdf.numPages;

            for (let i = 1; i <= this.totalPages; i++) {
                const page = await pdf.getPage(i);
                const viewport = page.getViewport({ scale: 2 });
                const canvas = this.createCanvas(viewport, i);
                const context = canvas.getContext('2d');
                await page.render({ canvasContext: context!, viewport }).promise;

                const canvasContainer = this.createCanvasContainer(canvas, i);
                this.pdfContainer.nativeElement.appendChild(canvasContainer);
            }

            if (append) {
                this.scrollToBottom();
            }
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isPdfLoading = false;
            if (append) {
                this.fileInput.nativeElement.value = '';
            }
        }
    }

    /**
     * Scrolls the PDF container to the bottom after appending new pages.
     */
    scrollToBottom(): void {
        const scrollOptions: ScrollToOptions = {
            top: this.pdfContainer.nativeElement.scrollHeight,
            left: 0,
            behavior: 'smooth' as ScrollBehavior,
        };
        this.pdfContainer.nativeElement.scrollTo(scrollOptions);
    }

    /**
     * Creates a canvas for each page of the PDF to allow for individual page rendering.
     * @param viewport The viewport settings used for rendering the page.
     * @param pageIndex The index of the page within the PDF document.
     * @returns A new HTMLCanvasElement configured for the PDF page.
     */
    createCanvas(viewport: PDFJS.PageViewport, pageIndex: number): HTMLCanvasElement {
        const canvas = document.createElement('canvas');
        canvas.id = `${pageIndex}`;
        /* Canvas styling is predefined because Canvas tags do not support CSS classes
         * as they are not HTML elements but rather a bitmap drawing surface.
         * See: https://stackoverflow.com/a/29675448
         * */
        canvas.height = viewport.height;
        canvas.width = viewport.width;
        const fixedWidth = this.DEFAULT_SLIDE_WIDTH;
        const scaleFactor = fixedWidth / viewport.width;
        canvas.style.width = `${fixedWidth}px`;
        canvas.style.height = `${viewport.height * scaleFactor}px`;
        return canvas;
    }

    /**
     * Creates a container div for each canvas, facilitating layering and interaction.
     * @param canvas The canvas element that displays a PDF page.
     * @param pageIndex The index of the page within the PDF document.
     * @returns A configured div element that includes the canvas and interactive overlays.
     */
    createCanvasContainer(canvas: HTMLCanvasElement, pageIndex: number): HTMLDivElement {
        const container = document.createElement('div');
        /* Dynamically created elements are not detected by DOM, that is why we need to set the styles manually.
         * See: https://stackoverflow.com/a/70911189
         */
        container.id = `pdf-page-${pageIndex}`;
        container.classList.add('pdf-canvas-container');
        container.style.cssText = `position: relative; display: inline-block; width: ${canvas.style.width}; height: ${canvas.style.height}; margin: 20px; box-shadow: 0 2px 6px var(--pdf-preview-canvas-shadow);`;

        const overlay = this.createOverlay(pageIndex);
        const checkbox = this.createCheckbox(pageIndex);
        container.appendChild(canvas);
        container.appendChild(overlay);
        container.appendChild(checkbox);

        container.addEventListener('mouseenter', () => {
            overlay.style.opacity = '1';
        });
        container.addEventListener('mouseleave', () => {
            overlay.style.opacity = '0';
        });
        overlay.addEventListener('click', () => this.displayEnlargedCanvas(canvas));

        return container;
    }

    /**
     * Generates an interactive overlay for each PDF page to allow for user interactions.
     * @param pageIndex The index of the page.
     * @returns A div element styled as an overlay.
     */
    private createOverlay(pageIndex: number): HTMLDivElement {
        const overlay = document.createElement('div');
        overlay.innerHTML = `<span>${pageIndex}</span>`;
        /* Dynamically created elements are not detected by DOM, that is why we need to set the styles manually.
         * See: https://stackoverflow.com/a/70911189
         */
        overlay.style.cssText = `position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex; justify-content: center; align-items: center; font-size: 24px; color: white; z-index: 1; transition: opacity 0.3s ease; opacity: 0; cursor: pointer; background-color: var(--pdf-preview-container-overlay)`;
        return overlay;
    }

    private createCheckbox(pageIndex: number): HTMLDivElement {
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.id = String(pageIndex);
        checkbox.style.cssText = `position: absolute; top: -5px; right: -5px; z-index: 4;`;
        checkbox.checked = this.selectedPages.has(pageIndex);
        checkbox.addEventListener('change', () => {
            if (checkbox.checked) {
                this.selectedPages.add(Number(checkbox.id));
            } else {
                this.selectedPages.delete(Number(checkbox.id));
            }
        });
        return checkbox;
    }

    /**
     * Dynamically updates the canvas size within an enlarged view based on the viewport.
     */
    adjustCanvasSize = () => {
        if (this.isEnlargedView) {
            const canvasElements = this.pdfContainer.nativeElement.querySelectorAll('.pdf-canvas-container canvas');
            if (this.currentPage - 1 < canvasElements.length) {
                const canvas = canvasElements[this.currentPage - 1] as HTMLCanvasElement;
                this.updateEnlargedCanvas(canvas);
            }
        }
    };

    /**
     * Displays the selected PDF page in an enlarged view for detailed examination.
     * @param originalCanvas - The original canvas element of the PDF page to be enlarged.
     * */
    displayEnlargedCanvas(originalCanvas: HTMLCanvasElement) {
        this.isEnlargedView = true;
        this.currentPage = Number(originalCanvas.id);
        this.updateEnlargedCanvas(originalCanvas);
        this.toggleBodyScroll(true);
    }
    /**
     * Updates the enlarged canvas dimensions to optimize PDF page display within the current viewport.
     * This method dynamically adjusts the size, position, and scale of the canvas to maintain the aspect ratio,
     * ensuring the content is centered and displayed appropriately within the available space.
     * It is called within an animation frame to synchronize updates with the browser's render cycle for smooth visuals.
     *
     * @param originalCanvas - The source canvas element used to extract image data for resizing and redrawing.
     */
    updateEnlargedCanvas(originalCanvas: HTMLCanvasElement) {
        requestAnimationFrame(() => {
            if (!this.isEnlargedView) return;

            const scaleFactor = this.calculateScaleFactor(originalCanvas);
            this.resizeCanvas(originalCanvas, scaleFactor);
            this.redrawCanvas(originalCanvas);
            this.positionCanvas();
        });
    }

    /**
     * Calculates the scaling factor to adjust the canvas size based on the dimensions of the container.
     * This method ensures that the canvas is scaled to fit within the container without altering the aspect ratio.
     *
     * @param originalCanvas - The original canvas element representing the PDF page.
     * @returns The scaling factor used to resize the original canvas to fit within the container dimensions.
     */
    calculateScaleFactor(originalCanvas: HTMLCanvasElement): number {
        const containerWidth = this.pdfContainer.nativeElement.clientWidth;
        const containerHeight = this.pdfContainer.nativeElement.clientHeight;
        const scaleX = containerWidth / originalCanvas.width;
        const scaleY = containerHeight / originalCanvas.height;
        return Math.min(scaleX, scaleY);
    }

    /**
     * Resizes the canvas according to the computed scale factor.
     * This method updates the dimensions of the enlarged canvas element to ensure that the entire PDF page
     * is visible and properly scaled within the viewer.
     *
     * @param originalCanvas - The canvas element from which the image is scaled.
     * @param scaleFactor - The factor by which the canvas is resized.
     */
    resizeCanvas(originalCanvas: HTMLCanvasElement, scaleFactor: number): void {
        const enlargedCanvas = this.enlargedCanvas.nativeElement;
        enlargedCanvas.width = originalCanvas.width * scaleFactor;
        enlargedCanvas.height = originalCanvas.height * scaleFactor;
    }

    /**
     * Redraws the original canvas content onto the enlarged canvas at the updated scale.
     * This method ensures that the image is rendered clearly and correctly positioned on the enlarged canvas.
     *
     * @param originalCanvas - The original canvas containing the image to be redrawn.
     */
    redrawCanvas(originalCanvas: HTMLCanvasElement): void {
        const enlargedCanvas = this.enlargedCanvas.nativeElement;
        const context = enlargedCanvas.getContext('2d');
        context!.clearRect(0, 0, enlargedCanvas.width, enlargedCanvas.height);
        context!.drawImage(originalCanvas, 0, 0, enlargedCanvas.width, enlargedCanvas.height);
    }

    /**
     * Adjusts the position of the enlarged canvas to center it within the viewport of the PDF container.
     * This method ensures that the canvas is both vertically and horizontally centered, providing a consistent
     * and visually appealing layout.
     */
    positionCanvas(): void {
        const enlargedCanvas = this.enlargedCanvas.nativeElement;
        const containerWidth = this.pdfContainer.nativeElement.clientWidth;
        const containerHeight = this.pdfContainer.nativeElement.clientHeight;

        enlargedCanvas.style.position = 'absolute';
        enlargedCanvas.style.left = `${(containerWidth - enlargedCanvas.width) / 2}px`;
        enlargedCanvas.style.top = `${(containerHeight - enlargedCanvas.height) / 2}px`;
        enlargedCanvas.parentElement!.style.top = `${this.pdfContainer.nativeElement.scrollTop}px`;
    }

    /**
     * Closes the enlarged view of the PDF and re-enables scrolling in the PDF container.
     */
    closeEnlargedView(event: MouseEvent) {
        this.isEnlargedView = false;
        this.toggleBodyScroll(false);
        event.stopPropagation();
    }

    /**
     * Toggles the ability to scroll through the PDF container.
     * @param disable A boolean flag indicating whether scrolling should be disabled (`true`) or enabled (`false`).
     */
    toggleBodyScroll(disable: boolean): void {
        this.pdfContainer.nativeElement.style.overflow = disable ? 'hidden' : 'auto';
    }

    /**
     * Closes the enlarged view if a click event occurs outside the actual canvas area but within the enlarged container.
     * @param event The mouse event captured, used to determine the location of the click.
     */
    closeIfOutside(event: MouseEvent): void {
        const target = event.target as HTMLElement;
        const enlargedCanvas = this.enlargedCanvas.nativeElement;

        if (target.classList.contains('enlarged-container') && target !== enlargedCanvas) {
            this.closeEnlargedView(event);
        }
    }

    /**
     * Handles navigation between PDF pages and stops event propagation to prevent unwanted side effects.
     * @param direction The direction to navigate.
     * @param event The MouseEvent to be stopped.
     */
    handleNavigation(direction: NavigationDirection, event: MouseEvent): void {
        event.stopPropagation();
        this.navigatePages(direction);
    }

    /**
     * Navigates to a specific page in the PDF based on the direction relative to the current page.
     * @param direction The navigation direction (next or previous).
     */
    navigatePages(direction: NavigationDirection) {
        const nextPageIndex = direction === 'next' ? this.currentPage + 1 : this.currentPage - 1;
        if (nextPageIndex > 0 && nextPageIndex <= this.totalPages) {
            this.currentPage = nextPageIndex;
            const canvas = this.pdfContainer.nativeElement.querySelectorAll('.pdf-canvas-container canvas')[this.currentPage - 1] as HTMLCanvasElement;
            this.updateEnlargedCanvas(canvas);
        }
    }

    /**
     * Deletes selected slides from the PDF viewer.
     */
    async deleteSelectedSlides() {
        this.isPdfLoading = true;
        try {
            const existingPdfBytes = await this.currentPdfBlob!.arrayBuffer();
            const pdfDoc = await PDFDocument.load(existingPdfBytes);

            const pagesToDelete = Array.from(this.selectedPages)
                .map((page) => page - 1)
                .sort((a, b) => b - a);
            pagesToDelete.forEach((pageIndex) => {
                pdfDoc.removePage(pageIndex);
            });

            const pdfBytes = await pdfDoc.save();
            this.currentPdfBlob = new Blob([pdfBytes], { type: 'application/pdf' });
            this.selectedPages.clear();

            const objectUrl = URL.createObjectURL(this.currentPdfBlob!);
            await this.loadOrAppendPdf(objectUrl, false).then(() => {
                this.dialogErrorSource.next('');
            });
            URL.revokeObjectURL(objectUrl);
        } catch (error) {
            this.alertService.error('artemisApp.attachment.pdfPreview.pageDeleteError', { error: error.message });
        } finally {
            this.isPdfLoading = false;
        }
    }

    /**
     * Triggers the file input to select files.
     */
    triggerFileInput(): void {
        this.fileInput.nativeElement.click();
    }

    /**
     * Adds a selected PDF file at the end of the current PDF document.
     * @param event - The event containing the file input.
     */
    async mergePDF(event: Event): Promise<void> {
        const file = (event.target as HTMLInputElement).files?.[0];

        this.isPdfLoading = true;
        try {
            const newPdfBytes = await file!.arrayBuffer();
            const existingPdfBytes = await this.currentPdfBlob!.arrayBuffer();
            const existingPdfDoc = await PDFDocument.load(existingPdfBytes);
            const newPdfDoc = await PDFDocument.load(newPdfBytes);

            const copiedPages = await existingPdfDoc.copyPages(newPdfDoc, newPdfDoc.getPageIndices());
            copiedPages.forEach((page) => existingPdfDoc.addPage(page));

            const mergedPdfBytes = await existingPdfDoc.save();
            this.currentPdfBlob = new Blob([mergedPdfBytes], { type: 'application/pdf' });

            this.selectedPages.clear();

            const objectUrl = URL.createObjectURL(this.currentPdfBlob!);
            await this.loadOrAppendPdf(objectUrl, true).then(() => URL.revokeObjectURL(objectUrl));
        } catch (error) {
            this.alertService.error('artemisApp.attachment.pdfPreview.mergeFailedError', { error: error.message });
        } finally {
            this.isPdfLoading = false;
        }
    }

    /**
     * Updates the IDs of remaining pages after some have been removed.
     */
    updatePageIDs() {
        const remainingPages = this.pdfContainer.nativeElement.querySelectorAll('.pdf-canvas-container');
        remainingPages.forEach((container, index) => {
            const pageIndex = index + 1;
            container.id = `pdf-page-${pageIndex}`;
            const canvas = container.querySelector('canvas');
            const overlay = container.querySelector('div');
            const checkbox = container.querySelector('input[type="checkbox"]');
            canvas!.id = String(pageIndex);
            overlay!.innerHTML = `<span>${pageIndex}</span>`;
            checkbox!.id = String(pageIndex);
        });
    }

    updateAttachmentWithFile(): void {
        const pdfFile = new File([this.currentPdfBlob!], 'updatedAttachment.pdf', { type: 'application/pdf' });

        if (pdfFile.size > MAX_FILE_SIZE) {
            this.alertService.error('artemisApp.attachment.pdfPreview.fileSizeError');
            return;
        }

        if (this.attachment) {
            this.attachmentToBeEdited = this.attachment;
            this.attachmentToBeEdited!.version!++;
            this.attachmentToBeEdited.uploadDate = dayjs();

            this.attachmentService.update(this.attachmentToBeEdited!.id!, this.attachmentToBeEdited, pdfFile).subscribe({
                next: () => {
                    this.alertService.success('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
                    this.router.navigate(['course-management', this.course?.id, 'lectures', this.attachment!.lecture!.id, 'attachments']);
                },
                error: (error) => {
                    this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                },
            });
        } else if (this.attachmentUnit) {
            this.attachmentToBeEdited = this.attachmentUnit.attachment!;
            this.attachmentToBeEdited!.version!++;
            this.attachmentToBeEdited!.uploadDate = dayjs();

            const formData = new FormData();
            formData.append('file', pdfFile);
            formData.append('attachment', objectToJsonBlob(this.attachmentToBeEdited));
            formData.append('attachmentUnit', objectToJsonBlob(this.attachmentUnit));

            this.attachmentUnitService.update(this.attachmentUnit!.lecture!.id!, this.attachmentUnit!.id!, formData).subscribe({
                next: () => {
                    this.alertService.success('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
                    this.router.navigate(['course-management', this.course?.id, 'lectures', this.attachmentUnit!.lecture!.id, 'unit-management']);
                },
                error: (error) => {
                    this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                },
            });
        }
    }
}
