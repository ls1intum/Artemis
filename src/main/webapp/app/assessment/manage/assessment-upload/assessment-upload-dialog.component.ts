import { ChangeDetectionStrategy, Component, inject, input, model, output, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCloudUploadAlt, faDownload, faFileZipper, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { MAX_FILE_SIZE } from 'app/foundation/constants/input.constants';
import { downloadZipFileFromResponse } from 'app/foundation/util/download.util';
import { TumUiButtonComponent, TumUiCheckboxComponent, TumUiDialogComponent, TumUiMessageComponent } from '@tumaet/ui-angular';
import { AssessmentUploadError, AssessmentUploadResult, AssessmentUploadService } from 'app/assessment/manage/services/assessment-upload.service';

/**
 * Dialog that lets an instructor upload a zip file with manual assessments for the participants of a programming exercise. The file can be selected via the file system or dropped
 * onto the drop zone. The server validates the whole file (all-or-nothing) and either reports per-row validation errors or the number of created assessments.
 * <p>
 * Rendered with the tum-ui kit: a declarative {@link TumUiDialogComponent} whose visibility the parent controls via {@code [(visible)]}.
 * <p>
 * Uploading overwrites the manual assessment a participant may already have, so the dialog states this up front and only enables the upload button once the instructor has
 * confirmed it explicitly.
 * <p>
 * Invariant: {@link isUploading} is true only while an upload request is in flight, and an upload is only ever started with {@link overwriteConfirmed} set. Validation errors keep
 * the dialog open, while a successful upload closes it.
 */
@Component({
    selector: 'jhi-assessment-upload-dialog',
    templateUrl: './assessment-upload-dialog.component.html',
    styleUrls: ['./assessment-upload-dialog.component.scss'],
    imports: [NgClass, FaIconComponent, TranslateDirective, ArtemisTranslatePipe, TumUiDialogComponent, TumUiButtonComponent, TumUiCheckboxComponent, TumUiMessageComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssessmentUploadDialogComponent {
    private readonly assessmentUploadService = inject(AssessmentUploadService);
    private readonly alertService = inject(AlertService);

    /** Two-way visibility of the dialog, controlled by the parent. */
    readonly visible = model(false);
    /** The id of the programming exercise whose participants are assessed. */
    readonly exerciseId = input.required<number>();
    /** Emitted with the number of created assessments after a successful upload, so the parent can refresh its now-stale assessment data. */
    readonly uploaded = output<number>();

    protected readonly selectedFile = signal<File | undefined>(undefined);
    protected readonly isDragOver = signal(false);
    protected readonly isUploading = signal(false);
    protected readonly isDownloadingTemplate = signal(false);
    protected readonly errors = signal<AssessmentUploadError[]>([]);
    /** Explicit confirmation that existing manual assessments of the uploaded participants may be overwritten. Gates the upload button. */
    protected readonly overwriteConfirmed = signal(false);

    protected readonly faCloudUploadAlt = faCloudUploadAlt;
    protected readonly faDownload = faDownload;
    protected readonly faFileZipper = faFileZipper;
    protected readonly faTriangleExclamation = faTriangleExclamation;

    /** Marks the drop zone as active while a file is dragged over it. */
    onDragOver(event: DragEvent): void {
        event.preventDefault();
        event.stopPropagation();
        this.isDragOver.set(true);
    }

    /** Clears the drop-zone active state when the drag leaves it. */
    onDragLeave(event: DragEvent): void {
        event.preventDefault();
        event.stopPropagation();
        this.isDragOver.set(false);
    }

    /**
     * Handles a file dropped onto the drop zone.
     * Postcondition: if a file was dropped it is validated and, when valid, selected; otherwise nothing changes.
     */
    onDrop(event: DragEvent): void {
        event.preventDefault();
        event.stopPropagation();
        this.isDragOver.set(false);
        const files = event.dataTransfer?.files;
        if (files?.length) {
            this.handleFile(files[0]);
        }
    }

    /**
     * Handles a file chosen via the hidden file input.
     * Postcondition: a chosen file is validated and, when valid, selected, and the input is reset so the same file can be re-selected.
     */
    onFileInputChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        if (input.files?.length) {
            this.handleFile(input.files[0]);
            // Reset the input so selecting the same file again re-triggers the change event.
            input.value = '';
        }
    }

    /**
     * Validates a candidate file and, if it is an acceptable `.zip` within the size limit, selects it and clears any previous errors.
     * Postcondition: on success `selectedFile()` is the file and `errors()` is cleared; on rejection an alert is shown and the selection is unchanged.
     */
    private handleFile(file: File): void {
        if (!file.name.toLowerCase().endsWith('.zip')) {
            this.alertService.error('artemisApp.assessmentUpload.error.fileTypeNotSupported');
            return;
        }
        if (file.size > MAX_FILE_SIZE) {
            this.alertService.error('artemisApp.assessmentUpload.error.fileTooLarge', { fileName: file.name });
            return;
        }
        // Reset any previous outcome when a new file is chosen.
        this.errors.set([]);
        this.selectedFile.set(file);
    }

    /**
     * Uploads the currently selected file and reflects the outcome in the component state.
     * Precondition: a file is selected and the overwrite of existing manual assessments has been confirmed (otherwise this is a no-op).
     * Postcondition: while in flight `isUploading()` is true; on completion it is false. A successful upload shows a success alert and closes the dialog; validation errors keep it open.
     */
    upload(): void {
        const file = this.selectedFile();
        if (!file || !this.overwriteConfirmed() || this.isUploading()) {
            return;
        }
        this.isUploading.set(true);
        this.errors.set([]);
        this.assessmentUploadService.uploadManualAssessments(this.exerciseId(), file).subscribe({
            next: (response) => this.handleUploadResult(response.body ?? { numberOfCreatedAssessments: 0 }),
            // Malformed-request errors (empty/oversized/not a zip) carry an Artemis error header and are surfaced by the global alert interceptor.
            error: (_error: HttpErrorResponse) => this.isUploading.set(false),
        });
    }

    /** Reflects the server's parsing/storing result: shows per-row errors, or shows a success alert and closes the dialog. */
    private handleUploadResult(result: AssessmentUploadResult): void {
        this.isUploading.set(false);
        if (result.errors?.length) {
            this.errors.set(result.errors);
        } else {
            this.alertService.success('artemisApp.assessmentUpload.success', { count: result.numberOfCreatedAssessments });
            // Notify the parent so it can refresh the assessment counts, chart and assessable-submission state that this import just changed.
            this.uploaded.emit(result.numberOfCreatedAssessments);
            this.close();
        }
    }

    /**
     * Downloads a template zip (participant identifiers pre-filled, points and feedback empty) that the instructor can edit and re-upload.
     * Postcondition: while in flight `isDownloadingTemplate()` is true; on success the browser saves the returned zip, on error the global alert interceptor surfaces the failure.
     */
    downloadTemplate(): void {
        if (this.isDownloadingTemplate()) {
            return;
        }
        this.isDownloadingTemplate.set(true);
        this.assessmentUploadService.downloadTemplate(this.exerciseId()).subscribe({
            next: (response) => {
                downloadZipFileFromResponse(response);
                this.isDownloadingTemplate.set(false);
            },
            error: (_error: HttpErrorResponse) => this.isDownloadingTemplate.set(false),
        });
    }

    /** Closes the dialog. */
    close(): void {
        if (this.isUploading()) {
            return;
        }
        this.visible.set(false);
    }

    /** Resets the transient state when the dialog is shown, so every open starts from a clean slate. */
    resetState(): void {
        this.selectedFile.set(undefined);
        this.errors.set([]);
        this.overwriteConfirmed.set(false);
        this.isUploading.set(false);
        this.isDownloadingTemplate.set(false);
    }
}
