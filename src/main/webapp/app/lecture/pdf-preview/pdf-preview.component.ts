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
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Slide } from 'app/entities/lecture-unit/slide.model';
import { finalize } from 'rxjs/operators';
import { ConfirmAutofocusButtonComponent } from 'app/shared/components/confirm-autofocus-button.component';
import { ButtonType } from 'app/shared/components/button.component';
import { PdfPreviewDateBoxComponent } from 'app/lecture/pdf-preview/pdf-preview-date-box/pdf-preview-date-box.component';
import { NgbModule, NgbPopover } from '@ng-bootstrap/ng-bootstrap';

type VisibilityAction = 'hide' | 'show';

export interface OrderedPage {
    pageIndex: number;
    slideId: string;
    order: number;
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
    initialHiddenPages = signal<HiddenPageMap>({});
    hiddenPages = signal<HiddenPageMap>({});
    isSaving = signal<boolean>(false);
    pageOrder = signal<OrderedPage[]>([]);

    allPagesSelected = computed(() => this.selectedPages().size === this.totalPages());
    pageOrderChanged = computed(() => {
        if (this.pageOrder().length === 0) return false;
        return this.pageOrder().some((page, index) => page.pageIndex !== index + 1);
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
                            this.currentPdfUrl.set(URL.createObjectURL(blob));
                        },
                        error: (error: HttpErrorResponse) => {
                            onError(this.alertService, error);
                        },
                    });
            } else if ('attachmentUnit' in data) {
                this.attachmentUnit.set(data.attachmentUnit);
                const { slides } = data.attachmentUnit;

                if (slides?.length > 0) {
                    this.pageOrder.set(
                        slides.map((slide: Slide, index: number) => ({
                            pageIndex: slide.slideNumber,
                            slideId: slide.id,
                            order: index + 1,
                        })),
                    );
                }

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
                            this.currentPdfBlob.set(blob);
                            this.currentPdfUrl.set(URL.createObjectURL(blob));
                        },
                        error: (error: HttpErrorResponse) => onError(this.alertService, error),
                    });
            } else {
                this.isPdfLoading.set(false);
            }
        });
    }

    ngOnDestroy() {
        this.attachmentSub?.unsubscribe();
        this.attachmentUnitSub?.unsubscribe();
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
     * Checks if any of the currently selected pages are in the hidden pages collection.
     */
    hasHiddenSelectedPages(): boolean {
        return Array.from(this.selectedPages()).some((page) => this.hiddenPages()[page.slideId]);
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
     * Updates the existing attachment file or creates a student version of the attachment with hidden files.
     */
    async updateAttachmentWithFile(): Promise<void> {
        this.isSaving.set(true);
        const pdfFileName = this.attachment()?.name ?? this.attachmentUnit()?.name ?? '';
        const pdfFile = new File([this.currentPdfBlob()!], `${pdfFileName}.pdf`, { type: 'application/pdf' });

        if (pdfFile.size > MAX_FILE_SIZE) {
            this.alertService.error('artemisApp.attachment.pdfPreview.fileSizeError');
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

            const finalHiddenPages = this.getHiddenPages();

            if (finalHiddenPages.length > 0) {
                const pdfFileWithHiddenPages = await this.createStudentVersionOfAttachment(finalHiddenPages);
                formData.append('studentVersion', pdfFileWithHiddenPages!);
                formData.append('hiddenPages', JSON.stringify(finalHiddenPages));
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
     * Deletes selected slides from the PDF viewer.
     */
    async deleteSelectedSlides() {
        this.isPdfLoading.set(true);
        try {
            const existingPdfBytes = await this.currentPdfBlob()!.arrayBuffer();
            const pdfDoc = await PDFDocument.load(existingPdfBytes);

            const selectedPages = Array.from(this.selectedPages());
            const slideIds = selectedPages.map((page) => page.slideId);
            const pageIndices = selectedPages
                .map((page) => page.pageIndex - 1) // PDF page indices are 0-based
                .sort((a, b) => b - a); // Sort in descending order for deletion

            this.updateHiddenPages(slideIds);

            pageIndices.forEach((pageIndex) => pdfDoc.removePage(pageIndex));

            this.updatePageOrderAfterDeletion(slideIds);

            this.isFileChanged.set(true);
            const pdfBytes = await pdfDoc.save();
            this.currentPdfBlob.set(new Blob([pdfBytes], { type: 'application/pdf' }));
            this.selectedPages.set(new Set());

            const objectUrl = URL.createObjectURL(this.currentPdfBlob()!);
            this.currentPdfUrl.set(objectUrl);
            this.appendFile.set(false);
            this.dialogErrorSource.next('');
        } catch (error) {
            this.alertService.error('artemisApp.attachment.pdfPreview.pageDeleteError', { error: error.message });
        } finally {
            this.isPdfLoading.set(false);
        }
    }

    /**
     * Updates the page order after deleting pages
     * @param deletedSlideIds Array of slide IDs that were deleted
     */
    updatePageOrderAfterDeletion(deletedSlideIds: string[]): void {
        // Filter out deleted pages from the page order
        const updatedOrder = this.pageOrder().filter((page) => !deletedSlideIds.includes(page.slideId));

        // Update page indices to maintain sequence
        updatedOrder.forEach((page, index) => {
            page.pageIndex = index + 1;
        });

        this.pageOrder.set(updatedOrder);
    }

    /**
     * Updates the mapping of hidden pages after deleting specified pages.
     *
     * @param slidesToDelete - An array of slide IDs to delete.
     */
    updateHiddenPages(slidesToDelete: string[]) {
        const updated: HiddenPageMap = {};

        Object.entries(this.hiddenPages()!).forEach(([slideId, data]) => {
            if (!slidesToDelete.includes(slideId)) {
                updated[slideId] = data;
            }
        });

        this.hiddenPages.set(updated);
    }

    /**
     * Creates a student version of the current PDF attachment by removing specified pages.
     *
     * @param hiddenPages - An array of hidden pages data
     * @returns A promise that resolves to a new `File` object representing the modified PDF, or undefined if an error occurs.
     */
    async createStudentVersionOfAttachment(hiddenPages: HiddenPage[]) {
        try {
            const fileName = this.attachmentUnit()!.attachment!.name;
            const existingPdfBytes = await this.currentPdfBlob()!.arrayBuffer();
            const hiddenPdfDoc = await PDFDocument.load(existingPdfBytes);

            // Map slide IDs to page indices for deletion
            const pagesToDelete = hiddenPages
                .map(({ slideId }) => {
                    const page = this.pageOrder().find((p) => p.slideId === slideId);
                    return page ? page.pageIndex - 1 : -1; // PDF page indices are 0-based
                })
                .filter((index) => index >= 0)
                .sort((a, b) => b - a);

            pagesToDelete.forEach((pageIndex) => {
                hiddenPdfDoc.removePage(pageIndex);
            });

            const pdfBytes = await hiddenPdfDoc.save();
            return new File([pdfBytes], `${fileName}.pdf`, { type: 'application/pdf' });
        } catch (error) {
            this.alertService.error('artemisApp.attachment.pdfPreview.pageDeleteError', { error: error.message });
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

            const currentPageCount = existingPdfDoc.getPageCount();
            const newPagesCount = newPdfDoc.getPageCount();

            const copiedPages = await existingPdfDoc.copyPages(newPdfDoc, newPdfDoc.getPageIndices());
            copiedPages.forEach((page) => existingPdfDoc.addPage(page));

            // Create temp IDs for new pages
            const currentOrder = this.pageOrder();
            const newEntries: OrderedPage[] = [];

            for (let i = 0; i < newPagesCount; i++) {
                const newPageIndex = currentPageCount + i + 1;
                newEntries.push({
                    slideId: `temp_${Date.now()}_${i}`, // Temporary ID until saved
                    pageIndex: newPageIndex,
                    order: currentOrder.length + i + 1,
                });
            }

            this.pageOrder.set([...currentOrder, ...newEntries]);

            this.isFileChanged.set(true);
            const mergedPdfBytes = await existingPdfDoc.save();
            this.currentPdfBlob.set(new Blob([mergedPdfBytes], { type: 'application/pdf' }));

            this.selectedPages.set(new Set());

            const objectUrl = URL.createObjectURL(this.currentPdfBlob()!);
            this.currentPdfUrl.set(objectUrl);
            this.appendFile.set(true);
        } catch (error) {
            this.alertService.error('artemisApp.attachment.pdfPreview.mergeFailedError', { error: error.message });
        } finally {
            this.isPdfLoading.set(false);
            this.fileInput()!.nativeElement.value = '';
        }
    }

    /**
     * Toggles visibility of selected pages by updating the hiddenPages map
     */
    toggleVisibility(action: VisibilityAction, selectedPages: Set<OrderedPage>): void {
        this.hiddenPages.update((currentMap) => {
            const updatedMap = { ...currentMap };

            selectedPages.forEach((page) => {
                if (action === 'hide') {
                    if (!updatedMap[page.slideId]) {
                        updatedMap[page.slideId] = {
                            date: dayjs(),
                            exerciseId: null,
                        };
                    }
                } else {
                    if (page.slideId in updatedMap) {
                        delete updatedMap[page.slideId];
                    }
                }
            });

            return updatedMap;
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

    /**
     * Handles the reception of one or more hidden pages from the date box component
     * @param hiddenPageData A single HiddenPage or an array of HiddenPages
     */
    onHiddenPagesReceived(hiddenPageData: HiddenPage | HiddenPage[]): void {
        const pages = Array.isArray(hiddenPageData) ? hiddenPageData : [hiddenPageData];

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
     * Handles updated page order from the thumbnail grid component
     * @param newOrder The updated page order
     */
    onPageOrderChanged(newOrder: OrderedPage[]): void {
        this.pageOrder.set(newOrder);
    }
}
