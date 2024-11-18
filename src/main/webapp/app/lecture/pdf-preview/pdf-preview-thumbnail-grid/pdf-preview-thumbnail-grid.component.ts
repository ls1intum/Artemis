import { Component, ElementRef, OnChanges, SimpleChanges, effect, inject, input, output, signal, viewChild } from '@angular/core';
import * as PDFJS from 'pdfjs-dist';
import 'pdfjs-dist/build/pdf.worker';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { PdfPreviewEnlargedCanvasComponent } from 'app/lecture/pdf-preview/pdf-preview-enlarged-canvas/pdf-preview-enlarged-canvas.component';
import { clone } from 'lodash-es';

@Component({
    selector: 'jhi-pdf-preview-thumbnail-grid-component',
    templateUrl: './pdf-preview-thumbnail-grid.component.html',
    styleUrls: ['./pdf-preview-thumbnail-grid.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule, PdfPreviewEnlargedCanvasComponent],
})
export class PdfPreviewThumbnailGridComponent implements OnChanges {
    pdfContainer = viewChild.required<ElementRef<HTMLDivElement>>('pdfContainer');

    readonly DEFAULT_SLIDE_WIDTH = 250;

    // Inputs
    currentPdfUrl = input<string>();
    appendFile = input<boolean>();
    hiddenPages = input<Set<number>>();
    isAttachmentUnit = input<boolean>();

    // Signals
    isEnlargedView = signal<boolean>(false);
    totalPages = signal<number>(0);
    selectedPages = signal<Set<number>>(new Set());
    originalCanvas = signal<HTMLCanvasElement | undefined>(undefined);
    newHiddenPages = signal(new Set<number>(this.hiddenPages()!));

    // Outputs
    isPdfLoading = output<boolean>();
    totalPagesOutput = output<number>();
    selectedPagesOutput = output<Set<number>>();
    newHiddenPagesOutput = output<Set<number>>();
    hiddenPagesChangedOutput = output<boolean>();

    // Injected services
    private readonly alertService = inject(AlertService);

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['hiddenPages']) {
            this.newHiddenPages.set(clone(this.hiddenPages()!));
        }
    }

    constructor() {
        effect(
            () => {
                this.loadOrAppendPdf(this.currentPdfUrl()!, this.appendFile());
            },
            { allowSignalWrites: true },
        );
    }

    /**
     * Loads or appends a PDF from a provided URL.
     * @param fileUrl The URL of the file to load or append.
     * @param append Whether the document should be appended to the existing one.
     * @returns A promise that resolves when the PDF is loaded.
     */
    async loadOrAppendPdf(fileUrl: string, append = false): Promise<void> {
        this.pdfContainer()
            .nativeElement.querySelectorAll('.pdf-canvas-container')
            .forEach((canvas) => canvas.remove());
        this.totalPages.set(0);
        this.isPdfLoading.emit(true);
        try {
            const loadingTask = PDFJS.getDocument(fileUrl);
            const pdf = await loadingTask.promise;
            this.totalPages.set(pdf.numPages);

            for (let i = 1; i <= this.totalPages(); i++) {
                const page = await pdf.getPage(i);
                const viewport = page.getViewport({ scale: 2 });
                const canvas = this.createCanvas(viewport, i);
                const context = canvas.getContext('2d');
                await page.render({ canvasContext: context!, viewport }).promise;

                const canvasContainer = this.createCanvasContainer(canvas, i);
                this.pdfContainer().nativeElement.appendChild(canvasContainer);
            }

            if (append) {
                this.scrollToBottom();
            }
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.totalPagesOutput.emit(this.totalPages());
            this.isPdfLoading.emit(false);
        }
    }

    /**
     * Scrolls the PDF container to the bottom after appending new pages.
     */
    scrollToBottom(): void {
        const scrollOptions: ScrollToOptions = {
            top: this.pdfContainer().nativeElement.scrollHeight,
            left: 0,
            behavior: 'smooth' as ScrollBehavior,
        };
        this.pdfContainer().nativeElement.scrollTo(scrollOptions);
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

        if (this.isAttachmentUnit()) {
            const hideShowButton = this.createHideShowButton(pageIndex, container);
            container.appendChild(hideShowButton);
            container.addEventListener('mouseenter', () => {
                hideShowButton.style.opacity = '1';
            });
            container.addEventListener('mouseleave', () => {
                hideShowButton.style.opacity = '0';
            });
            hideShowButton.addEventListener('click', () => this.toggleVisibility(pageIndex, container, hideShowButton));
        }
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
        overlay.style.cssText = `position: absolute; top: 0; left: 0; width: 100%; height: 100%; display: flex; justify-content: center; align-items: center; font-size: 24px; color: white; z-index: 2; transition: opacity 0.3s ease; opacity: 0; cursor: pointer; background-color: var(--pdf-preview-container-overlay)`;
        return overlay;
    }

    /**
     * Generates a checkbox for each PDF page to allow for selection of pages.
     * @param pageIndex The index of the page.
     * @returns An input element styled as a checkbox.
     */
    private createCheckbox(pageIndex: number): HTMLDivElement {
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.id = String(pageIndex);
        checkbox.style.cssText = `position: absolute; top: -5px; right: -5px; z-index: 4;`;
        checkbox.checked = this.selectedPages().has(pageIndex);
        checkbox.addEventListener('change', () => {
            if (checkbox.checked) {
                this.selectedPages().add(Number(checkbox.id));
                this.selectedPagesOutput.emit(this.selectedPages());
            } else {
                this.selectedPages().delete(Number(checkbox.id));
                this.selectedPagesOutput.emit(this.selectedPages());
            }
        });
        return checkbox;
    }

    private createHideShowButton(pageIndex: number, container: HTMLDivElement): HTMLAnchorElement {
        const hideShowButton = document.createElement('a');
        hideShowButton.type = 'button';
        hideShowButton.style.cssText = `opacity: 0; position: absolute; bottom: -20px; left: 50%; transform: translateX(-50%); cursor: pointer; z-index: 4;`;

        if (this.hiddenPages()!.has(pageIndex)) {
            hideShowButton.className = 'btn btn-success';
            hideShowButton.id = `show-button-${pageIndex}`;
            hideShowButton.innerHTML = `<i class="fa fa-eye"></i>`;

            const overlay = document.createElement('div');
            overlay.id = 'pdf-page-overlay';
            overlay.style.cssText = `position: absolute; top: 0; width: 100%; height: 100%; z-index: 1; transition: opacity 0.3s ease; background-color: var(--pdf-preview-container-overlay)`;

            const diagonalLine = document.createElement('div');
            diagonalLine.style.cssText = `position: absolute; top: 0; left: 0; width: 115%; height: 2px; background-color: white; transform: rotate(29deg); transform-origin: top left;`;
            overlay.appendChild(diagonalLine);

            container.appendChild(overlay);
        } else {
            hideShowButton.className = 'btn btn-secondary';
            hideShowButton.id = `hide-button-${pageIndex}`;
            hideShowButton.innerHTML = `<i class="fa fa-eye-slash"></i>`;
        }

        return hideShowButton;
    }

    private toggleVisibility(pageIndex: number, container: HTMLDivElement, hideShowButton: HTMLAnchorElement): void {
        const overlay = document.createElement('div');
        overlay.id = 'pdf-page-overlay';
        overlay.style.cssText = `position: absolute; top: 0; width: 100%; height: 100%; z-index: 1; transition: opacity 0.3s ease; background-color: var(--pdf-preview-container-overlay)`;

        const diagonalLine = document.createElement('div');
        diagonalLine.style.cssText = `position: absolute; top: 0; left: 0; width: 115%; height: 2px; background-color: white; transform: rotate(29deg); transform-origin: top left;`;
        overlay.appendChild(diagonalLine);

        if (hideShowButton.className === 'btn btn-secondary') {
            container.appendChild(overlay);
            hideShowButton.className = 'btn btn-success';
            hideShowButton.children[0].className = 'fa fa-eye';
            hideShowButton.id = `show-button-${pageIndex}`;
            this.newHiddenPages().add(pageIndex);
        } else {
            container.removeChild(container.querySelector(`#pdf-page-overlay`)!);
            hideShowButton.className = 'btn btn-secondary';
            hideShowButton.children[0].className = 'fa fa-eye-slash';
            hideShowButton.id = `hide-button-${pageIndex}`;
            this.newHiddenPages().delete(Number(pageIndex));
        }
        this.newHiddenPagesOutput.emit(this.newHiddenPages());
    }

    /**
     * Displays the selected PDF page in an enlarged view for detailed examination.
     * @param originalCanvas - The original canvas element of the PDF page to be enlarged.
     * */
    displayEnlargedCanvas(originalCanvas: HTMLCanvasElement) {
        this.originalCanvas.set(originalCanvas);
        this.isEnlargedView.set(true);
    }
}
