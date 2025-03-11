import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AttachmentService } from 'app/lecture/attachment.service';
import { Attachment } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { Subject, Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { HttpErrorResponse } from '@angular/common/http';

import { faCancel, faExclamationCircle, faEye, faEyeSlash, faFileImport, faSave, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { objectToJsonBlob } from 'app/utils/blob-util';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { PdfPreviewThumbnailGridComponent } from 'app/lecture/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { PDFDocument } from 'pdf-lib';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbModule, NgbPopover, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Slide } from 'app/entities/lecture-unit/slide.model';
import { finalize } from 'rxjs/operators';
import { ConfirmAutofocusButtonComponent } from 'app/shared/components/confirm-autofocus-button.component';
import { ButtonType } from 'app/shared/components/button.component';
import { PdfPreviewDateBoxComponent } from 'app/lecture/pdf-preview/pdf-preview-date-box/pdf-preview-date-box.component';
import * as PDFJS from 'pdfjs-dist';
import 'pdfjs-dist/build/pdf.worker';

export interface OrderedPage {
    pageIndex: number;
    slideId: string;
    order: number;
    pageProxy?: PDFJS.PDFPageProxy;
    hidden?: dayjs.Dayjs | null; // Date when the page becomes hidden, null if not hidden
    exerciseId?: number | null; // Associated exercise ID, if any
}

@Component({
    selector: 'jhi-pdf-preview-component',
    templateUrl: './pdf-preview.component.html',
    styleUrls: ['./pdf-preview.component.scss'],
    imports: [
        PdfPreviewThumbnailGridComponent,
        ArtemisTranslatePipe,
        FontAwesomeModule,
        NgbTooltipModule,
        RouterModule,
        DeleteButtonDirective,
        TranslateDirective,
        ConfirmAutofocusButtonComponent,
        PdfPreviewDateBoxComponent,
        NgbModule,
    ],
})
export class PdfPreviewComponent implements OnInit, OnDestroy {
    fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');
    showPopover = viewChild.required<NgbPopover>('showPopover');

    attachmentSub: Subscription;
    attachmentUnitSub: Subscription;

    forever = dayjs('9999-12-31');

    protected readonly ButtonType = ButtonType;
    protected readonly Object = Object;
    protected readonly Array = Array;

    // Signals
    course = signal<Course | undefined>(undefined);
    attachment = signal<Attachment | undefined>(undefined);
    attachmentUnit = signal<AttachmentUnit | undefined>(undefined);
    isPdfLoading = signal<boolean>(false);
    attachmentToBeEdited = signal<Attachment | undefined>(undefined);
    currentPdfBlob = signal<Blob | undefined>(undefined);
    currentPdfUrl = signal<string | undefined>(undefined);
    totalPages = signal<number>(0);
    appendFile = signal<boolean>(false);
    isFileChanged = signal<boolean>(false);
    selectedPages = signal<Set<OrderedPage>>(new Set());
    pageOrder = signal<OrderedPage[]>([]);
    initialPageOrder = signal<OrderedPage[]>([]);
    isSaving = signal<boolean>(false);

    // Computed properties
    allPagesSelected = computed(() => this.selectedPages().size === this.totalPages());
    pageOrderChanged = computed(() => {
        if (this.pageOrder().length === 0) return false;
        return this.pageOrder().some((page, index) => page.pageIndex !== index + 1);
    });
    hasHiddenPages = computed(() => this.pageOrder().some((page) => page.hidden !== null));
    hasHiddenSelectedPages = computed(() => {
        const pageMap = new Map(this.pageOrder().map((p) => [p.slideId, p]));
        return Array.from(this.selectedPages()).some((page) => pageMap.get(page.slideId)?.hidden);
    });

    // Injected services
    private readonly route = inject(ActivatedRoute);
    private readonly attachmentService = inject(AttachmentService);
    private readonly attachmentUnitService = inject(AttachmentUnitService);
    private readonly lectureUnitService = inject(LectureUnitService);
    private readonly alertService = inject(AlertService);
    private readonly router = inject(Router);

    dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    protected readonly faCancel = faCancel;
    protected readonly faExclamationCircle = faExclamationCircle;
    protected readonly faFileImport = faFileImport;
    protected readonly faEye = faEye;
    protected readonly faEyeSlash = faEyeSlash;
    protected readonly faSave = faSave;
    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;

    ngOnInit() {
        this.isPdfLoading.set(true);
        this.route.data.subscribe((data) => {
            this.course.set(data.course);

            if ('attachment' in data) {
                this.attachment.set(data.attachment);
                this.attachmentSub = this.attachmentService
                    .getAttachmentFile(this.course()!.id!, this.attachment()!.id!)
                    .pipe(finalize(() => this.isPdfLoading.set(false)))
                    .subscribe({
                        next: (blob: Blob) => {
                            this.currentPdfBlob.set(blob);
                            const url = URL.createObjectURL(blob);
                            this.currentPdfUrl.set(url);
                            this.loadPdf(url, false);
                        },
                        error: (error: HttpErrorResponse) => {
                            onError(this.alertService, error);
                        },
                    });
            } else if ('attachmentUnit' in data) {
                this.attachmentUnit.set(data.attachmentUnit);
                const { slides } = data.attachmentUnit;

                if (slides?.length > 0) {
                    const orderedPages = slides.map((slide: Slide, index: number) => ({
                        pageIndex: slide.slideNumber,
                        slideId: slide.id,
                        order: index + 1,
                        hidden: slide.hidden ? dayjs(slide.hidden) : null,
                        exerciseId: slide.exercise?.id ?? null,
                    }));

                    this.pageOrder.set(orderedPages);
                    this.initialPageOrder.set(JSON.parse(JSON.stringify(orderedPages)));
                }

                this.attachmentUnitSub = this.attachmentUnitService
                    .getAttachmentFile(this.course()!.id!, this.attachmentUnit()!.id!)
                    .pipe(finalize(() => this.isPdfLoading.set(false)))
                    .subscribe({
                        next: (blob: Blob) => {
                            this.currentPdfBlob.set(blob);
                            const url = URL.createObjectURL(blob);
                            this.currentPdfUrl.set(url);
                            this.loadPdf(url, false);
                        },
                        error: (error: HttpErrorResponse) => {
                            onError(this.alertService, error);
                        },
                    });
            } else {
                this.isPdfLoading.set(false);
            }
        });
    }

    ngOnDestroy() {
        this.attachmentSub?.unsubscribe();
        this.attachmentUnitSub?.unsubscribe();

        if (this.currentPdfUrl()) {
            URL.revokeObjectURL(this.currentPdfUrl()!);
        }
    }

    constructor() {
        effect(() => {
            this.hiddenPagesChanged();
        });
    }

    /**
     * Triggers the file input to select files.
     */
    triggerFileInput(): void {
        this.fileInput().nativeElement.click();
    }

    /**
     * Compares the initial and current hidden pages to determine if there have been any changes.
     *
     * @returns `true` if the initial and current hidden pages differ, indicating a change; otherwise, `false`.
     */
    hiddenPagesChanged(): boolean {
        const initial = this.initialPageOrder();
        const current = this.pageOrder();

        if (initial.length !== current.length) return true;

        return initial.some((initialPage) => {
            const currentPage = current.find((p) => p.slideId === initialPage.slideId);
            if (!currentPage) return true;

            const initialHiddenDate = initialPage.hidden?.format?.() || null;
            const currentHiddenDate = currentPage.hidden?.format?.() || null;

            return initialHiddenDate !== currentHiddenDate || initialPage.exerciseId !== currentPage.exerciseId;
        });
    }

    /**
     * Loads a PDF from a provided URL and creates/updates page order.
     * @param fileUrl The URL of the file to load.
     * @param append Whether to append the new pages to existing ones.
     */
    async loadPdf(fileUrl: string, append: boolean = false): Promise<void> {
        this.isPdfLoading.set(true);
        try {
            const loadingTask = PDFJS.getDocument(fileUrl);
            const pdf = await loadingTask.promise;
            this.totalPages.set(pdf.numPages);

            if (append) {
                const currentPageCount = this.pageOrder().length;
                const newPages: OrderedPage[] = [];

                for (let i = 1; i <= pdf.numPages; i++) {
                    const pageProxy = await pdf.getPage(i);
                    newPages.push({
                        slideId: `temp_${Date.now()}_${i - 1}`,
                        pageIndex: currentPageCount + i,
                        order: currentPageCount + i,
                        pageProxy: pageProxy,
                        hidden: null,
                        exerciseId: null,
                    });
                }

                this.pageOrder.update((pages) => [...pages, ...newPages]);
            } else {
                const currentOrder = [...this.pageOrder()];

                for (let i = 0; i < currentOrder.length && i < pdf.numPages; i++) {
                    currentOrder[i].pageProxy = await pdf.getPage(i + 1);
                }

                this.pageOrder.set(currentOrder);
            }

            this.appendFile.set(false);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isPdfLoading.set(false);
        }
    }

    /**
     * Updates the existing attachment file or creates a student version of the attachment with hidden files.
     * Uses the page proxies from pageOrder to generate the PDF.
     */
    async updateAttachmentWithFile(): Promise<void> {
        this.isSaving.set(true);

        try {
            const pdfName = this.attachment()?.name ?? this.attachmentUnit()?.name ?? '';
            const pdfFile = await this.createPdf(pdfName);

            if (pdfFile.size > MAX_FILE_SIZE) {
                this.alertService.error('artemisApp.attachment.pdfPreview.fileSizeError');
                this.isSaving.set(false);
                return;
            }

            if (this.attachment()) {
                this.attachmentToBeEdited.set(this.attachment());
                this.attachmentToBeEdited()!.version!++;
                this.attachmentToBeEdited()!.uploadDate = dayjs();

                this.attachmentService.update(this.attachmentToBeEdited()!.id!, this.attachmentToBeEdited()!, pdfFile).subscribe({
                    next: () => {
                        this.isSaving.set(false);
                        this.alertService.success('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
                        this.navigateToCourseManagement();
                    },
                    error: (error) => {
                        this.isSaving.set(false);
                        this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                    },
                });
            } else if (this.attachmentUnit()) {
                this.attachmentToBeEdited.set(this.attachmentUnit()!.attachment!);
                this.attachmentToBeEdited()!.uploadDate = dayjs();

                const formData = new FormData();
                formData.append('file', pdfFile);
                formData.append('attachment', objectToJsonBlob(this.attachmentToBeEdited()!));
                formData.append('attachmentUnit', objectToJsonBlob(this.attachmentUnit()!));

                const pageOrderForAPI = this.pageOrder().map((page) => ({
                    pageIndex: page.pageIndex,
                    slideId: page.slideId,
                    order: page.order,
                    hidden: page.hidden ? page.hidden.format() : null,
                    exerciseId: page.exerciseId,
                }));

                formData.append('pageOrder', JSON.stringify(pageOrderForAPI));

                const hasHiddenPages = this.pageOrder().some((page) => page.hidden);

                if (hasHiddenPages) {
                    const studentVersionFile = await this.createPdf(pdfName, true);
                    formData.append('studentVersion', studentVersionFile);
                }

                this.attachmentUnitService.update(this.attachmentUnit()!.lecture!.id!, this.attachmentUnit()!.id!, formData).subscribe({
                    next: () => {
                        this.isSaving.set(false);
                        this.alertService.success('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
                        this.navigateToCourseManagement();
                    },
                    error: (error) => {
                        this.isSaving.set(false);
                        this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                    },
                });
            }
        } catch (error) {
            this.isSaving.set(false);
            this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
        }
    }

    /**
     * Deletes the attachment file if it exists, or deletes the attachment unit if it exists.
     * @returns A Promise that resolves when the deletion process is completed.
     */
    async deleteAttachmentFile() {
        if (this.attachment()) {
            this.attachmentService.delete(this.attachment()!.id!).subscribe({
                next: () => {
                    this.navigateToCourseManagement();
                    this.dialogErrorSource.next('');
                },
                error: (error) => {
                    this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                },
            });
        } else if (this.attachmentUnit()) {
            this.lectureUnitService.delete(this.attachmentUnit()!.id!, this.attachmentUnit()!.lecture!.id!).subscribe({
                next: () => {
                    this.navigateToCourseManagement();
                    this.dialogErrorSource.next('');
                },
                error: (error) => {
                    this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                },
            });
        }
    }

    /**
     * Deletes selected slides by removing them from the page order.
     */
    deleteSelectedSlides() {
        try {
            this.isPdfLoading.set(true);
            const slideIds = Array.from(this.selectedPages()).map((page) => page.slideId);

            this.pageOrder.update((pages) =>
                pages
                    .filter((page) => !slideIds.includes(page.slideId))
                    .map((page, index) => ({
                        ...page,
                        pageIndex: index + 1,
                        order: index + 1,
                    })),
            );

            this.isFileChanged.set(true);
            this.selectedPages.set(new Set());
        } catch (error) {
            this.alertService.error('artemisApp.attachment.pdfPreview.pageDeleteError', { error: error.message });
        } finally {
            this.isPdfLoading.set(false);
            this.dialogErrorSource.next('');
        }
    }

    /**
     * Adds a selected PDF file at the end of the current PDF document.
     * @param event - The event containing the file input.
     */
    async mergePDF(event: Event): Promise<void> {
        const file = (event.target as HTMLInputElement).files?.[0];

        this.isPdfLoading.set(true);
        try {
            const newPdfBytes = await file!.arrayBuffer();
            const existingPdfBytes = await this.currentPdfBlob()!.arrayBuffer();
            const existingPdfDoc = await PDFDocument.load(existingPdfBytes);
            const newPdfDoc = await PDFDocument.load(newPdfBytes);

            const copiedPages = await existingPdfDoc.copyPages(newPdfDoc, newPdfDoc.getPageIndices());
            copiedPages.forEach((page) => existingPdfDoc.addPage(page));

            this.isFileChanged.set(true);
            const mergedPdfBytes = await existingPdfDoc.save();
            this.currentPdfBlob.set(new Blob([mergedPdfBytes], { type: 'application/pdf' }));

            this.selectedPages.set(new Set());

            const objectUrl = URL.createObjectURL(this.currentPdfBlob()!);
            this.currentPdfUrl.set(objectUrl);
            this.appendFile.set(false);
            this.loadPdf(objectUrl, true);
        } catch (error) {
            this.alertService.error('artemisApp.attachment.pdfPreview.mergeFailedError', { error: error.message });
        } finally {
            this.isPdfLoading.set(false);
            this.fileInput()!.nativeElement.value = '';
        }
    }

    /**
     * Creates a PDF file from page proxies in the pageOrder array
     * @param fileName Name to use for the generated PDF file
     * @param studentVersion Whether to create a student version (excluding hidden pages)
     * @returns A Promise that resolves to a File object containing the generated PDF
     */
    async createPdf(fileName: string, studentVersion: boolean = false): Promise<File> {
        const pdfDoc = await PDFDocument.create();

        for (const page of this.pageOrder()) {
            if (studentVersion && page.hidden) continue;

            const viewport = page.pageProxy!.getViewport({ scale: 1.0 });
            const { width, height } = viewport;

            const newPage = pdfDoc.addPage([width, height]);

            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            const context = canvas.getContext('2d')!;

            await page.pageProxy!.render({
                canvasContext: context,
                viewport: viewport,
            }).promise;

            const pngImage = await pdfDoc.embedPng(canvas.toDataURL('image/png'));

            newPage.drawImage(pngImage, {
                x: 0,
                y: 0,
                width: width,
                height: height,
            });
        }

        const pdfBytes = await pdfDoc.save();
        return new File([pdfBytes], `${fileName}.pdf`, { type: 'application/pdf' });
    }

    /**
     * Shows previously hidden pages by setting their hidden property to null
     * and clearing any associated exercise IDs
     * @param selectedPages The set of pages to be made visible
     */
    showPages(selectedPages: Set<OrderedPage>): void {
        this.pageOrder.update((currentOrder) => {
            const updatedOrder = [...currentOrder];

            updatedOrder.forEach((page) => {
                if (Array.from(selectedPages).some((selectedPage) => selectedPage.slideId === page.slideId)) {
                    page.hidden = null;
                    page.exerciseId = null;
                }
            });

            return updatedOrder;
        });

        this.selectedPages.set(new Set());
    }

    /**
     * Handles the reception of one or more hidden pages from the date box component
     * @param hiddenPageData A single hidden page or an array of hidden pages
     */
    hidePages(hiddenPageData: { slideId: string; date: dayjs.Dayjs; exerciseId: number | null } | Array<{ slideId: string; date: dayjs.Dayjs; exerciseId: number | null }>): void {
        const pages = Array.isArray(hiddenPageData) ? hiddenPageData : [hiddenPageData];

        this.pageOrder.update((currentOrder) => {
            const updatedOrder = [...currentOrder];

            for (const hiddenPage of pages) {
                const pageIndex = updatedOrder.findIndex((p) => p.slideId === hiddenPage.slideId);
                if (pageIndex >= 0) {
                    updatedOrder[pageIndex] = {
                        ...updatedOrder[pageIndex],
                        hidden: hiddenPage.date,
                        exerciseId: hiddenPage.exerciseId,
                    };
                }
            }

            return updatedOrder;
        });

        this.selectedPages.set(new Set());
    }

    /**
     * Navigates to the appropriate course management page based on context.
     */
    navigateToCourseManagement(): void {
        if (this.attachment()) {
            this.router.navigate(['course-management', this.course()?.id, 'lectures', this.attachment()!.lecture!.id, 'attachments']);
        } else {
            this.router.navigate(['course-management', this.course()!.id, 'lectures', this.attachmentUnit()!.lecture!.id, 'unit-management']);
        }
    }
}
