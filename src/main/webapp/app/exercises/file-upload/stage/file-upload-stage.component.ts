import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';
import { FileDetails } from 'app/entities/file-details.model';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { AlertService } from 'app/core/util/alert.service';
import { FileService } from 'app/shared/http/file.service';

export class StagedFile {
    constructor(
        public fileDetails: FileDetails,
        public file: File,
    ) {}
}

@Component({
    selector: 'jhi-stage',
    templateUrl: './file-upload-stage.component.html',
    styleUrls: ['./file-upload-stage.component.scss'],
})
export class FileUploadStageComponent {
    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;
    stagedFiles: StagedFile[] = [];
    @Input() allowsUploads?: boolean;
    @Input() submission?: FileUploadSubmission;
    @Input() allowedFileExtensions: string[];
    @Input() showUploadButton: boolean;
    @Output() stagedFilesChanged = new EventEmitter<StagedFile[]>();
    @Output() uploadButtonClicked = new EventEmitter();

    protected readonly FileDetails = FileDetails;

    constructor(
        private alertService: AlertService,
        private fileService: FileService,
    ) {}

    /**
     * Stages a file submission for exercise
     * @param event {object} Event object which contains the uploaded file
     */
    stageFileSubmissionForExercise(event: any): void {
        if (event.target.files.length) {
            const fileList: FileList = event.target.files;
            const submissionFile = fileList[0];
            if (!this.allowedFileExtensions.some((extension) => submissionFile.name.toLowerCase().endsWith(extension))) {
                this.alertService.error('artemisApp.fileUploadSubmission.fileExtensionError');
            } else if (submissionFile.size > MAX_SUBMISSION_FILE_SIZE) {
                this.alertService.error('artemisApp.fileUploadSubmission.fileTooBigError', { fileName: submissionFile.name });
            } else {
                this.stagedFiles!.push(new StagedFile(FileDetails.getFileDetailsFromPath(submissionFile.name), submissionFile));
            }

            this.fileInput.nativeElement.value = '';
            this.stagedFilesChanged.emit(this.stagedFiles);
        }
    }

    /**
     * Removes a file submission from the stage
     * @param stagedFile File to be removed
     */
    removeFileSubmissionFromStage(stagedFile: StagedFile): void {
        this.stagedFiles = this.stagedFiles!.filter((file) => file != stagedFile);
        this.stagedFilesChanged.emit(this.stagedFiles);
    }

    /**
     * Triggers the uploadButtonClicked output.
     */
    uploadStagedFiles(): void {
        this.uploadButtonClicked.emit();
    }

    /**
     * Opens the given file on the server in a new tab for the user to inspect and or download.
     * @param filePath File to be opened and or downloaded.
     */
    downloadFile(filePath: string) {
        this.fileService.downloadFile(filePath);
    }

    /**
     * Opens the given file on the client in a new tab for the user to verify before uploading.
     * @param file File to be opened and verified.
     */
    openFile(file: File) {
        const url = URL.createObjectURL(file);
        window.open(url, '_blank');
        URL.revokeObjectURL(url);
    }

    /**
     * Clears the staged files.
     */
    public clearStagedFiles() {
        this.stagedFiles = [];
        this.stagedFilesChanged.emit(this.stagedFiles);
    }
}
