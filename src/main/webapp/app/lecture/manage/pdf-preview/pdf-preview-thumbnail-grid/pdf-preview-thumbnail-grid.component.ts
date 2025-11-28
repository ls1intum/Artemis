import { ChangeDetectionStrategy, Component, ElementRef, OnChanges, Renderer2, SimpleChanges, inject, input, output, signal, viewChild } from '@angular/core';
import * as PDFJS from 'pdfjs-dist';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';

import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { PdfPreviewEnlargedCanvasComponent } from 'app/lecture/manage/pdf-preview/pdf-preview-enlarged-canvas/pdf-preview-enlarged-canvas.component';
import { faEye, faEyeSlash, faGripLines } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PdfPreviewDateBoxComponent } from 'app/lecture/manage/pdf-preview/pdf-preview-date-box/pdf-preview-date-box.component';
import dayjs from 'dayjs/esm';
import { HiddenPage, HiddenPageMap, OrderedPage } from 'app/lecture/manage/pdf-preview/pdf-preview.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-pdf-preview-thumbnail-grid-component',
    templateUrl: './pdf-preview-thumbnail-grid.component.html',
    styleUrls: ['./pdf-preview-thumbnail-grid.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [PdfPreviewEnlargedCanvasComponent, FaIconComponent, PdfPreviewDateBoxComponent, NgbModule, TranslateDirective, DragDropModule],
})
export class PdfPreviewThumbnailGridComponent implements OnChanges {
    pdfContainer = viewChild.required<ElementRef<HTMLDivElement>>('pdfContainer');

    FOREVER = dayjs('9999-12-31');

    // Inputs
    courseId = input<number>();
    currentPdfUrl = input<string>();
    isAppendingFile = input<boolean>();
    hiddenPages = input<HiddenPageMap>({});
    isAttachmentVideoUnit = input<boolean>();
    updatedSelectedPages = input<Set<OrderedPage>>(new Set());
    orderedPages = input<OrderedPage[]>([]);

    // Signals
    isEnlargedView = signal<boolean>(false);
    loadedPages = signal<Set<number>>(new Set());
    selectedPages = signal<Set<OrderedPage>>(new Set());
    originalCanvas = signal<HTMLCanvasElement | undefined>(undefined);
    initialPageNumber = signal<number>(0);
    activeButtonPage = signal<OrderedPage | undefined>(undefined);
    isPopoverOpen = signal<boolean>(false);
    dragSlideId = signal<string | undefined>(undefined);
    isDragging = signal<boolean>(false);
    reordering = signal<boolean>(false);

    // Outputs
    selectedPagesOutput = output<Set<OrderedPage>>();
    hiddenPagesOutput = output<HiddenPageMap>();
    pageOrderOutput = output<OrderedPage[]>();

    // Injected services
    private readonly alertService = inject(AlertService);
    private readonly renderer = inject(Renderer2);

    protected readonly faEye = faEye;
    protected readonly faEyeSlash = faEyeSlash;
    protected readonly faGripLines = faGripLines;

    /**
     * pdfjs-dist requires a URL pointing to a worker script to process PDFs. See:
     * - <a href="https://github.com/mozilla/pdfjs-dist">official package repo</a>
     * - <a href="https://github.com/mozilla/pdf.js/issues/12917#">discussion about the topic</a>
     *
     * The worker script is included in the package but is required to be served separately as a static asset for Angular projects. See:
     * - <a href="https://www.nutrient.io/blog/how-to-build-an-angular-pdf-viewer-with-pdfjs/#:~:text=Copy%20the%20,use%20the%20following%20bash%20command">this article</a>
     * - <a href="https://stackoverflow.com/questions/49822219/how-to-give-pdf-js-worker-in-angular-cli-application#:~:text=Put%20your%20pdf,to%20%2Fsrc%2Fassets%2F%20And%20then%20use">this stackoverflow discussion
     * - <a href="https://github.com/mozilla/pdf.js/discussions/18438#">this github issue</a>
     *
     * In Artemis the postinstall lifecycle hook (see package.json) copies the script from the package to the publicly served contents folder.
     */
    constructor() {
        PDFJS.GlobalWorkerOptions.workerSrc = '/content/scripts/pdf.worker.min.mjs';
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['orderedPages']) {
            if (!this.reordering()) {
                this.renderPages();
            }
            this.reordering.set(false);
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
            const containerEl = this.pdfContainer().nativeElement;
            const canvases = containerEl.querySelectorAll('.pdf-canvas-container canvas');
            canvases.forEach((canvas: HTMLCanvasElement) => {
                if (canvas.parentNode) {
                    this.renderer.removeChild(canvas.parentNode, canvas);
                }
            });

            this.loadedPages.set(new Set());

            for (let i = 0; i < pages.length; i++) {
                const page = pages[i];
                const pageProxy = page.pageProxy;
                if (pageProxy) {
                    const viewport = pageProxy.getViewport({ scale: 1 });
                    const canvas = this.createCanvas(viewport);
                    const context = canvas.getContext('2d')!;

                    await pageProxy.render({ canvasContext: context, canvas, viewport }).promise;

                    const container = this.pdfContainer().nativeElement.querySelector(`#pdf-page-${page.slideId}`);
                    if (container) {
                        this.renderer.appendChild(container, canvas);
                        this.loadedPages.update((loadedPages) => {
                            const newLoadedPages = new Set(loadedPages);
                            newLoadedPages.add(page.order);
                            return newLoadedPages;
                        });
                    }
                }
            }

            if (this.isAppendingFile()) {
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
    createCanvas(viewport: PDFJS.PageViewport): HTMLCanvasElement {
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
                this.renderer.setStyle(button, 'opacity', '1');
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

        if (page) {
            const newSelection = new Set(this.selectedPages());

            if (checkbox.checked) {
                newSelection.add(page);
            } else {
                newSelection.forEach((selectedPage) => {
                    if (selectedPage.slideId === slideId) {
                        newSelection.delete(selectedPage);
                    }
                });
            }

            this.selectedPages.set(newSelection);
            this.selectedPagesOutput.emit(newSelection);
        }
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
                exerciseId: page.exerciseId ?? undefined,
            };
        });

        this.hiddenPagesOutput.emit(updatedHiddenPages);
    }

    /**
     * Displays the selected PDF page in an enlarged view for detailed examination.
     * @param pageOrder - The order of PDF page to be enlarged.
     * @param slideId - The ID of the slide
     * */
    displayEnlargedCanvas(pageOrder: number, slideId: string): void {
        const canvas = this.pdfContainer().nativeElement.querySelector(`#pdf-page-${slideId} canvas`) as HTMLCanvasElement;
        this.originalCanvas.set(canvas!);
        this.isEnlargedView.set(true);
        this.initialPageNumber.set(pageOrder);
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
        const container = this.pdfContainer().nativeElement;
        const button = container.querySelector(`#hide-show-button-${slideId}`);
        if (button) {
            this.renderer.setStyle(button, 'opacity', '0');
        }
    }

    /**
     * Updates checkbox states to match the current selection model
     */
    private updateCheckboxStates(): void {
        const checkboxes = this.pdfContainer()?.nativeElement.querySelectorAll('input[type="checkbox"]');

        checkboxes.forEach((checkbox: HTMLInputElement) => {
            const match = checkbox.id.match(/checkbox-(.+)/);
            if (match) {
                const slideId = match[1];
                checkbox.checked = Array.from(this.selectedPages()).some((page) => page.slideId === slideId);
            }
        });
    }

    /**
     * Handles the drop event from Angular CDK drag-drop
     * Updates the page order when an item is dropped in a new position
     * @param event The CDK drop event containing source and target indices
     */
    onPageDrop(event: CdkDragDrop<OrderedPage[]>): void {
        if (event.previousIndex === event.currentIndex) {
            return;
        }

        this.reordering.set(true);
        this.isDragging.set(false);

        const pages = [...this.orderedPages()];

        moveItemInArray(pages, event.previousIndex, event.currentIndex);

        pages.forEach((page, index) => {
            page.order = index + 1;
        });

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
