import { Component, ElementRef, HostListener, effect, input, output, signal, viewChild } from '@angular/core';
import 'pdfjs-dist/build/pdf.worker';
import { ArtemisSharedModule } from 'app/shared/shared.module';

type NavigationDirection = 'next' | 'prev';

@Component({
    selector: 'jhi-pdf-preview-enlarged-canvas-component',
    templateUrl: './pdf-preview-enlarged-canvas.component.html',
    styleUrls: ['./pdf-preview-enlarged-canvas.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule],
})
export class PdfPreviewEnlargedCanvasComponent {
    enlargedCanvas = viewChild.required<ElementRef<HTMLCanvasElement>>('enlargedCanvas');

    readonly DEFAULT_ENLARGED_SLIDE_HEIGHT = 800;

    // Inputs
    pdfContainer = input.required<HTMLDivElement>();
    originalCanvas = input<HTMLCanvasElement>();
    totalPages = input<number>(0);

    // Signals
    currentPage = signal<number>(1);

    //Outputs
    isEnlargedViewOutput = output<boolean>();

    constructor() {
        effect(
            () => {
                this.displayEnlargedCanvas(this.originalCanvas()!);
            },
            { allowSignalWrites: true },
        );
    }

    /**
     * Handles navigation within the PDF viewer using keyboard arrow keys.
     * @param event - The keyboard event captured for navigation.
     */
    @HostListener('document:keydown', ['$event'])
    handleKeyboardEvents(event: KeyboardEvent) {
        if (event.key === 'ArrowRight' && this.currentPage() < this.totalPages()) {
            this.navigatePages('next');
        } else if (event.key === 'ArrowLeft' && this.currentPage() > 1) {
            this.navigatePages('prev');
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
     * Dynamically updates the canvas size within an enlarged view based on the viewport.
     */
    adjustCanvasSize = () => {
        const canvasElements = this.pdfContainer().querySelectorAll('.pdf-canvas-container canvas');
        if (this.currentPage() - 1 < canvasElements.length) {
            const canvas = canvasElements[this.currentPage() - 1] as HTMLCanvasElement;
            this.updateEnlargedCanvas(canvas);
        }
    };

    displayEnlargedCanvas(originalCanvas: HTMLCanvasElement) {
        this.currentPage.set(Number(originalCanvas.id));
        this.toggleBodyScroll(true);
        setTimeout(() => {
            this.updateEnlargedCanvas(originalCanvas);
        }, 500);
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
            const isVertical = originalCanvas.height > originalCanvas.width;
            this.adjustPdfContainerSize(isVertical);

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
        const containerWidth = this.pdfContainer().clientWidth;
        const containerHeight = this.pdfContainer().clientHeight;

        let scaleX, scaleY;

        if (originalCanvas.height > originalCanvas.width) {
            // Vertical slide
            const fixedHeight = this.DEFAULT_ENLARGED_SLIDE_HEIGHT;
            scaleY = fixedHeight / originalCanvas.height;
            scaleX = containerWidth / originalCanvas.width;
        } else {
            // Horizontal slide
            scaleX = containerWidth / originalCanvas.width;
            scaleY = containerHeight / originalCanvas.height;
        }

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
        const enlargedCanvas = this.enlargedCanvas().nativeElement;
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
        const enlargedCanvas = this.enlargedCanvas().nativeElement;
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
        const enlargedCanvas = this.enlargedCanvas().nativeElement;
        const containerWidth = this.pdfContainer().clientWidth;
        const containerHeight = this.pdfContainer().clientHeight;

        enlargedCanvas.style.position = 'absolute';
        enlargedCanvas.style.left = `${(containerWidth - enlargedCanvas.width) / 2}px`;
        enlargedCanvas.style.top = `${(containerHeight - enlargedCanvas.height) / 2}px`;
        enlargedCanvas.parentElement!.style.top = `${this.pdfContainer().scrollTop}px`;
    }

    /**
     * Adjusts the size of the PDF container based on whether the enlarged view is active or not.
     * If the enlarged view is active, the container's size is reduced to focus on the enlarged content.
     * If the enlarged view is closed, the container returns to its original size.
     *
     * @param isVertical A boolean flag indicating whether to enlarge or reset the container size.
     */
    adjustPdfContainerSize(isVertical: boolean): void {
        const pdfContainer = this.pdfContainer();
        if (isVertical) {
            pdfContainer.style.height = `${this.DEFAULT_ENLARGED_SLIDE_HEIGHT}px`;
        } else {
            pdfContainer.style.height = '60vh';
        }
    }

    /**
     * Toggles the ability to scroll through the PDF container.
     * @param disable A boolean flag indicating whether scrolling should be disabled (`true`) or enabled (`false`).
     */
    toggleBodyScroll(disable: boolean): void {
        this.pdfContainer().style.overflow = disable ? 'hidden' : 'auto';
    }

    /**
     * Closes the enlarged view of the PDF and re-enables scrolling in the PDF container.
     */
    closeEnlargedView(event: MouseEvent) {
        this.isEnlargedViewOutput.emit(false);
        this.adjustPdfContainerSize(false);
        this.toggleBodyScroll(false);
        event.stopPropagation();
    }

    /**
     * Closes the enlarged view if a click event occurs outside the actual canvas area but within the enlarged container.
     * @param event The mouse event captured, used to determine the location of the click.
     */
    closeIfOutside(event: MouseEvent): void {
        const target = event.target as HTMLElement;
        const enlargedCanvas = this.enlargedCanvas().nativeElement;

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
        const nextPageIndex = direction === 'next' ? this.currentPage() + 1 : this.currentPage() - 1;
        if (nextPageIndex > 0 && nextPageIndex <= this.totalPages()) {
            this.currentPage.set(nextPageIndex);
            const canvas = this.pdfContainer().querySelectorAll('.pdf-canvas-container canvas')[this.currentPage() - 1] as HTMLCanvasElement;
            this.updateEnlargedCanvas(canvas);
        }
    }
}
