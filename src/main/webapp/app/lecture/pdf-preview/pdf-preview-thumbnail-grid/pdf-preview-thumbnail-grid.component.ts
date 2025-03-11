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
import { HiddenPage, HiddenPageMap, OrderedPage } from 'app/lecture/pdf-preview/pdf-preview.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-pdf-preview-thumbnail-grid-component',
    templateUrl: './pdf-preview-thumbnail-grid.component.html',
    styleUrls: ['./pdf-preview-thumbnail-grid.component.scss'],
    imports: [PdfPreviewEnlargedCanvasComponent, FaIconComponent, PdfPreviewDateBoxComponent, NgbModule, TranslateDirective],
    standalone: true,
})
export class PdfPreviewThumbnailGridComponent implements OnChanges {
    pdfContainer = viewChild.required<ElementRef<HTMLDivElement>>('pdfContainer');

    forever = dayjs('9999-12-31');

    // Inputs
    course = input<Course>();
    currentPdfUrl = input<string>();
    appendFile = input<boolean>();
    hiddenPages = input<HiddenPageMap>({});
    isAttachmentUnit = input<boolean>();
    updatedSelectedPages = input<Set<OrderedPage>>(new Set());
    orderedPages = input<OrderedPage[]>([]);

    // Signals
    isEnlargedView = signal<boolean>(false);
    loadedPages = signal<Set<number>>(new Set());
    selectedPages = signal<Set<OrderedPage>>(new Set());
    originalCanvas = signal<HTMLCanvasElement | undefined>(undefined);
    initialPageNumber = signal<number>(0);
    activeButtonPage = signal<OrderedPage | null>(null);
    isPopoverOpen = signal<boolean>(false);
    dragSlideId = signal<string | null>(null);
    isDragging = signal<boolean>(false);

    // Outputs
    selectedPagesOutput = output<Set<OrderedPage>>();
    hiddenPagesOutput = output<HiddenPageMap>();
    pageOrderOutput = output<OrderedPage[]>();

    // Injected services
    private readonly alertService = inject(AlertService);

    protected readonly faEye = faEye;
    protected readonly faEyeSlash = faEyeSlash;
    protected readonly faGripLines = faGripLines;

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['orderedPages']) {
            this.renderPages();
        }
        if (changes['updatedSelectedPages']) {
            this.selectedPages.set(new Set(this.updatedSelectedPages()!));
            this.updateCheckboxStates();
        }
    }

    /**
     * Renders PDF pages using the page proxies from the ordered pages
     */
    async renderPages(): Promise<void> {
        const pages = this.orderedPages();
        try {
            this.pdfContainer()
                .nativeElement.querySelectorAll('.pdf-canvas-container canvas')
                .forEach((canvas) => canvas.remove());

            this.loadedPages.set(new Set());

            for (let i = 0; i < pages.length; i++) {
                const page = pages[i];
                const pageProxy = page.pageProxy;
                if (pageProxy) {
                    const viewport = pageProxy.getViewport({ scale: 1 });
                    const canvas = this.createCanvas(viewport);
                    const context = canvas.getContext('2d')!;

                    await pageProxy.render({ canvasContext: context, viewport }).promise;

                    const container = this.pdfContainer().nativeElement.querySelector(`#pdf-page-${page.slideId}`);
                    if (container) {
                        container.appendChild(canvas);
                        this.loadedPages.update((loadedPages) => {
                            const newLoadedPages = new Set(loadedPages);
                            newLoadedPages.add(page.pageIndex);
                            return newLoadedPages;
                        });
                    }
                }
            }

            if (this.appendFile()) {
                this.scrollToBottom();
            }
        } catch (error) {
            onError(this.alertService, error);
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
     * Toggles the visibility state of a page by showing the date selection
     * @param slideId The ID of the slide whose visibility is being toggled.
     * @param event The event object triggered by the click action.
     */
    toggleVisibility(slideId: string, event: Event): void {
        const page = this.findPageBySlideId(slideId);
        if (page) {
            this.activeButtonPage.set(page);
            const button = (event.target as HTMLElement).closest('button');
            if (button) {
                button.style.opacity = '1';
            }
        }

        event.stopPropagation();
    }

    /**
     * Toggles the selection state of a page by adding or removing it from the selected pages set.
     * @param slideId The ID of the slide whose selection state is being toggled.
     * @param event The change event triggered by the checkbox interaction.
     */
    togglePageSelection(slideId: string, event: Event): void {
        const checkbox = event.target as HTMLInputElement;
        const page = this.findPageBySlideId(slideId);

        if (!page) return;

        if (checkbox.checked) {
            this.selectedPages().add(page);
        } else {
            this.selectedPages().forEach((selectedPage) => {
                if (selectedPage.slideId === slideId) {
                    this.selectedPages().delete(selectedPage);
                }
            });
        }
        this.selectedPagesOutput.emit(this.selectedPages());
    }

    /**
     * Updates hidden pages information based on data from the date box component
     * @param hiddenPageData Data for one or more hidden pages
     */
    onHiddenPagesReceived(hiddenPageData: HiddenPage | HiddenPage[]): void {
        const pages = Array.isArray(hiddenPageData) ? hiddenPageData : [hiddenPageData];
        const updatedHiddenPages = { ...this.hiddenPages() };

        pages.forEach((page) => {
            updatedHiddenPages[page.slideId] = {
                date: dayjs(page.date),
                exerciseId: page.exerciseId ?? null,
            };
        });

        this.hiddenPagesOutput.emit(updatedHiddenPages);
    }

    /**
     * Displays the selected PDF page in an enlarged view for detailed examination.
     * @param pageIndex - The index of PDF page to be enlarged.
     * @param slideId - The ID of the slide
     * */
    displayEnlargedCanvas(pageIndex: number, slideId: string): void {
        const canvas = this.pdfContainer().nativeElement.querySelector(`#pdf-page-${slideId} canvas`) as HTMLCanvasElement;
        this.originalCanvas.set(canvas!);
        this.isEnlargedView.set(true);
        this.initialPageNumber.set(pageIndex);
    }

    /**
     * Removes a page from the hidden pages and hides the associated action button.
     *
     * @param slideId - The ID of the slide to be made visible.
     */
    showPage(slideId: string): void {
        const updatedHiddenPages = { ...this.hiddenPages() };
        delete updatedHiddenPages[slideId];
        this.hiddenPagesOutput.emit(updatedHiddenPages);
        this.hideActionButton(slideId);
    }

    /**
     * Hides the action button associated with a specified slide by setting its opacity to 0.
     *
     * @param slideId - The ID of the slide whose action button should be hidden.
     */
    hideActionButton(slideId: string): void {
        const button = document.getElementById('hide-show-button-' + slideId);
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
            const match = checkbox.id.match(/checkbox-(.+)/);
            if (match) {
                const slideId = match[1];
                checkbox.checked = Array.from(this.selectedPages()).some((page) => page.slideId === slideId);
            }
        });
    }

    /**
     * Initiates drag operation for a slide
     * @param event Drag event
     * @param slideId ID of the slide being dragged
     */
    onDragStart(event: DragEvent, slideId: string): void {
        if (event.dataTransfer) {
            event.dataTransfer.setData('text/plain', slideId);
            event.dataTransfer.effectAllowed = 'move';

            this.dragSlideId.set(slideId);
            this.isDragging.set(true);

            const element = document.getElementById(`pdf-page-${slideId}`);
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
     * Handles dropping of a slide to reorder it
     * @param event Drop event
     * @param targetSlideId ID of the target slide where item is being dropped
     */
    onDrop(event: DragEvent, targetSlideId: string): void {
        event.preventDefault();
        if (!event.dataTransfer) return;

        const sourceSlideId = event.dataTransfer.getData('text/plain');
        if (sourceSlideId !== targetSlideId) {
            this.reorderPages(sourceSlideId, targetSlideId);
        }

        this.isDragging.set(false);
        this.dragSlideId.set(null);

        document.querySelectorAll('.pdf-canvas-container').forEach((el) => {
            el.classList.remove('dragging');
            el.classList.remove('drag-over');
        });
    }

    /**
     * Handles drag enter event to highlight drop targets
     * @param event Drag event
     * @param slideId ID of the slide being entered
     */
    onDragEnter(event: DragEvent, slideId: string): void {
        event.preventDefault();
        const element = document.getElementById(`pdf-page-${slideId}`);
        if (element && this.dragSlideId() !== slideId) {
            element.classList.add('drag-over');
        }
    }

    /**
     * Handles drag leave event to remove highlighting
     * @param event Drag event
     * @param slideId ID of the slide being left
     */
    onDragLeave(event: DragEvent, slideId: string): void {
        event.preventDefault();
        const element = document.getElementById(`pdf-page-${slideId}`);
        if (element) {
            element.classList.remove('drag-over');
        }
    }

    /**
     * Ends the drag operation
     */
    onDragEnd(): void {
        this.isDragging.set(false);
        this.dragSlideId.set(null);

        document.querySelectorAll('.pdf-canvas-container').forEach((el) => {
            el.classList.remove('dragging');
            el.classList.remove('drag-over');
        });
    }

    /**
     * Reorders pages based on drag and drop operation using array positions
     * @param sourceSlideId ID of the source slide
     * @param targetSlideId ID of the target slide
     */
    reorderPages(sourceSlideId: string, targetSlideId: string): void {
        const pages = [...this.orderedPages()];

        const sourceIndex = pages.findIndex((page) => page.slideId === sourceSlideId);
        const targetIndex = pages.findIndex((page) => page.slideId === targetSlideId);

        if (sourceIndex === -1 || targetIndex === -1) return;

        pages.splice(targetIndex, 0, pages.splice(sourceIndex, 1)[0]);
        pages.forEach((page, index) => (page.pageIndex = index + 1));

        this.pageOrderOutput.emit(pages);
    }

    /**
     * Gets the display order (position + 1) of a slide for the UI
     * @param slideId The ID of the slide
     * @returns The display order (1-based)
     */
    getPageOrder(slideId: string): number {
        const index = this.orderedPages().findIndex((page) => page.slideId === slideId);
        return index !== -1 ? index + 1 : -1;
    }

    /**
     * Find a page by its slide ID
     * @param slideId The slide ID to search for
     */
    findPageBySlideId(slideId: string): OrderedPage | undefined {
        return this.orderedPages().find((page) => page.slideId === slideId);
    }
}
