import { Component, ElementRef, OnDestroy, OnInit, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AttachmentService } from 'app/lecture/attachment.service';
import 'pdfjs-dist/build/pdf.worker';
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
            this.attachmentToBeEdited.set(this.attachmentUnit()!.attachment!);
            this.attachmentToBeEdited()!.version!++;
            this.attachmentToBeEdited()!.uploadDate = dayjs();

            const formData = new FormData();
            formData.append('file', pdfFile);
            formData.append('attachment', objectToJsonBlob(this.attachmentToBeEdited()!));
            formData.append('attachmentUnit', objectToJsonBlob(this.attachmentUnit()!));

            this.attachmentUnitService.update(this.attachmentUnit()!.lecture!.id!, this.attachmentUnit()!.id!, formData).subscribe({
                next: () => {
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
