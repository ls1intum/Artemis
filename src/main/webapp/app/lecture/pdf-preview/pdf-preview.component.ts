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

type VisibilityAction = 'hide' | 'show';

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
    ],
})
export class PdfPreviewComponent implements OnInit, OnDestroy {
    fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

    attachmentSub: Subscription;
    attachmentUnitSub: Subscription;
    protected readonly ButtonType = ButtonType;

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
    initialHiddenPages = signal<Set<number>>(new Set());
    hiddenPages = signal<Set<number>>(new Set());
    isSaving = signal<boolean>(false);

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
                const hiddenPages: Set<number> = new Set(data.attachmentUnit.slides.filter((page: Slide) => page.hidden).map((page: Slide) => page.slideNumber));
                this.initialHiddenPages.set(new Set(hiddenPages));
                this.hiddenPages.set(new Set(hiddenPages));

                this.attachmentUnitSub = this.attachmentUnitService
                    .getAttachmentFile(this.course()!.id!, this.attachmentUnit()!.id!)
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
     * Checks if there has been any change between the current set of hidden pages and the new set of hidden pages.
     *
     * @returns Returns true if the sets differ in size or if any element in `newHiddenPages` is not found in `hiddenPages`, otherwise false.
     */
    hiddenPagesChanged() {
        if (this.initialHiddenPages()!.size !== this.hiddenPages()!.size) return true;

        for (const elem of this.initialHiddenPages()!) {
            if (!this.hiddenPages()!.has(elem)) return true;
        }
        return false;
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
                return match ? parseInt(match[1], 10) : null;
            })
            .filter((id) => id !== null);
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
                formData.append('hiddenPages', finalHiddenPages.join(','));
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
    updateHiddenPages(pagesToDelete: number[]) {
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
     * Creates a student version of the current PDF attachment by removing specified pages.
     *
     * @param hiddenPages - An array of page numbers to be removed from the original PDF.
     * @returns A promise that resolves to a new `File` object representing the modified PDF, or undefined if an error occurs.
     */
    async createStudentVersionOfAttachment(hiddenPages: number[]) {
        try {
            const fileName = this.attachmentUnit()!.attachment!.name;
            const existingPdfBytes = await this.currentPdfBlob()!.arrayBuffer();
            const hiddenPdfDoc = await PDFDocument.load(existingPdfBytes);

            const pagesToDelete = hiddenPages.map((page) => page - 1).sort((a, b) => b - a);
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
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];

        if (file!.type !== 'application/pdf') {
            this.alertService.error('artemisApp.attachment.pdfPreview.invalidFileType');
            input.value = '';
            return;
        }

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

    /**
     * Toggles visibility of selected pages
     */
    toggleVisibility(action: VisibilityAction, selectedPages: Set<number>): void {
        this.hiddenPages.update((currentSet) => {
            const updatedSet = new Set(currentSet);

            selectedPages.forEach((page) => {
                if (action === 'hide') {
                    updatedSet.add(page);
                } else {
                    updatedSet.delete(page);
                }
            });

            return updatedSet;
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
