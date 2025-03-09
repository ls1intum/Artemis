import { Component, ElementRef, OnChanges, SimpleChanges, inject, input, output, signal, viewChild } from '@angular/core';
import * as PDFJS from 'pdfjs-dist';
import 'pdfjs-dist/build/pdf.worker';

import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { PdfPreviewEnlargedCanvasComponent } from 'app/lecture/pdf-preview/pdf-preview-enlarged-canvas/pdf-preview-enlarged-canvas.component';
import { faEye, faEyeSlash, faGripLines } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PdfPreviewDateBoxComponent } from 'app/lecture/pdf-preview/pdf-preview-date-box/pdf-preview-date-box.component';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';
import { HiddenPage, HiddenPageMap } from 'app/lecture/pdf-preview/pdf-preview.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';

interface OrderedPage {
    pageIndex: number;
    slideId?: string;
    order: number;
}

@Component({
    selector: 'jhi-pdf-preview-thumbnail-grid-component',
    templateUrl: './pdf-preview-thumbnail-grid.component.html',
    styleUrls: ['./pdf-preview-thumbnail-grid.component.scss'],
    imports: [PdfPreviewEnlargedCanvasComponent, FaIconComponent, PdfPreviewDateBoxComponent, NgbModule, TranslateDirective],
})
export class PdfPreviewThumbnailGridComponent implements OnChanges {
    pdfContainer = viewChild.required<ElementRef<HTMLDivElement>>('pdfContainer');

    forever = dayjs('9999-12-31');

    // Inputs
    course = input<Course>();
    currentPdfUrl = input<string>();
    appendFile = input<boolean>();
    hiddenPages = input<HiddenPageMap>();
    isAttachmentUnit = input<boolean>();
    updatedSelectedPages = input<Set<number>>(new Set());

    // Drag and drop properties
    dragIndex = signal<number | null>(null);
    isDragging = signal<boolean>(false);
    orderedPages = signal<OrderedPage[]>([]);
    pageOrder = signal<Map<number, number>>(new Map());

    // Signals
    isEnlargedView = signal<boolean>(false);
    totalPagesArray = signal<Set<number>>(new Set());
    loadedPages = signal<Set<number>>(new Set());
    selectedPages = signal<Set<number>>(new Set());
    originalCanvas = signal<HTMLCanvasElement | undefined>(undefined);
    newHiddenPages = signal<HiddenPageMap>(this.hiddenPages() || {});
    initialPageNumber = signal<number>(0);
    activeButtonIndex = signal<number | null>(null);
    isPopoverOpen = signal<boolean>(false);

    // Outputs
    totalPagesOutput = output<number>();
    selectedPagesOutput = output<Set<number>>();
    newHiddenPagesOutput = output<HiddenPageMap>();
    pageOrderOutput = output<OrderedPage[]>();

    // Injected services
    private readonly alertService = inject(AlertService);

    protected readonly faEye = faEye;
    protected readonly faEyeSlash = faEyeSlash;
    protected readonly faGripLines = faGripLines;

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['hiddenPages']) {
            this.newHiddenPages.set(this.hiddenPages()!);
        }
        if (changes['currentPdfUrl']) {
            this.loadPdf(this.currentPdfUrl()!, this.appendFile()!);
        }
        if (changes['updatedSelectedPages']) {
            this.selectedPages.set(new Set(this.updatedSelectedPages()!));
            this.updateCheckboxStates();
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
        try {
            const loadingTask = PDFJS.getDocument(fileUrl);
            const pdf = await loadingTask.promise;
            this.totalPagesArray.set(new Set(Array.from({ length: pdf.numPages }, (_, i) => i + 1)));

            this.initializePageOrder(pdf.numPages);

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
        }
    }

    /**
     * Initializes the page ordering for drag and drop functionality
     * @param numPages The total number of pages
     */
    initializePageOrder(numPages: number): void {
        const initialOrder = Array.from({ length: numPages }, (_, i) => ({
            pageIndex: i + 1,
            order: i + 1,
        }));
        this.orderedPages.set(initialOrder);

        // Create a map for quick lookup of page order
        const orderMap = new Map<number, number>();
        initialOrder.forEach((page) => orderMap.set(page.pageIndex, page.order));
        this.pageOrder.set(orderMap);
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
        this.activeButtonIndex.set(pageIndex);
        const button = (event.target as HTMLElement).closest('button');
        if (button) {
            button.style.opacity = '1';
        }

        event.stopPropagation();
    }

    /**
     * Toggles the selection state of a page by adding or removing it from the selected pages set.
     * @param pageIndex The index of the page whose selection state is being toggled.
     * @param event The change event triggered by the checkbox interaction.
     */
    togglePageSelection(pageIndex: number, event: Event): void {
        const checkbox = event.target as HTMLInputElement;
        if (checkbox.checked) {
            this.selectedPages().add(pageIndex);
        } else {
            this.selectedPages().delete(pageIndex);
        }
        this.selectedPagesOutput.emit(this.selectedPages());
    }

    /**
     * Updates hidden pages information based on data from the date box component
     * @param hiddenPageData Data for one or more hidden pages
     */
    onHiddenPagesReceived(hiddenPageData: HiddenPage | HiddenPage[]): void {
        const pages = Array.isArray(hiddenPageData) ? hiddenPageData : [hiddenPageData];

        const updatedHiddenPages = { ...this.newHiddenPages() };

        pages.forEach((page) => {
            updatedHiddenPages[page.pageIndex] = {
                date: dayjs(page.date),
                exerciseId: page.exerciseId ?? null,
            };
        });

        this.newHiddenPages.set(updatedHiddenPages);
        this.newHiddenPagesOutput.emit(this.newHiddenPages());
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
        this.newHiddenPagesOutput.emit(this.newHiddenPages());
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

    /**
     * Updates checkbox states to match the current selection model
     */
    private updateCheckboxStates(): void {
        const checkboxes = this.pdfContainer()?.nativeElement.querySelectorAll('input[type="checkbox"]');
        if (!checkboxes) return;

        checkboxes.forEach((checkbox: HTMLInputElement) => {
            const pageNumber = parseInt(checkbox.id.replace('checkbox-', ''), 10);
            checkbox.checked = this.selectedPages().has(pageNumber);
        });
    }

    /**
     * Initiates drag operation for a page
     * @param event Drag event
     * @param pageIndex Page being dragged
     */
    onDragStart(event: DragEvent, pageIndex: number): void {
        if (event.dataTransfer) {
            // Set the drag data and effect
            event.dataTransfer.setData('text/plain', pageIndex.toString());
            event.dataTransfer.effectAllowed = 'move';

            this.dragIndex.set(pageIndex);
            this.isDragging.set(true);

            // Add a class to the dragged element
            const element = document.getElementById(`pdf-page-${pageIndex}`);
            if (element) {
                element.classList.add('dragging');
            }
        }
    }

    /**
     * Handles the drag over event to allow dropping
     * @param event Drag event
     */
    onDragOver(event: DragEvent): void {
        event.preventDefault();
        if (event.dataTransfer) {
            event.dataTransfer.dropEffect = 'move';
        }
    }

    /**
     * Handles dropping of a page to reorder it
     * @param event Drop event
     * @param targetPageIndex Target page where item is being dropped
     */
    onDrop(event: DragEvent, targetPageIndex: number): void {
        event.preventDefault();
        if (!event.dataTransfer) return;

        const sourcePageIndex = parseInt(event.dataTransfer.getData('text/plain'), 10);
        if (sourcePageIndex !== targetPageIndex) {
            this.reorderPages(sourcePageIndex, targetPageIndex);
        }

        this.isDragging.set(false);
        this.dragIndex.set(null);

        // Remove dragging class from all elements
        document.querySelectorAll('.pdf-canvas-container').forEach((el) => {
            el.classList.remove('dragging');
            el.classList.remove('drag-over');
        });
    }

    /**
     * Handles drag enter event to highlight drop targets
     * @param event Drag event
     * @param pageIndex Page being entered
     */
    onDragEnter(event: DragEvent, pageIndex: number): void {
        event.preventDefault();
        const element = document.getElementById(`pdf-page-${pageIndex}`);
        if (element && this.dragIndex() !== pageIndex) {
            element.classList.add('drag-over');
        }
    }

    /**
     * Handles drag leave event to remove highlighting
     * @param event Drag event
     * @param pageIndex Page being left
     */
    onDragLeave(event: DragEvent, pageIndex: number): void {
        event.preventDefault();
        const element = document.getElementById(`pdf-page-${pageIndex}`);
        if (element) {
            element.classList.remove('drag-over');
        }
    }

    /**
     * Ends the drag operation
     */
    onDragEnd(): void {
        this.isDragging.set(false);
        this.dragIndex.set(null);

        // Remove dragging class from all elements
        document.querySelectorAll('.pdf-canvas-container').forEach((el) => {
            el.classList.remove('dragging');
            el.classList.remove('drag-over');
        });
    }

    /**
     * Reorders pages based on drag and drop operation
     * @param sourceIndex Source page index
     * @param targetIndex Target page index
     */
    reorderPages(sourceIndex: number, targetIndex: number): void {
        const pages = [...this.orderedPages()];

        const currentOrderMap = new Map<number, number>();
        pages.forEach((page) => currentOrderMap.set(page.pageIndex, page.order));

        const sourceOrder = currentOrderMap.get(sourceIndex) || sourceIndex;
        const targetOrder = currentOrderMap.get(targetIndex) || targetIndex;
        const newOrderMap = new Map<number, number>();

        pages.forEach((page) => {
            const currentOrder = currentOrderMap.get(page.pageIndex) || page.pageIndex;

            if (page.pageIndex === sourceIndex) {
                page.order = targetOrder;
                newOrderMap.set(page.pageIndex, targetOrder);
            } else if (currentOrder === targetOrder) {
                page.order = sourceOrder;
                newOrderMap.set(page.pageIndex, sourceOrder);
            } else {
                page.order = currentOrder;
                newOrderMap.set(page.pageIndex, currentOrder);
            }
        });

        this.orderedPages.set(pages);
        this.pageOrder.set(newOrderMap);
        this.pageOrderOutput.emit(pages);
    }

    /**
     * Gets the visual order of a page for display in the template
     * @param pageIndex The page index
     * @returns The display order of the page
     */
    getPageDisplayOrder(pageIndex: number): number {
        return this.pageOrder().get(pageIndex) || pageIndex;
    }

    /**
     * Gets the sorted array of pages based on their current order
     */
    getSortedPages(): number[] {
        if (this.orderedPages().length === 0) {
            return Array.from(this.totalPagesArray());
        }

        return [...this.orderedPages()].sort((a, b) => a.order - b.order).map((page) => page.pageIndex);
    }
}
