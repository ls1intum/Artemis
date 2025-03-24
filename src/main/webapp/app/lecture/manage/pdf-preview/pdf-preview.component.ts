import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AttachmentService } from 'app/lecture/manage/attachment.service';
import { Attachment } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentUnitService } from 'app/lecture/manage/lecture-units/attachmentUnit.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { Subject, Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { HttpErrorResponse } from '@angular/common/http';

import { faCancel, faExclamationCircle, faEye, faEyeSlash, faFileImport, faSave, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { objectToJsonBlob } from 'app/utils/blob-util';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { PdfPreviewThumbnailGridComponent } from 'app/lecture/manage/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/lectureUnit.service';
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
import { PdfPreviewDateBoxComponent } from 'app/lecture/manage/pdf-preview/pdf-preview-date-box/pdf-preview-date-box.component';
import * as PDFJS from 'pdfjs-dist';
import 'pdfjs-dist/build/pdf.worker';

interface PdfOperation {
    type: 'MERGE' | 'DELETE' | 'HIDE' | 'SHOW' | 'REORDER';
    timestamp: dayjs.Dayjs;
    data: any;
}

export interface PDFSource {
    id: string;
    pdfDocument: PDFDocument;
    blob: Blob;
    url: string;
}

export interface OrderedPage {
    initialIndex: number;
    slideId: string;
    order: number;
    sourcePdfId: string;
    sourceIndex: number;
    pageProxy: PDFJS.PDFPageProxy;
}

export interface HiddenPage {
    slideId: string;
    date: dayjs.Dayjs;
    exerciseId: number | null;
}

export interface HiddenPageMap {
    [slideId: string]: {
        date: dayjs.Dayjs;
        exerciseId: number | null;
    };
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

    FOREVER = dayjs('9999-12-31');

    protected readonly ButtonType = ButtonType;
    protected readonly Object = Object;
    protected readonly Array = Array;

    // Track operations in sequential order
    private operations: PdfOperation[] = [];

    // Signals
    course = signal<Course | undefined>(undefined);
    attachment = signal<Attachment | undefined>(undefined);
    attachmentUnit = signal<AttachmentUnit | undefined>(undefined);
    isPdfLoading = signal<boolean>(false);
    attachmentToBeEdited = signal<Attachment | undefined>(undefined);
    currentPdfUrl = signal<string | undefined>(undefined);
    totalPages = signal<number>(0);
    appendFile = signal<boolean>(false);
    isFileChanged = signal<boolean>(false);
    selectedPages = signal<Set<OrderedPage>>(new Set());
    initialHiddenPages = signal<HiddenPageMap>({});
    hiddenPages = signal<HiddenPageMap>({});
    isSaving = signal<boolean>(false);
    pageOrder = signal<OrderedPage[]>([]);
    sourcePDFs = signal<Map<string, PDFSource>>(new Map());
    hasOperations = signal<boolean>(false);

    // Computed properties
    allPagesSelected = computed(() => this.selectedPages().size === this.totalPages());
    pageOrderChanged = computed(() => {
        return this.pageOrder().some((page, index) => page.initialIndex !== index + 1);
    });
    hasHiddenPages = computed(() => Object.keys(this.hiddenPages()).length > 0);
    hasHiddenSelectedPages = computed(() => {
        return Array.from(this.selectedPages()).some((page) => this.hiddenPages()[page.slideId]);
    });

    hasChanges = computed(() => {
        return this.hasOperations() || this.hiddenPagesChanged() || this.pageOrderChanged() || this.isFileChanged();
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
                            const url = URL.createObjectURL(blob);
                            this.currentPdfUrl.set(url);
                            blob.arrayBuffer().then((arrayBuffer) => {
                                this.loadPdf(url, arrayBuffer, 'original');
                            });
                        },
                        error: (error: HttpErrorResponse) => {
                            onError(this.alertService, error);
                        },
                    });
            } else if ('attachmentUnit' in data) {
                this.attachmentUnit.set(data.attachmentUnit);
                const { slides } = data.attachmentUnit;

                // Store hidden pages information
                const hiddenPagesMap: HiddenPageMap = Object.fromEntries(
                    slides
                        .filter((page: Slide) => page.hidden)
                        .map((page: Slide) => [
                            page.id,
                            {
                                date: dayjs(page.hidden),
                                exerciseId: page.exercise?.id ?? null,
                            },
                        ]),
                );
                this.initialHiddenPages.set(hiddenPagesMap);
                this.hiddenPages.set({ ...hiddenPagesMap });
                this.attachmentUnitSub = this.attachmentUnitService
                    .getAttachmentFile(this.course()!.id!, this.attachmentUnit()!.id!)
                    .pipe(finalize(() => this.isPdfLoading.set(false)))
                    .subscribe({
                        next: (blob: Blob) => {
                            const url = URL.createObjectURL(blob);
                            this.currentPdfUrl.set(url);
                            blob.arrayBuffer().then((arrayBuffer) => {
                                this.loadPdf(url, arrayBuffer, 'original', slides);
                            });
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

    /**
     * Loads a PDF from a provided URL and creates/updates page order.
     * @param fileUrl The URL of the file to load
     * @param arrayBuffer The PDF file as an ArrayBuffer
     * @param sourceId Identifier for the source PDF, or 'original' for the initial PDF
     * @param existingSlides Optional array of existing slides (for attachmentUnit)
     * @param append Whether to append the new pages to existing ones
     */
    async loadPdf(fileUrl: string, arrayBuffer: ArrayBuffer, sourceId: string = 'original', existingSlides?: Slide[], append: boolean = false): Promise<void> {
        this.isPdfLoading.set(true);
        try {
            const pdfDocument = await PDFDocument.load(arrayBuffer);
            const pageCount = pdfDocument.getPageCount();

            const loadingTask = PDFJS.getDocument(fileUrl);
            const pdfJsDocument = await loadingTask.promise;

            this.sourcePDFs.update((sources) => {
                const newSources = new Map(sources);
                newSources.set(sourceId, {
                    id: sourceId,
                    pdfDocument,
                    blob: new Blob([arrayBuffer], { type: 'application/pdf' }),
                    url: fileUrl,
                });
                return newSources;
            });

            this.totalPages.set(this.totalPages() + pageCount);

            if (append) {
                this.operations.push({
                    type: 'MERGE',
                    timestamp: dayjs(),
                    data: {
                        sourceId,
                        pageCount,
                    },
                });
                this.hasOperations.set(true);

                const currentPageCount = this.pageOrder().length;
                const newPages: OrderedPage[] = [];

                for (let i = 0; i < pageCount; i++) {
                    const pageProxy = await pdfJsDocument.getPage(i + 1);

                    newPages.push({
                        slideId: `temp_${Date.now()}_${i}`,
                        initialIndex: currentPageCount + i + 1,
                        order: currentPageCount + i + 1,
                        sourcePdfId: sourceId,
                        sourceIndex: i,
                        pageProxy,
                    });
                }

                this.pageOrder.update((pages) => [...pages, ...newPages]);
                this.isFileChanged.set(true);
            } else if (existingSlides && existingSlides.length > 0) {
                const orderedPages: OrderedPage[] = [];

                for (const slide of existingSlides) {
                    const sourceIndex = slide.slideNumber! - 1;
                    const pageProxy = await pdfJsDocument.getPage(sourceIndex + 1);

                    orderedPages.push({
                        initialIndex: slide.slideNumber!,
                        slideId: slide.id!.toString(),
                        order: slide.slideNumber!,
                        sourcePdfId: sourceId,
                        sourceIndex,
                        pageProxy,
                    });
                }

                orderedPages.sort((a, b) => a.order - b.order);
                this.pageOrder.set(orderedPages);
            } else {
                const orderedPages: OrderedPage[] = [];

                for (let i = 0; i < pageCount; i++) {
                    const pageProxy = await pdfJsDocument.getPage(i + 1);

                    orderedPages.push({
                        initialIndex: i + 1,
                        slideId: `temp_${Date.now()}_${i}`,
                        order: i + 1,
                        sourcePdfId: sourceId,
                        sourceIndex: i,
                        pageProxy,
                    });
                }

                this.pageOrder.set(orderedPages);
            }
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isPdfLoading.set(false);
        }
    }

    ngOnDestroy() {
        this.attachmentSub?.unsubscribe();
        this.attachmentUnitSub?.unsubscribe();

        this.sourcePDFs().forEach((source) => {
            URL.revokeObjectURL(source.url);
        });
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
    hiddenPagesChanged() {
        const initial = this.initialHiddenPages()!;
        const current = this.hiddenPages()!;
        return JSON.stringify(initial) !== JSON.stringify(current);
    }

    /**
     * Retrieves an array of hidden page objects directly from the hiddenPages signal.
     *
     * @returns An array of HiddenPage objects representing the hidden pages.
     */
    getHiddenPages(): HiddenPage[] {
        return Object.entries(this.hiddenPages()).map(([slideId, pageData]) => ({
            slideId,
            date: pageData.date,
            exerciseId: pageData.exerciseId ?? null,
        }));
    }

    /**
     * Applies the operations directly to the original PDF document
     * Operations are applied sequentially: deletions, merges, and reordering
     * Hidden slides are only removed in the student version
     * @param studentVersion Whether to create a student version
     * @returns A Promise that resolves to an object with instructor and student PDFs
     */
    async applyOperations(studentVersion: boolean = false): Promise<{
        instructorPdf: PDFDocument;
        studentPdf: PDFDocument | null;
    }> {
        const originalSource = this.sourcePDFs().get('original');
        if (!originalSource) {
            throw new Error('Original PDF source not found');
        }

        const instructorPdf = await PDFDocument.load(await originalSource.pdfDocument.save());

        const slideToPageMap = new Map();
        this.pageOrder()
            .filter((page) => page.sourcePdfId === 'original')
            .forEach((page) => {
                slideToPageMap.set(page.slideId, page.sourceIndex);
            });

        const sortedOperations = [...this.operations].sort((a, b) => a.timestamp.valueOf() - b.timestamp.valueOf());

        for (const operation of sortedOperations) {
            switch (operation.type) {
                case 'DELETE': {
                    const { slideIds } = operation.data;

                    const pageIndicesToDelete = [];

                    for (const slideId of slideIds) {
                        const originalIndex = slideToPageMap.get(slideId);
                        if (originalIndex !== undefined) {
                            pageIndicesToDelete.push(originalIndex);
                        }
                    }

                    pageIndicesToDelete.sort((a, b) => b - a);

                    for (const pageIndex of pageIndicesToDelete) {
                        instructorPdf.removePage(pageIndex);
                    }

                    for (const slideId of slideIds) {
                        slideToPageMap.delete(slideId);
                    }

                    const remainingSlides = Array.from(slideToPageMap.entries());
                    for (const [slideId, pageIndex] of remainingSlides) {
                        const newIndex = pageIndex - pageIndicesToDelete.filter((delIndex) => delIndex < pageIndex).length;
                        slideToPageMap.set(slideId, newIndex);
                    }
                    break;
                }

                case 'MERGE': {
                    const { sourceId } = operation.data;
                    const sourcePdf = this.sourcePDFs().get(sourceId);
                    if (sourcePdf) {
                        const pageIndices = Array.from({ length: sourcePdf.pdfDocument.getPageCount() }, (_, i) => i);
                        const copiedPages = await instructorPdf.copyPages(sourcePdf.pdfDocument, pageIndices);
                        for (const page of copiedPages) {
                            instructorPdf.addPage(page);
                        }
                    }

                    const mergePages = this.pageOrder().filter((page) => page.sourcePdfId === sourceId);

                    const baseIndex = instructorPdf.getPageCount() - mergePages.length;

                    mergePages.forEach((page, index) => {
                        slideToPageMap.set(page.slideId, baseIndex + index);
                    });
                    break;
                }

                case 'REORDER': {
                    const { pageOrder } = operation.data;

                    const totalPages = instructorPdf.getPageCount();
                    const pageObjects = Array.from({ length: totalPages }, (_, i) => instructorPdf.getPage(i));

                    for (let i = totalPages - 1; i >= 0; i--) {
                        instructorPdf.removePage(i);
                    }

                    const slideToOrderMap = new Map();
                    pageOrder.forEach((item: { slideId: string; order: number }) => {
                        slideToOrderMap.set(item.slideId, item.order);
                    });

                    const reorderEntries = Array.from(slideToPageMap.entries()).map(([slideId, originalIndex]) => [
                        slideId,
                        originalIndex,
                        slideToOrderMap.get(slideId) || Number.MAX_SAFE_INTEGER,
                    ]);

                    reorderEntries.sort((a, b) => (a[2] as number) - (b[2] as number));

                    const newSlideToPageMap = new Map();

                    for (let i = 0; i < reorderEntries.length; i++) {
                        const [slideId, originalIndex] = reorderEntries[i];
                        if (originalIndex !== undefined && originalIndex < pageObjects.length) {
                            instructorPdf.addPage(pageObjects[originalIndex as number]);
                            newSlideToPageMap.set(slideId, i); // Set the new index
                        }
                    }

                    slideToPageMap.clear();
                    for (const [slideId, newIndex] of newSlideToPageMap.entries()) {
                        slideToPageMap.set(slideId, newIndex);
                    }
                    break;
                }
            }
        }

        // Create student version if requested
        let studentPdf = null;
        if (studentVersion) {
            const hiddenSlideIds = Object.keys(this.hiddenPages());

            if (hiddenSlideIds.length > 0) {
                studentPdf = await PDFDocument.load(await instructorPdf.save());

                const finalPageOrder = await this.getFinalPageOrder();

                const slideToFinalPositionMap = new Map();
                finalPageOrder.forEach((page, index) => {
                    slideToFinalPositionMap.set(page.slideId, index);
                });

                const hiddenPageIndices = hiddenSlideIds
                    .map((slideId) => slideToFinalPositionMap.get(slideId))
                    .filter((index) => index !== undefined)
                    .sort((a, b) => (b as number) - (a as number));

                for (const pageIndex of hiddenPageIndices) {
                    if (pageIndex !== undefined) {
                        studentPdf.removePage(pageIndex);
                    }
                }
            }
        }

        return { instructorPdf, studentPdf };
    }

    /**
     * Calculate the final page order after applying all operations
     * @returns Promise<OrderedPage[]> The final page order
     */
    async getFinalPageOrder(): Promise<OrderedPage[]> {
        let workingPageOrder = [...this.pageOrder()];

        const sortedOperations = [...this.operations].sort((a, b) => a.timestamp.valueOf() - b.timestamp.valueOf());

        for (const operation of sortedOperations) {
            if (operation.type === 'DELETE') {
                const { slideIds } = operation.data;
                workingPageOrder = workingPageOrder.filter((page) => !slideIds.includes(page.slideId));
            } else if (operation.type === 'REORDER') {
                const { pageOrder } = operation.data;
                const orderMap = new Map();
                pageOrder.forEach((item: { slideId: string; order: number }) => {
                    orderMap.set(item.slideId, item.order);
                });

                for (const page of workingPageOrder) {
                    if (orderMap.has(page.slideId)) {
                        page.order = orderMap.get(page.slideId);
                    }
                }

                workingPageOrder.sort((a, b) => a.order - b.order);
            }
        }

        return workingPageOrder.map((page, index) => ({
            ...page,
            order: index + 1,
        }));
    }

    /**
     * Updates the existing attachment file or creates a student version of the attachment with hidden files.
     */
    async updateAttachmentWithFile(): Promise<void> {
        this.isSaving.set(true);

        try {
            const pdfName = this.attachment()?.name ?? this.attachmentUnit()?.name ?? '';
            const { instructorPdf, studentPdf } = await this.applyOperations(true);

            const instructorBytes = await instructorPdf.save();
            const instructorPdfFile = new File([instructorBytes], `${pdfName}.pdf`, { type: 'application/pdf' });

            if (instructorPdfFile.size > MAX_FILE_SIZE) {
                this.alertService.error('artemisApp.attachment.pdfPreview.fileSizeError');
                this.isSaving.set(false);
                return;
            }

            if (this.attachment()) {
                this.attachmentToBeEdited.set(this.attachment());
                this.attachmentToBeEdited()!.version!++;
                this.attachmentToBeEdited()!.uploadDate = dayjs();

                this.attachmentService.update(this.attachmentToBeEdited()!.id!, this.attachmentToBeEdited()!, instructorPdfFile).subscribe({
                    next: () => {
                        this.finishSaving();
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
                formData.append('file', instructorPdfFile);
                formData.append('attachment', objectToJsonBlob(this.attachmentToBeEdited()!));
                formData.append('attachmentUnit', objectToJsonBlob(this.attachmentUnit()!));

                const finalPageOrder = await this.getFinalPageOrder();

                formData.append(
                    'pageOrder',
                    JSON.stringify(
                        finalPageOrder.map((page) => ({
                            slideId: page.slideId,
                            order: page.order,
                        })),
                    ),
                );

                const hiddenPages = this.getHiddenPages();
                if (hiddenPages.length > 0) {
                    formData.append('hiddenPages', JSON.stringify(hiddenPages));
                }

                this.attachmentUnitService.update(this.attachmentUnit()!.lecture!.id!, this.attachmentUnit()!.id!, formData).subscribe({
                    next: () => {
                        if (studentPdf && hiddenPages.length > 0) {
                            this.updateAttachmentUnitStudentVersion(studentPdf, pdfName);
                        } else {
                            this.finishSaving();
                        }
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
     * Updates only the student version of the attachment unit's attachment
     * @param studentPdf The student PDF document
     * @param pdfName The base name for the PDF file
     */
    private updateAttachmentUnitStudentVersion(studentPdf: PDFDocument, pdfName: string): void {
        studentPdf
            .save()
            .then((studentBytes) => {
                const studentPdfFile = new File([studentBytes], `${pdfName}_student.pdf`, { type: 'application/pdf' });

                const formData = new FormData();
                formData.append('studentVersion', studentPdfFile);

                this.attachmentUnitService.updateStudentVersion(this.attachmentUnit()!.lecture!.id!, this.attachmentUnit()!.id!, formData).subscribe({
                    next: () => this.finishSaving(),
                    error: (error) => {
                        this.isSaving.set(false);
                        this.alertService.error('artemisApp.attachment.pdfPreview.studentVersionUpdateError', { error: error.message });
                    },
                });
            })
            .catch((error) => {
                this.isSaving.set(false);
                this.alertService.error('artemisApp.attachment.pdfPreview.studentVersionCreateError', { error: error.message });
            });
    }

    /**
     * Finishes the saving process and resets state
     */
    private finishSaving(): void {
        this.isSaving.set(false);
        this.operations = [];
        this.hasOperations.set(false);
        this.isFileChanged.set(false);
        this.alertService.success('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
        this.navigateToCourseManagement();
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
     * Deletes selected slides by removing them from the page order and hidden pages.
     */
    deleteSelectedSlides() {
        try {
            this.isPdfLoading.set(true);
            const slideIds = Array.from(this.selectedPages()).map((page) => page.slideId);

            this.operations.push({
                type: 'DELETE',
                timestamp: dayjs(),
                data: { slideIds },
            });

            const remainingPages = this.pageOrder().filter((page) => !slideIds.includes(page.slideId));

            const updatedPageOrder = remainingPages.map((page, index) => ({
                ...page,
                order: index + 1,
            }));

            this.operations.push({
                type: 'REORDER',
                timestamp: dayjs().add(1, 'millisecond'), // Just after the DELETE
                data: {
                    pageOrder: updatedPageOrder.map((page) => ({
                        slideId: page.slideId,
                        order: page.order,
                    })),
                },
            });

            this.hasOperations.set(true);

            this.pageOrder.set(updatedPageOrder);

            this.hiddenPages.update((current) => {
                const updated = { ...current };
                slideIds.forEach((id) => delete updated[id]);
                return updated;
            });

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
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];

        if (file!.type !== 'application/pdf') {
            this.alertService.error('artemisApp.attachment.pdfPreview.invalidFileType');
            input.value = '';
            return;
        }

        this.isPdfLoading.set(true);
        this.appendFile.set(true);
        try {
            const newPdfBytes = await file!.arrayBuffer();
            const objectUrl = URL.createObjectURL(file!);

            const mergeSourceId = `merge_${Date.now()}`;
            await this.loadPdf(objectUrl, newPdfBytes, mergeSourceId, undefined, true);

            this.selectedPages.set(new Set());
        } catch (error) {
            this.alertService.error('artemisApp.attachment.pdfPreview.mergeFailedError', { error: error.message });
        } finally {
            this.isPdfLoading.set(false);
            this.fileInput()!.nativeElement.value = '';
        }
    }

    /**
     * Shows previously hidden pages by removing them from the hidden pages map
     * @param selectedPages The set of pages to be made visible
     */
    showPages(selectedPages: Set<OrderedPage>): void {
        const slideIds = Array.from(selectedPages).map((page) => page.slideId);

        this.operations.push({
            type: 'SHOW',
            timestamp: dayjs(),
            data: { slideIds },
        });
        this.hasOperations.set(true);

        this.hiddenPages.update((current) => {
            const updated = { ...current };
            slideIds.forEach((id) => delete updated[id]);
            return updated;
        });

        this.selectedPages.set(new Set());
    }

    /**
     * Handles the reception of one or more hidden pages from the date box component
     * @param hiddenPageData A single HiddenPage or an array of HiddenPages
     */
    hidePages(hiddenPageData: HiddenPage | HiddenPage[]): void {
        const pages = Array.isArray(hiddenPageData) ? hiddenPageData : [hiddenPageData];

        this.operations.push({
            type: 'HIDE',
            timestamp: dayjs(),
            data: { pages },
        });
        this.hasOperations.set(true);

        this.hiddenPages.update((currentMap) => {
            const updatedMap = { ...currentMap };
            pages.forEach((page) => {
                updatedMap[page.slideId] = {
                    date: page.date,
                    exerciseId: page.exerciseId,
                };
            });
            return updatedMap;
        });

        this.selectedPages.set(new Set());
    }

    /**
     * Handles page order changes from the thumbnail grid component
     * @param newOrder The new page order
     */
    onPageOrderChange(newOrder: OrderedPage[]): void {
        this.operations.push({
            type: 'REORDER',
            timestamp: dayjs(),
            data: {
                pageOrder: newOrder.map((page) => ({
                    slideId: page.slideId,
                    order: page.order,
                })),
            },
        });
        this.hasOperations.set(true);

        this.pageOrder.set(newOrder);
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
