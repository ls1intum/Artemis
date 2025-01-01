import { Component, ElementRef, OnChanges, SimpleChanges, inject, input, output, signal, viewChild } from '@angular/core';
import * as PDFJS from 'pdfjs-dist';
import 'pdfjs-dist/build/pdf.worker';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { PdfPreviewEnlargedCanvasComponent } from 'app/lecture/pdf-preview/pdf-preview-enlarged-canvas/pdf-preview-enlarged-canvas.component';
import { faEye, faEyeSlash } from '@fortawesome/free-solid-svg-icons';
import { PdfPreviewDateBoxComponent } from 'app/lecture/pdf-preview/pdf-preview-date-box/pdf-preview-date-box.component';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';

interface HiddenPage {
    pageIndex: number;
    date: dayjs.Dayjs;
}

interface HiddenPageMap {
    [pageIndex: number]: dayjs.Dayjs;
}

@Component({
    selector: 'jhi-pdf-preview-thumbnail-grid-component',
    templateUrl: './pdf-preview-thumbnail-grid.component.html',
    styleUrls: ['./pdf-preview-thumbnail-grid.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule, PdfPreviewEnlargedCanvasComponent, PdfPreviewDateBoxComponent],
})
export class PdfPreviewThumbnailGridComponent implements OnChanges {
    pdfContainer = viewChild.required<ElementRef<HTMLDivElement>>('pdfContainer');

    // Inputs
    course = input<Course>();
    currentPdfUrl = input<string>();
    appendFile = input<boolean>();
    hiddenPages = input<HiddenPageMap>();
    isAttachmentUnit = input<boolean>();

    // Signals
    isEnlargedView = signal<boolean>(false);
    totalPagesArray = signal<Set<number>>(new Set());
    loadedPages = signal<Set<number>>(new Set());
    selectedPages = signal<Set<number>>(new Set());
    originalCanvas = signal<HTMLCanvasElement | undefined>(undefined);
    newHiddenPages = signal<HiddenPageMap>(this.hiddenPages() || {});
    initialPageNumber = signal<number>(0);
    activeButtonIndex = signal<number | null>(null);
    isDateBoxOpen = signal<boolean>(false);

    // Outputs
    isPdfLoading = output<boolean>();
    totalPagesOutput = output<number>();
    selectedPagesOutput = output<Set<number>>();
    newHiddenPagesOutput = output<HiddenPageMap>();

    // Injected services
    private readonly alertService = inject(AlertService);

    protected readonly faEye = faEye;
    protected readonly faEyeSlash = faEyeSlash;

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['hiddenPages']) {
            this.newHiddenPages.set(this.hiddenPages()!);
        }
        if (changes['currentPdfUrl']) {
            this.loadPdf(this.currentPdfUrl()!, this.appendFile()!);
        }
    }

    /**
     * Loads or appends a PDF from a provided URL.
     * @param fileUrl The URL of the file to load or append.
     * @param append Whether the document should be appended to the existing one.
     * @returns A promise that resolves when the PDF is loaded.
     */
    async loadPdf(fileUrl: string, append: boolean): Promise<void> {
        this.pdfContainer()
            .nativeElement.querySelectorAll('.pdf-canvas-container')
            .forEach((canvas) => canvas.remove());
        this.totalPagesArray.set(new Set());
        this.isPdfLoading.emit(true);
        try {
            const loadingTask = PDFJS.getDocument(fileUrl);
            const pdf = await loadingTask.promise;
            this.totalPagesArray.set(new Set(Array.from({ length: pdf.numPages }, (_, i) => i + 1)));

            for (let i = 1; i <= pdf.numPages; i++) {
                const page = await pdf.getPage(i);
                const viewport = page.getViewport({ scale: 1 });
                const canvas = this.createCanvas(viewport);
                const context = canvas.getContext('2d')!;
                await page.render({ canvasContext: context, viewport }).promise;

                const container = this.pdfContainer().nativeElement.querySelector(`#pdf-page-${i}`);
                if (container) {
                    container.appendChild(canvas);
                    this.loadedPages().add(i);
                }
            }

            if (append) {
                this.scrollToBottom();
            }
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.totalPagesOutput.emit(this.totalPagesArray().size);
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
     * @returns A new HTMLCanvasElement configured for the PDF page.
     */
    private createCanvas(viewport: PDFJS.PageViewport): HTMLCanvasElement {
        const canvas = document.createElement('canvas');
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        canvas.style.display = 'block';
        canvas.style.width = '100%';
        canvas.style.height = '100%';
        return canvas;
    }

    /**
     * Toggles the visibility state of a page by adding or removing it from the hidden pages set.
     * @param pageIndex The index of the page whose visibility is being toggled.
     * @param event The event object triggered by the click action.
     */
    toggleVisibility(pageIndex: number, event: Event): void {
        /**if (this.hiddenPages()!.has(pageIndex)) {
         this.hiddenPages()!.delete(pageIndex);
         } else {
         this.hiddenPages()!.add(pageIndex);
         }
         this.newHiddenPagesOutput.emit(this.hiddenPages()!);
         event.stopPropagation();**/
        this.activeButtonIndex.set(pageIndex);
        const button = (event.target as HTMLElement).closest('button');
        if (button) {
            button.style.opacity = '1';
        }

        event.stopPropagation();
    }

    /**
     * Toggles the selection state of a page by adding or removing it from the selected pages set.
     * @param hiddenPage
     */
    onHiddenPageChange(hiddenPage: HiddenPage): void {
        const updatedHiddenPages = this.newHiddenPages();
        updatedHiddenPages[hiddenPage.pageIndex] = dayjs(hiddenPage.date);
        this.newHiddenPages.set(updatedHiddenPages);
        this.newHiddenPagesOutput.emit(updatedHiddenPages);
    }

    /**
     * Displays the selected PDF page in an enlarged view for detailed examination.
     * @param pageIndex - The index of PDF page to be enlarged.
     * */
    displayEnlargedCanvas(pageIndex: number): void {
        const canvas = this.pdfContainer().nativeElement.querySelector(`#pdf-page-${pageIndex} canvas`) as HTMLCanvasElement;
        this.originalCanvas.set(canvas!);
        this.isEnlargedView.set(true);
        this.initialPageNumber.set(pageIndex);
    }

    /**
     * Handles the date box open/close state
     */
    handleDateBoxOpen(isOpen: boolean): void {
        this.isDateBoxOpen.set(isOpen);
        if (!isOpen) {
            this.hideActionButton(this.activeButtonIndex()!);
        }
    }

    /**
     * Removes a page from the hidden pages and hides the associated action button.
     *
     * @param pageIndex - The index of the page to be made visible.
     */
    showPage(pageIndex: number): void {
        this.newHiddenPages.update((pages) => {
            delete pages[pageIndex];
            return pages;
        });
        this.hideActionButton(pageIndex);
    }

    /**
     * Hides the action button associated with a specified page by setting its opacity to 0.
     *
     * @param pageIndex - The index of the page whose action button should be hidden.
     */
    hideActionButton(pageIndex: number): void {
        const button = document.getElementById('hide-show-button-' + pageIndex);
        if (button) {
            button.style.opacity = '0';
        }
    }
}
