import { Component, ElementRef, OnDestroy, OnInit, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AttachmentService } from 'app/lecture/attachment.service';
import { Attachment } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { Subject, Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { HttpErrorResponse } from '@angular/common/http';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { faFileImport, faSave, faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { objectToJsonBlob } from 'app/utils/blob-util';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { PdfPreviewThumbnailGridComponent } from 'app/lecture/pdf-preview/pdf-preview-thumbnail-grid/pdf-preview-thumbnail-grid.component';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { PDFDocument } from 'pdf-lib';
import { cloneDeep } from 'lodash-es';

@Component({
    selector: 'jhi-pdf-preview-component',
    templateUrl: './pdf-preview.component.html',
    standalone: true,
    imports: [ArtemisSharedModule, PdfPreviewThumbnailGridComponent],
})
export class PdfPreviewComponent implements OnInit, OnDestroy {
    fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

    attachmentSub: Subscription;
    attachmentUnitSub: Subscription;

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
    selectedPages = signal<Set<number>>(new Set());
    hiddenPages = signal<Set<number>>(new Set());
    newHiddenPages = signal<Set<number>>(new Set());

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
    protected readonly faFileImport = faFileImport;
    protected readonly faSave = faSave;
    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;

    ngOnInit() {
        this.route.data.subscribe((data) => {
            this.course.set(data.course);

            if ('attachment' in data) {
                this.attachment.set(data.attachment);
                this.hiddenPages.set(new Set(data.attachment.hiddenPages?.split(',').map(Number) || []));
                this.attachmentSub = this.attachmentService.getAttachmentFile(this.course()!.id!, this.attachment()!.id!).subscribe({
                    next: (blob: Blob) => {
                        this.currentPdfBlob.set(blob);
                        this.currentPdfUrl.set(URL.createObjectURL(blob));
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
            } else if ('attachmentUnit' in data) {
                this.attachmentUnit.set(data.attachmentUnit);
                this.hiddenPages.set(new Set(data.attachmentUnit.attachment.hiddenPages?.split(',').map(Number) || []));
                this.attachmentUnitSub = this.attachmentUnitService.getAttachmentFile(this.course()!.id!, this.attachmentUnit()!.id!).subscribe({
                    next: (blob: Blob) => {
                        this.currentPdfBlob.set(blob);
                        this.currentPdfUrl.set(URL.createObjectURL(blob));
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
            }
        });
    }

    ngOnDestroy() {
        this.attachmentSub?.unsubscribe();
        this.attachmentUnitSub?.unsubscribe();
    }

    constructor() {
        effect(
            () => {
                this.hiddenPagesChanged();
            },
            { allowSignalWrites: true },
        );
    }

    /**
     * Checks if all pages are selected.
     * @returns True if the number of selected pages equals the total number of pages, otherwise false.
     */
    allPagesSelected() {
        return this.selectedPages().size === this.totalPages();
    }

    /**
     * Triggers the file input to select files.
     */
    triggerFileInput(): void {
        this.fileInput().nativeElement.click();
    }

    /**
     * Checks if there has been any change between the current set of hidden pages and the new set of hidden pages.
     *
     * @returns Returns true if the sets differ in size or if any element in `newHiddenPages` is not found in `hiddenPages`, otherwise false.
     */
    hiddenPagesChanged() {
        if (this.hiddenPages()!.size !== this.newHiddenPages()!.size) return true;

        for (const elem of this.newHiddenPages()!) {
            if (!this.hiddenPages()!.has(elem)) return true;
        }
        return false;
    }

    /**
     * Updates the existing attachment file or creates a new hidden version of the attachment.
     */
    updateAttachmentWithFile(): void {
        const pdfFile = new File([this.currentPdfBlob()!], 'updatedAttachment.pdf', { type: 'application/pdf' });

        if (pdfFile.size > MAX_FILE_SIZE) {
            this.alertService.error('artemisApp.attachment.pdfPreview.fileSizeError');
            return;
        }

        if (this.attachment()) {
            this.attachmentToBeEdited.set(this.attachment());
            this.attachmentToBeEdited()!.version!++;
            this.attachmentToBeEdited()!.uploadDate = dayjs();
            this.attachmentToBeEdited()!.hiddenPages = Array.from(this.newHiddenPages()).join(',');

            this.attachmentService.update(this.attachmentToBeEdited()!.id!, this.attachmentToBeEdited()!, pdfFile).subscribe({
                next: async () => {
                    if (this.newHiddenPages().size > 0) {
                        const pdfFileWithHiddenPages = await this.createHiddenVersionOfAttachment(Array.from(this.newHiddenPages()));
                        this.attachmentService.getAttachmentByParentAttachmentId(this.attachment()!.id!).subscribe({
                            next: async (attachmentRes) => {
                                if (attachmentRes.body && attachmentRes.body.id) {
                                    attachmentRes.body.version!++;
                                    this.attachmentService.update(attachmentRes.body.id, attachmentRes.body, pdfFileWithHiddenPages!).subscribe({
                                        next: () => {
                                            this.router.navigate(['course-management', this.course()?.id, 'lectures', this.attachment()!.lecture!.id, 'attachments']);
                                        },
                                        error: (error) => {
                                            this.alertService.error('artemisApp.attachment.pdfPreview.hiddenAttachmentUpdateError', { error: error.message });
                                        },
                                    });
                                } else {
                                    const attachmentWithHiddenPages = cloneDeep(this.attachment()!);
                                    attachmentWithHiddenPages.parentAttachment = this.attachment()!;
                                    attachmentWithHiddenPages.name = `${attachmentWithHiddenPages.name}_hidden`;
                                    attachmentWithHiddenPages.hiddenPages = undefined;
                                    this.attachmentService.create(attachmentWithHiddenPages, pdfFileWithHiddenPages!).subscribe({
                                        next: () => {
                                            this.alertService.success('artemisApp.attachment.pdfPreview.hiddenAttachmentCreateSuccess');
                                            this.router.navigate(['course-management', this.course()?.id, 'lectures', this.attachment()!.lecture!.id, 'attachments']);
                                        },
                                        error: (error) => {
                                            this.alertService.error('artemisApp.attachment.pdfPreview.hiddenAttachmentCreateError', { error: error.message });
                                        },
                                    });
                                }
                            },
                            error: (error) => {
                                this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                            },
                        });
                    } else {
                        this.alertService.success('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
                        this.router.navigate(['course-management', this.course()?.id, 'lectures', this.attachment()!.lecture!.id, 'attachments']);
                    }
                },
                error: (error) => {
                    this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                },
            });
        } else if (this.attachmentUnit()) {
            this.attachmentToBeEdited.set(this.attachmentUnit()!.attachment!);
            this.attachmentToBeEdited()!.version!++;
            this.attachmentToBeEdited()!.uploadDate = dayjs();
            this.attachmentToBeEdited()!.hiddenPages = Array.from(this.hiddenPages()).join(',');

            const formData = new FormData();
            formData.append('file', pdfFile);
            formData.append('attachment', objectToJsonBlob(this.attachmentToBeEdited()!));
            formData.append('attachmentUnit', objectToJsonBlob(this.attachmentUnit()!));

            this.attachmentUnitService.update(this.attachmentUnit()!.lecture!.id!, this.attachmentUnit()!.id!, formData).subscribe({
                next: async () => {
                    if (this.hiddenPages().size > 0) {
                        const attachmentWithHiddenPages = cloneDeep(this.attachmentUnit()?.attachment!);
                        attachmentWithHiddenPages.parentAttachment = this.attachmentUnit()?.attachment!;
                        const pdfFileWithHiddenPages = await this.createHiddenVersionOfAttachment(Array.from(this.hiddenPages()));

                        formData.append('file', pdfFileWithHiddenPages!);
                        formData.append('attachment', objectToJsonBlob(attachmentWithHiddenPages));

                        this.attachmentUnitService.create(formData, this.attachmentUnit()?.lecture!.id!).subscribe({
                            next: () => {
                                this.alertService.success('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
                                this.router.navigate(['course-management', this.course()?.id, 'lectures', this.attachmentUnit()!.lecture!.id, 'unit-management']);
                            },
                            error: (error) => {
                                this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                            },
                        });
                    } else {
                        this.alertService.success('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
                        this.router.navigate(['course-management', this.course()?.id, 'lectures', this.attachment()!.lecture!.id, 'attachments']);
                    }
                },
                error: (error) => {
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
                    this.router.navigate(['course-management', this.course()!.id, 'lectures', this.attachment()!.lecture!.id, 'attachments']);
                    this.dialogErrorSource.next('');
                },
                error: (error) => {
                    this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                },
            });
        } else if (this.attachmentUnit()) {
            this.lectureUnitService.delete(this.attachmentUnit()!.id!, this.attachmentUnit()!.lecture!.id!).subscribe({
                next: () => {
                    this.router.navigate(['course-management', this.course()!.id, 'lectures', this.attachmentUnit()!.lecture!.id, 'unit-management']);
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

            const pagesToDelete = Array.from(this.selectedPages()!)
                .map((page) => page - 1)
                .sort((a, b) => b - a);
            pagesToDelete.forEach((pageIndex) => {
                pdfDoc.removePage(pageIndex);
            });

            this.isFileChanged.set(true);
            const pdfBytes = await pdfDoc.save();
            this.currentPdfBlob.set(new Blob([pdfBytes], { type: 'application/pdf' }));
            this.selectedPages()!.clear();

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
     * Creates a hidden version of the current PDF attachment by removing specified pages.
     *
     * @param hiddenPages - An array of page numbers to be removed from the original PDF.
     * @returns A promise that resolves to a new `File` object representing the modified PDF, or undefined if an error occurs.
     */
    async createHiddenVersionOfAttachment(hiddenPages: number[]) {
        try {
            const fileName = this.attachment()!.name;
            const existingPdfBytes = await this.currentPdfBlob()!.arrayBuffer();
            const hiddenPdfDoc = await PDFDocument.load(existingPdfBytes);

            const pagesToDelete = hiddenPages.map((page) => page - 1).sort((a, b) => b - a);
            pagesToDelete.forEach((pageIndex) => {
                hiddenPdfDoc.removePage(pageIndex);
            });

            const pdfBytes = await hiddenPdfDoc.save();
            return new File([pdfBytes], `${fileName}_hidden.pdf`, { type: 'application/pdf' });
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

            const copiedPages = await existingPdfDoc.copyPages(newPdfDoc, newPdfDoc.getPageIndices());
            copiedPages.forEach((page) => existingPdfDoc.addPage(page));

            this.isFileChanged.set(true);
            const mergedPdfBytes = await existingPdfDoc.save();
            this.currentPdfBlob.set(new Blob([mergedPdfBytes], { type: 'application/pdf' }));

            this.selectedPages()!.clear();

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
}
