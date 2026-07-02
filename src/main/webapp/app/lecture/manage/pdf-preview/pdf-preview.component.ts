import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AttachmentService } from 'app/lecture/manage/services/attachment.service';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { onError } from 'app/foundation/util/global.utils';
import { AlertService } from 'app/foundation/service/alert.service';
import { Subject, Subscription } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { faCancel, faExclamationCircle, faEye, faEyeSlash, faFileImport, faSave, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { objectToJsonBlob } from 'app/foundation/util/blob-util';
import { MAX_FILE_SIZE } from 'app/foundation/constants/input.constants';
import { PdfPreviewThumbnailGridComponent } from 'app/lecture/manage/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { PdfEngineService } from 'app/core/pdf/pdf-engine.service';
import type { PdfDocumentObject } from '@embedpdf/models';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbPopover, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { Slide } from 'app/lecture/shared/entities/lecture-unit/slide.model';
import { finalize } from 'rxjs/operators';
import { ConfirmAutofocusButtonComponent } from 'app/shared-ui/components/buttons/confirm-autofocus-button/confirm-autofocus-button.component';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { PdfPreviewDateBoxComponent } from 'app/lecture/manage/pdf-preview/pdf-preview-date-box/pdf-preview-date-box.component';

interface PdfOperation {
    type: 'MERGE' | 'DELETE' | 'HIDE' | 'SHOW' | 'REORDER';
    timestamp: dayjs.Dayjs;
    data: any;
}

export interface PDFSource {
    id: string;
    /** The document handle opened in the shared PDFium engine (cached by {@link id}). */
    doc: PdfDocumentObject;
    url: string;
}

export interface OrderedPage {
    initialIndex: number;
    slideId: string;
    order: number;
    sourcePdfId: string;
    sourceIndex: number;
    /** Handle of the source document this page belongs to, used by the thumbnail grid to render it. */
    sourceDoc: PdfDocumentObject;
}

export interface HiddenPage {
    slideId: string;
    date: dayjs.Dayjs;
    exerciseId: number | undefined;
}

export interface HiddenPageMap {
    [slideId: string]: {
        date: dayjs.Dayjs;
        exerciseId: number | undefined;
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
        NgbPopover,
        NgbTooltipModule,
    ],
})
export class PdfPreviewComponent implements OnInit, OnDestroy {
    fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');
    showPopover = viewChild.required<NgbPopover>('showPopover');

    attachmentSub: Subscription;
    attachmentVideoUnitSub: Subscription;

    FOREVER = dayjs('9999-12-31');

    protected readonly ButtonType = ButtonType;
    protected readonly Object = Object;
    protected readonly Array = Array;

    readonly courseId = signal<number>(undefined!);

    // Signals
    attachment = signal<Attachment | undefined>(undefined);
    attachmentVideoUnit = signal<AttachmentVideoUnit | undefined>(undefined);
    isPdfLoading = signal<boolean>(false);
    attachmentToBeEdited = signal<Attachment | undefined>(undefined);
    currentPdfUrl = signal<string | undefined>(undefined);
    totalPages = signal<number>(0);
    isAppendingFile = signal<boolean>(false);
    isFileChanged = signal<boolean>(false);
    selectedPages = signal<Set<OrderedPage>>(new Set());
    initialHiddenPages = signal<HiddenPageMap>({});
    hiddenPages = signal<HiddenPageMap>({});
    isSaving = signal<boolean>(false);
    pageOrder = signal<OrderedPage[]>([]);
    sourcePDFs = signal<Map<string, PDFSource>>(new Map());
    hasOperations = signal<boolean>(false);
    operations = signal<PdfOperation[]>([]);

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
        return this.operations().length > 0 || this.hiddenPagesChanged() || this.pageOrderChanged() || this.isFileChanged();
    });
    sortedHiddenSelectedPages = computed(() => {
        return Array.from(this.selectedPages())
            .filter((page) => this.hiddenPages()[page.slideId])
            .sort((a, b) => a.order - b.order);
    });

    // Injected services
    private readonly route = inject(ActivatedRoute);
    private readonly attachmentService = inject(AttachmentService);
    private readonly attachmentVideoUnitService = inject(AttachmentVideoUnitService);
    private readonly lectureUnitService = inject(LectureUnitService);
    private readonly alertService = inject(AlertService);
    private readonly router = inject(Router);
    private readonly pdfEngineService = inject(PdfEngineService);

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
        this.courseId.set(Number(this.route?.parent?.snapshot.paramMap.get('courseId')));
        this.route.data.subscribe((data) => {
            if ('attachment' in data) {
                this.attachment.set(data.attachment);
                this.fetchPdfFile('attachment');
            } else if ('attachmentVideoUnit' in data) {
                this.attachmentVideoUnit.set(data.attachmentVideoUnit);
                const { slides } = data.attachmentVideoUnit;

                // Store hidden pages information
                const hiddenPagesMap: HiddenPageMap = Object.fromEntries(
                    slides
                        .filter((page: Slide) => page.hidden)
                        .map((page: Slide) => [
                            page.id,
                            {
                                date: dayjs(page.hidden),
                                exerciseId: page.exercise?.id ?? undefined,
                            },
                        ]),
                );
                this.initialHiddenPages.set(hiddenPagesMap);
                this.hiddenPages.set({ ...hiddenPagesMap });

                this.fetchPdfFile('attachmentVideoUnit', slides);
            } else {
                this.isPdfLoading.set(false);
            }
        });
    }

    /**
     * Fetches a PDF file based on the specified file type (attachment or attachmentVideoUnit).
     * @param fileType The type of file to fetch ('attachment' or 'attachmentVideoUnit')
     * @param slides Optional array of slides (only used for attachmentVideoUnit)
     */
    private fetchPdfFile(fileType: 'attachment' | 'attachmentVideoUnit', slides?: Slide[]): void {
        let subscription: Subscription;

        if (fileType === 'attachment') {
            subscription = this.attachmentService
                .getAttachmentFile(this.courseId(), this.attachment()!.id!)
                .pipe(finalize(() => this.isPdfLoading.set(false)))
                .subscribe({
                    next: (blob: Blob) => this.processPdfBlob(blob, slides),
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });

            this.attachmentSub = subscription;
        } else {
            subscription = this.attachmentVideoUnitService
                .getAttachmentFile(this.courseId(), this.attachmentVideoUnit()!.id!)
                .pipe(finalize(() => this.isPdfLoading.set(false)))
                .subscribe({
                    next: (blob: Blob) => this.processPdfBlob(blob, slides),
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });

            this.attachmentVideoUnitSub = subscription;
        }
    }

    /**
     * Processes a PDF blob by creating a URL object and loading the PDF.
     * @param blob The PDF file as a Blob
     * @param existingSlides Optional array of existing slides
     */
    private processPdfBlob(blob: Blob, existingSlides?: Slide[]): void {
        const url = URL.createObjectURL(blob);
        this.currentPdfUrl.set(url);

        blob.arrayBuffer()
            .then((arrayBuffer) => {
                this.loadPdf(url, arrayBuffer, 'original', existingSlides);
            })
            .catch((error) => {
                onError(this.alertService, error);
                this.isPdfLoading.set(false);
            });
    }

    /**
     * Loads a PDF from a provided URL and creates/updates page order.
     * @param fileUrl The URL of the file to load
     * @param arrayBuffer The PDF file as an ArrayBuffer
     * @param sourceId Identifier for the source PDF, or 'original' for the initial PDF
     * @param existingSlides Optional array of existing slides (for attachmentVideoUnit)
     * @param append Whether to append the new pages to existing ones
     */
    async loadPdf(fileUrl: string, arrayBuffer: ArrayBuffer, sourceId: string = 'original', existingSlides?: Slide[], append: boolean = false): Promise<void> {
        this.isPdfLoading.set(true);
        try {
            const engine = await this.pdfEngineService.getEngine();
            const doc = await engine.openDocumentBuffer({ id: sourceId, content: arrayBuffer }).toPromise();
            const pageCount = doc.pageCount;

            this.sourcePDFs.update((sources) => {
                const newSources = new Map(sources);
                newSources.set(sourceId, { id: sourceId, doc, url: fileUrl });
                return newSources;
            });

            this.totalPages.set(this.totalPages() + pageCount);

            if (append) {
                this.operations.update((ops) => [
                    ...ops,
                    {
                        type: 'MERGE',
                        timestamp: dayjs(),
                        data: {
                            sourceId,
                            pageCount,
                        },
                    },
                ]);
                this.hasOperations.set(true);

                const currentPageCount = this.pageOrder().length;
                const newPages: OrderedPage[] = [];

                for (let i = 0; i < pageCount; i++) {
                    newPages.push({
                        slideId: `temp_${Date.now()}_${i}`,
                        initialIndex: currentPageCount + i + 1,
                        order: currentPageCount + i + 1,
                        sourcePdfId: sourceId,
                        sourceIndex: i,
                        sourceDoc: doc,
                    });
                }

                this.pageOrder.update((pages) => [...pages, ...newPages]);
                this.isFileChanged.set(true);
            } else if (existingSlides && existingSlides.length > 0) {
                const orderedPages: OrderedPage[] = existingSlides.map((slide) => ({
                    initialIndex: slide.slideNumber!,
                    slideId: slide.id!.toString(),
                    order: slide.slideNumber!,
                    sourcePdfId: sourceId,
                    sourceIndex: slide.slideNumber! - 1,
                    sourceDoc: doc,
                }));

                orderedPages.sort((a, b) => a.order - b.order);
                this.pageOrder.set(orderedPages);
            } else {
                const orderedPages: OrderedPage[] = [];

                for (let i = 0; i < pageCount; i++) {
                    orderedPages.push({
                        initialIndex: i + 1,
                        slideId: `temp_${Date.now()}_${i}`,
                        order: i + 1,
                        sourcePdfId: sourceId,
                        sourceIndex: i,
                        sourceDoc: doc,
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
        this.attachmentVideoUnitSub?.unsubscribe();

        const sources = this.sourcePDFs();
        sources.forEach((source) => {
            URL.revokeObjectURL(source.url);
        });

        // Close the documents opened in the shared PDFium engine so their ids are freed and memory is released.
        if (sources.size > 0) {
            this.pdfEngineService
                .getEngine()
                .then((engine) => {
                    sources.forEach((source) => {
                        engine
                            .closeDocument(source.doc)
                            .toPromise()
                            .catch(() => {});
                    });
                })
                .catch(() => {});
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
            exerciseId: pageData.exerciseId ?? undefined,
        }));
    }

    /**
     * Builds the bytes of a PDF composed of the given pages, in order, by importing each page from its
     * source document in the shared PDFium engine. This replaces the previous pdf-lib replay pipeline:
     * because every {@link OrderedPage} already knows its source document and original page index, the final
     * document is simply a merge of those pages in the desired order (PDFium preserves the requested order).
     *
     * @param order the pages to include, in their final order
     * @returns the serialized PDF as an ArrayBuffer
     */
    private async buildPdfBytes(order: OrderedPage[]): Promise<ArrayBuffer> {
        const engine = await this.pdfEngineService.getEngine();
        const mergeConfigs = order.map((page) => ({ docId: page.sourcePdfId, pageIndices: [page.sourceIndex] }));
        const merged = await engine.mergePages(mergeConfigs).toPromise();
        return merged.content;
    }

    /**
     * Wraps serialized PDF bytes in a {@link File} for upload.
     */
    private bytesToFile(bytes: ArrayBuffer, fileName: string, isStudentVersion: boolean = false): File {
        const suffix = isStudentVersion ? '_student' : '';
        return new File([bytes], `${fileName}${suffix}.pdf`, { type: 'application/pdf' });
    }

    /**
     * Produces the instructor PDF (all pages in their final order) and, when requested, the student PDF
     * (the same order with hidden pages removed). Both are built from the final page order, which already
     * reflects every delete/reorder/merge operation.
     */
    async applyOperations(studentVersion: boolean = false): Promise<{
        instructorBytes: ArrayBuffer;
        studentBytes: ArrayBuffer | undefined;
    }> {
        if (!this.sourcePDFs().get('original')) {
            throw new Error('Original PDF source not found');
        }

        const finalOrder = await this.getFinalPageOrder();
        if (finalOrder.length === 0) {
            throw new Error('Cannot save a PDF without pages');
        }
        const instructorBytes = await this.buildPdfBytes(finalOrder);

        let studentBytes: ArrayBuffer | undefined = undefined;
        if (studentVersion) {
            const hiddenSlideIds = new Set(Object.keys(this.hiddenPages()));
            if (hiddenSlideIds.size > 0) {
                const visibleOrder = finalOrder.filter((page) => !hiddenSlideIds.has(page.slideId));
                if (visibleOrder.length === 0) {
                    // Every page is hidden: a student PDF would be empty (a 0-page document), so reject the save
                    // instead of uploading an invalid student version.
                    throw new Error('Cannot create a student version with no visible pages');
                }
                studentBytes = await this.buildPdfBytes(visibleOrder);
            }
        }

        return { instructorBytes, studentBytes };
    }

    /**
     * Updates the attachment with both instructor and student versions
     * Refactored version combining updateAttachmentWithFile and updateAttachmentVideoUnitStudentVersion
     */
    async updateAttachmentWithFile(): Promise<void> {
        // Validate hidden slides dates before proceeding
        if (!this.validateHiddenSlidesDates()) {
            return;
        }

        this.isSaving.set(true);

        try {
            const pdfName = this.attachment()?.name ?? this.attachmentVideoUnit()?.name ?? '';
            const { instructorBytes, studentBytes } = await this.applyOperations(true);

            const instructorPdfFile = this.bytesToFile(instructorBytes, pdfName);

            if (instructorPdfFile.size > MAX_FILE_SIZE) {
                this.alertService.error('artemisApp.attachment.pdfPreview.fileSizeError');
                this.isSaving.set(false);
                return;
            }

            if (this.attachment()) {
                await this.updateAttachment(instructorPdfFile);
            } else if (this.attachmentVideoUnit()) {
                const hiddenPages = this.getHiddenPages();
                await this.updateAttachmentVideoUnit(instructorPdfFile, hiddenPages);

                if (studentBytes && hiddenPages.length > 0) {
                    const studentPdfFile = this.bytesToFile(studentBytes, pdfName, true);
                    await this.updateStudentVersion(studentPdfFile);
                } else {
                    this.finishSaving();
                }
            }
        } catch (error) {
            this.isSaving.set(false);
            this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
        }
    }

    /**
     * Updates a regular attachment
     */
    private updateAttachment(instructorPdfFile: File): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            this.attachmentToBeEdited.set(this.attachment());
            this.attachmentToBeEdited()!.version!++;
            this.attachmentToBeEdited()!.uploadDate = dayjs();

            this.attachmentService.update(this.attachmentToBeEdited()!.id!, this.attachmentToBeEdited()!, instructorPdfFile).subscribe({
                next: () => {
                    this.finishSaving();
                    resolve();
                },
                error: (error) => {
                    this.isSaving.set(false);
                    this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                    reject(error);
                },
            });
        });
    }

    /**
     * Updates an attachment video unit
     */
    private async updateAttachmentVideoUnit(instructorPdfFile: File, hiddenPages: HiddenPage[]): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            this.attachmentToBeEdited.set(this.attachmentVideoUnit()!.attachment!);
            this.attachmentToBeEdited()!.uploadDate = dayjs();

            const formData = new FormData();
            formData.append('file', instructorPdfFile);
            formData.append('attachment', objectToJsonBlob(this.attachmentToBeEdited()!));
            formData.append('attachmentVideoUnit', objectToJsonBlob(this.attachmentVideoUnit()!));

            this.getFinalPageOrder().then((finalPageOrder) => {
                formData.append(
                    'pageOrder',
                    new Blob(
                        [
                            JSON.stringify(
                                finalPageOrder.map((page) => ({
                                    slideId: page.slideId,
                                    order: page.order,
                                })),
                            ),
                        ],
                        { type: 'application/json' },
                    ),
                );

                if (hiddenPages.length > 0) {
                    formData.append('hiddenPages', new Blob([JSON.stringify(hiddenPages)], { type: 'application/json' }));
                }

                this.attachmentVideoUnitService.update(this.attachmentVideoUnit()!.lecture!.id!, this.attachmentVideoUnit()!.id!, formData).subscribe({
                    next: () => resolve(),
                    error: (error) => {
                        this.isSaving.set(false);
                        this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                        reject(error);
                    },
                });
            });
        });
    }

    /**
     * Updates only the student version of the attachment video unit
     */
    private updateStudentVersion(studentPdfFile: File): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            const formData = new FormData();
            formData.append('studentVersion', studentPdfFile);

            this.attachmentVideoUnitService.updateStudentVersion(this.attachmentVideoUnit()!.lecture!.id!, this.attachmentVideoUnit()!.id!, formData).subscribe({
                next: () => {
                    this.finishSaving();
                    resolve();
                },
                error: (error) => {
                    this.isSaving.set(false);
                    this.alertService.error('artemisApp.attachment.pdfPreview.studentVersionUpdateError', { error: error.message });
                    reject(error);
                },
            });
        });
    }

    /**
     * Finishes the saving process and resets state
     */
    private finishSaving(): void {
        this.isSaving.set(false);
        this.operations.set([]);
        this.hasOperations.set(false);
        this.isFileChanged.set(false);
        this.alertService.success('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
        this.navigateToCourseManagement();
    }

    /**
     * Calculate the final page order after applying all operations
     * @returns Promise<OrderedPage[]> The final page order
     */
    async getFinalPageOrder(): Promise<OrderedPage[]> {
        let workingPageOrder = [...this.pageOrder()];

        const sortedOperations = [...this.operations()].sort((a, b) => a.timestamp.valueOf() - b.timestamp.valueOf());

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
     * Deletes the attachment file if it exists, or deletes the attachment video unit if it exists.
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
        } else if (this.attachmentVideoUnit()) {
            this.lectureUnitService.delete(this.attachmentVideoUnit()!.id!, this.attachmentVideoUnit()!.lecture!.id!).subscribe({
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

            this.operations.update((ops) => [
                ...ops,
                {
                    type: 'DELETE',
                    timestamp: dayjs(),
                    data: { slideIds },
                },
            ]);

            const remainingPages = this.pageOrder().filter((page) => !slideIds.includes(page.slideId));

            const updatedPageOrder = remainingPages.map((page, index) => ({
                ...page,
                order: index + 1,
            }));

            this.operations.update((ops) => [
                ...ops,
                {
                    type: 'REORDER',
                    timestamp: dayjs().add(1, 'millisecond'), // Just after the DELETE
                    data: {
                        pageOrder: updatedPageOrder.map((page) => ({
                            slideId: page.slideId,
                            order: page.order,
                        })),
                    },
                },
            ]);

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
        this.isAppendingFile.set(true);
        let objectUrl: string | undefined = undefined;
        try {
            const newPdfBytes = await file!.arrayBuffer();
            objectUrl = URL.createObjectURL(file!);
            const mergeSourceId = `merge_${Date.now()}`;
            await this.loadPdf(objectUrl, newPdfBytes, mergeSourceId, undefined, true);
            this.selectedPages.set(new Set());
        } catch (error) {
            this.alertService.error('artemisApp.attachment.pdfPreview.mergeFailedError', { error: error.message });
            if (objectUrl) {
                URL.revokeObjectURL(objectUrl);
            }
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

        this.operations.update((ops) => [
            ...ops,
            {
                type: 'SHOW',
                timestamp: dayjs(),
                data: { slideIds },
            },
        ]);
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

        this.operations.update((ops) => [
            ...ops,
            {
                type: 'HIDE',
                timestamp: dayjs(),
                data: { pages },
            },
        ]);
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
     * Validates that all hidden slide dates are valid (not in the past)
     * @returns true if all dates are valid, false otherwise
     */
    private validateHiddenSlidesDates(): boolean {
        const now = dayjs();
        const hiddenPagesMap = this.hiddenPages();

        // Find affected pages by mapping slideIds to page orders for error messages
        const slideIdToOrderMap = new Map<string, number>();
        this.pageOrder().forEach((page) => {
            slideIdToOrderMap.set(page.slideId, page.order);
        });

        // Collect invalid pages to show in error message
        const invalidPageOrders: number[] = [];

        for (const [slideId, pageData] of Object.entries(hiddenPagesMap)) {
            // Check if the date is in the past
            if (pageData.date.isBefore(now)) {
                const pageOrder = slideIdToOrderMap.get(slideId);
                if (pageOrder !== undefined) {
                    invalidPageOrders.push(pageOrder);
                }
            }
        }

        // If we found invalid pages, show error and return false
        if (invalidPageOrders.length > 0) {
            invalidPageOrders.sort((a, b) => a - b);
            const pagesList = invalidPageOrders.join(', ');
            this.alertService.error('artemisApp.attachment.pdfPreview.dateBox.dateErrorWithPages', { param: pagesList });
            return false;
        }

        return true;
    }

    /**
     * Handles page order changes from the thumbnail grid component
     * @param newOrder The new page order
     */
    onPageOrderChange(newOrder: OrderedPage[]): void {
        this.operations.update((ops) => [
            ...ops,
            {
                type: 'REORDER',
                timestamp: dayjs(),
                data: {
                    pageOrder: newOrder.map((page) => ({
                        slideId: page.slideId,
                        order: page.order,
                    })),
                },
            },
        ]);
        this.hasOperations.set(true);

        this.pageOrder.set(newOrder);
    }

    /**
     * Navigates to the appropriate course management page based on context.
     */
    navigateToCourseManagement(): void {
        if (this.attachment()) {
            this.router.navigate(['course-management', this.courseId(), 'lectures', this.attachment()!.lecture!.id, 'attachments']);
        } else {
            this.router.navigate(['course-management', this.courseId(), 'lectures', this.attachmentVideoUnit()!.lecture!.id, 'unit-management']);
        }
    }
}
