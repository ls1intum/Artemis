import { ChangeDetectorRef, Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileService } from 'app/shared/http/file.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { ButtonType } from 'app/shared/components/button.component';
import { Result } from 'app/entities/result.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-file-upload-submission-exam',
    templateUrl: './file-upload-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: FileUploadExamSubmissionComponent }],
    // change deactivation must be triggered manually
})
export class FileUploadExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;

    @Input()
    studentSubmission: FileUploadSubmission;
    @Input()
    exercise: FileUploadExercise;

    submittedFileName: string;
    submittedFileExtension: string;
    participation: StudentParticipation;
    result: Result;
    submissionFile?: File;

    readonly ButtonType = ButtonType;

    readonly IncludedInOverallScore = IncludedInOverallScore;
    filePath?: string;

    // Icons
    farListAlt = faListAlt;

    constructor(
        private route: ActivatedRoute,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private fileUploaderService: FileUploaderService,
        private resultService: ResultService,
        private alertService: AlertService,
        private location: Location,
        private translateService: TranslateService,
        private fileService: FileService,
        changeDetectorReference: ChangeDetectorRef,
    ) {
        super(changeDetectorReference);
    }

    /**
     * Initializes data for file upload editor
     */
    ngOnInit() {
        // show submission answers in UI
        this.updateViewFromSubmission();
    }

    /**
     * Updates the problem statement of the currently loaded file upload exercise which is part of the user's student exam.
     * @param newProblemStatement is the updated problem statement that should be displayed to the user.
     */
    updateProblemStatement(newProblemStatement: string): void {
        this.exercise.problemStatement = newProblemStatement;
        this.changeDetectorReference.detectChanges();
    }

    /**
     * Sets file submission for exercise
     * Here the file selected with the -browse- button is handled.
     * @param event {object} Event object which contains the uploaded file
     */
    setFileSubmissionForExercise(event: any): void {
        if (event.target.files.length) {
            const fileList: FileList = event.target.files;
            const submissionFile = fileList[0];
            const allowedFileExtensions = this.exercise.filePattern!.split(',');
            if (!allowedFileExtensions.some((extension) => submissionFile.name.toLowerCase().endsWith(extension))) {
                this.alertService.error('artemisApp.fileUploadSubmission.fileExtensionError');
            } else if (submissionFile.size > MAX_SUBMISSION_FILE_SIZE) {
                this.alertService.error('artemisApp.fileUploadSubmission.fileTooBigError', { fileName: submissionFile.name });
            } else {
                this.submissionFile = submissionFile;
                this.studentSubmission.isSynced = false;
            }
        }
    }

    downloadFile(filePath: string) {
        this.fileService.downloadFileWithAccessToken(filePath);
    }

    /**
     * The exercise is still active if it's due date hasn't passed yet.
     */
    get isActive(): boolean {
        return this.exercise && (!this.exercise.dueDate || dayjs(this.exercise.dueDate).isSameOrAfter(dayjs()));
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    public hasUnsavedChanges(): boolean {
        return !this.studentSubmission.isSynced!;
    }

    getSubmission(): Submission {
        return this.studentSubmission;
    }

    updateSubmissionFromView(): void {
        // we do nothing here as the new file path comes from the server
    }

    /**
     *  Here the new filePath, which was received from the server, is used to display the name and type of the just uploaded file.
     */
    updateViewFromSubmission(): void {
        if (this.studentSubmission.isSynced && this.studentSubmission.filePath) {
            // clear submitted file so that it is not displayed in the input (this might be confusing)
            this.submissionFile = undefined;
            const filePath = this.studentSubmission!.filePath!.split('/');
            this.submittedFileName = filePath.last()!;
            const fileName = this.submittedFileName.split('.');
            this.submittedFileExtension = fileName.last()!;
        }
    }

    /**
     *  Here we send the submissionFile obtained in setFileSubmissionForExercise() to the server with the update method. The server returns the path to the file, and we
     *  set it in the submission.
     */
    saveUploadedFile() {
        if (!this.submissionFile) {
            return;
        }
        this.fileUploadSubmissionService.update(this.studentSubmission as FileUploadSubmission, this.exercise.id!, this.submissionFile).subscribe({
            next: (res) => {
                const submissionFromServer = res.body!;
                this.studentSubmission.filePath = submissionFromServer.filePath;
                this.studentSubmission.isSynced = true;
                this.studentSubmission.submitted = true;
                this.updateViewFromSubmission();
            },
            error: () => this.onError(),
        });
    }

    /**
     * Pass on an error to the browser console and the alertService.
     */
    private onError() {
        this.alertService.error(this.translateService.instant('error.fileUploadSavingError'));
    }
}
