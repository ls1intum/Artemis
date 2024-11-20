import { Component, ElementRef, OnChanges, OnDestroy, OnInit, SimpleChanges, computed, inject, signal, viewChild } from '@angular/core';
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

@Component({
    selector: 'jhi-pdf-preview-component',
    templateUrl: './pdf-preview.component.html',
    styleUrls: ['./pdf-preview.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule, PdfPreviewThumbnailGridComponent],
})
export class PdfPreviewComponent implements OnInit, OnDestroy, OnChanges {
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
    allPagesSelected = computed(() => this.selectedPages().size === this.totalPages());
    hiddenPages = signal<Set<number>>(new Set());

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
                this.attachmentSub = this.attachmentService.getAttachmentFile(this.course()!.id!, this.attachment()!.id!).subscribe({
                    next: (blob: Blob) => {
                        this.currentPdfBlob.set(blob);
                        this.currentPdfUrl.set(URL.createObjectURL(blob));
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
            } else if ('attachmentUnit' in data) {
                this.attachmentUnit.set(data.attachmentUnit);
                this.attachmentUnitService.getHiddenSlides(this.attachmentUnit()!.lecture!.id!, this.attachmentUnit()!.id!).subscribe({
                    next: (hiddenPages: number[]) => {
                        this.hiddenPages.set(new Set(hiddenPages));
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
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

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['hiddenPages']) {
            this.isFileChanged.set(true);
        }
    }
    /**
     * Triggers the file input to select files.
     */
    triggerFileInput(): void {
        this.fileInput().nativeElement.click();
    }

    /**
     * Retrieves an array of hidden page numbers from elements with IDs starting with "show-button-".
     *
     * @returns An array of strings representing the hidden page numbers.
     */
    getHiddenPages() {
        return Array.from(document.querySelectorAll('.hide-show-btn.btn-success'))
            .map((el) => {
                const match = el.id.match(/hide-show-button-(\d+)/);
                return match ? match[1] : null;
            })
            .filter((id) => id !== null);
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

            this.attachmentService.update(this.attachmentToBeEdited()!.id!, this.attachmentToBeEdited()!, pdfFile).subscribe({
                next: () => {
                    this.alertService.success('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
                    this.router.navigate(['course-management', this.course()?.id, 'lectures', this.attachment()!.lecture!.id, 'attachments']);
                },
                error: (error) => {
                    this.alertService.error('artemisApp.attachment.pdfPreview.attachmentUpdateError', { error: error.message });
                },
            });
        } else if (this.attachmentUnit()) {
            const finalHiddenPages = this.getHiddenPages().join(',');
            this.attachmentToBeEdited.set(this.attachmentUnit()!.attachment!);
            this.attachmentToBeEdited()!.version!++;
            this.attachmentToBeEdited()!.uploadDate = dayjs();

            const formData = new FormData();
            formData.append('file', pdfFile);
            formData.append('attachment', objectToJsonBlob(this.attachmentToBeEdited()!));
            formData.append('attachmentUnit', objectToJsonBlob(this.attachmentUnit()!));

            this.attachmentUnitService.update(this.attachmentUnit()!.lecture!.id!, this.attachmentUnit()!.id!, formData, undefined, finalHiddenPages).subscribe({
                next: async () => {
                    this.alertService.success('artemisApp.attachment.pdfPreview.attachmentUpdateSuccess');
                    this.router.navigate(['course-management', this.course()?.id, 'lectures', this.attachmentUnit()!.lecture!.id, 'unit-management']);
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

            this.updateHiddenPages(pagesToDelete);
            pagesToDelete.forEach((pageIndex) => pdfDoc.removePage(pageIndex));

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
     * Updates hidden pages after selected pages are deleted.
     * @param pagesToDelete - Array of pages to be deleted (0-indexed).
     */
    private updateHiddenPages(pagesToDelete: number[]) {
        const updatedHiddenPages = new Set<number>();
        this.hiddenPages().forEach((hiddenPage) => {
            // Adjust hiddenPage based on the deleted pages
            const adjustedPage = pagesToDelete.reduce((acc, pageIndex) => {
                if (acc === pageIndex + 1) {
                    return;
                }
                return pageIndex < acc - 1 ? acc - 1 : acc;
            }, hiddenPage);
            if (adjustedPage !== -1) {
                updatedHiddenPages.add(adjustedPage!);
            }
        });
        this.hiddenPages.set(updatedHiddenPages);
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
