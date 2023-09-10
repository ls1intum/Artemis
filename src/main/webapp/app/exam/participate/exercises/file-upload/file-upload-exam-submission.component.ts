import { ChangeDetectorRef, Component, ElementRef, Input, ViewChild } from '@angular/core';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { FileDetails } from 'app/entities/file-details.model';
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
import { FileUploadStageComponent, StagedFile } from 'app/exercises/file-upload/stage/file-upload-stage.component';

@Component({
    selector: 'jhi-file-upload-submission-exam',
    templateUrl: './file-upload-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: FileUploadExamSubmissionComponent }],
    // change deactivation must be triggered manually
})
export class FileUploadExamSubmissionComponent extends ExamSubmissionComponent {
    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;
    @ViewChild('stage', { static: false }) stage: FileUploadStageComponent;

    @Input()
    studentSubmission: FileUploadSubmission;
    @Input()
    exercise: FileUploadExercise;

    stagedFiles: StagedFile[];
    submittedFiles?: FileDetails[];
    participation: StudentParticipation;
    result: Result;

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
     * Updates the problem statement of the currently loaded file upload exercise which is part of the user's student exam.
     * @param newProblemStatement is the updated problem statement that should be displayed to the user.
     */
    updateProblemStatement(newProblemStatement: string): void {
        this.exercise.problemStatement = newProblemStatement;
        this.changeDetectorReference.detectChanges();
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

    hasUnsavedChanges(): boolean {
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
        // we do nothing here as the studentSubmission is an input to the file upload stage which displays it correctly
    }

    /**
     *  Here we send the submissionFile obtained in setFileSubmissionForExercise() to the server with the update method. The server returns the path to the file, and we
     *  set it in the submission.
     */
    saveUploadedFile() {
        if (this.stagedFiles?.length === 0) {
            return;
        }

        const files: File[] = this.stagedFiles.map((stagedFile) => stagedFile.file);
        this.fileUploadSubmissionService.update(this.studentSubmission as FileUploadSubmission, this.exercise.id!, files).subscribe({
            next: (res) => {
                const submissionFromServer = res.body!;
                this.studentSubmission.filePaths = submissionFromServer.filePaths;
                this.studentSubmission.isSynced = true;
                this.studentSubmission.submitted = true;
            },
            error: () => this.onError(),
        });
    }

    stagedFilesChanged(stagedFiles: StagedFile[]): void {
        this.stagedFiles = stagedFiles;
        this.studentSubmission.isSynced = false;
    }

    uploadButtonClicked(): void {
        this.saveUploadedFile();
        this.stage.clearStagedFiles();
    }

    /**
     * Pass on an error to the browser console and the alertService.
     */
    private onError() {
        this.alertService.error(this.translateService.instant('error.fileUploadSavingError'));
    }
}
