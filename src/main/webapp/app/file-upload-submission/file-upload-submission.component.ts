import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { Result, ResultService } from 'app/entities/result';
import { FileUploadExercise } from 'app/entities/file-upload-exercise';
import * as moment from 'moment';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { FileUploadSubmission } from 'app/entities/file-upload-submission';
import { FileUploadSubmissionService } from 'app/entities/file-upload-submission/file-upload-submission.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { ComponentCanDeactivate, FileService } from 'app/shared';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';
import { FileUploadAssessmentsService } from 'app/entities/file-upload-assessment/file-upload-assessment.service';
import { ButtonType } from 'app/shared/components';
import { omit } from 'lodash';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';

@Component({
    templateUrl: './file-upload-submission.component.html',
})
export class FileUploadSubmissionComponent implements OnInit, ComponentCanDeactivate {
    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;
    submission: FileUploadSubmission | null;
    submittedFileName: string;
    submittedFileExtension: string;
    fileUploadExercise: FileUploadExercise;
    participation: StudentParticipation;
    result: Result;
    submissionFile: File | null;
    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;
    isSaving: boolean;

    acceptedFileExtensions: string;

    isLate: boolean; // indicates if the submission is late

    readonly ButtonType = ButtonType;

    private submissionConfirmationText: string;

    constructor(
        private route: ActivatedRoute,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private fileUploaderService: FileUploaderService,
        private resultService: ResultService,
        private jhiAlertService: JhiAlertService,
        private location: Location,
        private translateService: TranslateService,
        private fileService: FileService,
        private participationWebsocketService: ParticipationWebsocketService,
        private fileUploadAssessmentService: FileUploadAssessmentsService,
    ) {
        translateService.get('artemisApp.fileUploadSubmission.confirmSubmission').subscribe(text => (this.submissionConfirmationText = text));
    }

    /**
     * Initializes data for file upload editor
     */
    ngOnInit() {
        const participationId = Number(this.route.snapshot.paramMap.get('participationId'));
        if (Number.isNaN(participationId)) {
            return this.jhiAlertService.error('artemisApp.fileUploadExercise.error', null, undefined);
        }
        this.fileUploadSubmissionService.getDataForFileUploadEditor(participationId).subscribe(
            (submission: FileUploadSubmission) => {
                // reconnect participation <--> result
                if (submission.result) {
                    submission.participation.results = [submission.result];
                }
                this.participation = <StudentParticipation>submission.participation;

                // reconnect participation <--> submission
                this.participation.submissions = [<FileUploadSubmission>omit(submission, 'participation')];

                this.submission = submission;
                this.result = submission.result;
                this.fileUploadExercise = this.participation.exercise as FileUploadExercise;

                // checks if the student started the exercise after the due date
                this.isLate =
                    this.fileUploadExercise &&
                    !!this.fileUploadExercise.dueDate &&
                    !!this.participation.initializationDate &&
                    moment(this.participation.initializationDate).isAfter(this.fileUploadExercise.dueDate);

                this.acceptedFileExtensions = this.fileUploadExercise.filePattern
                    .split(',')
                    .map(extension => `.${extension}`)
                    .join(',');
                this.isAfterAssessmentDueDate = !this.fileUploadExercise.assessmentDueDate || moment().isAfter(this.fileUploadExercise.assessmentDueDate);

                if (this.submission.submitted) {
                    this.setSubmittedFile();
                }
                if (this.submission.submitted && this.result && this.result.completionDate) {
                    this.fileUploadAssessmentService.getAssessment(this.submission.id).subscribe((assessmentResult: Result) => {
                        this.result = assessmentResult;
                    });
                }
            },
            (error: HttpErrorResponse) => this.onError(error),
        );
    }
    /**
     * Uploads a submission file and submits File Upload Exercise
     */
    public submitExercise() {
        if (this.isSaving) {
            // don't execute the function if it is already currently executing
            return;
        }

        const file = this.submissionFile;
        if (!this.submission || !file) {
            return;
        }
        this.submission!.submitted = true;
        this.isSaving = true;
        this.fileUploadSubmissionService.update(this.submission!, this.fileUploadExercise.id, file).subscribe(
            response => {
                this.submission = response.body!;
                // reconnect so that the submission status is displayed correctly in the result.component
                this.submission.participation.submissions = [this.submission];
                this.participationWebsocketService.addParticipation(this.submission.participation as StudentParticipation, this.fileUploadExercise);
                this.result = this.submission.result;
                this.setSubmittedFile();
                if (this.isActive) {
                    this.jhiAlertService.success('artemisApp.fileUploadExercise.submitSuccessful');
                } else {
                    this.jhiAlertService.warning('artemisApp.fileUploadExercise.submitDeadlineMissed');
                }
                this.isSaving = false;
            },
            (error: HttpErrorResponse) => {
                this.submission!.submitted = false;
                const serverError = error.headers.get('X-artemisApp-error');
                if (serverError) {
                    this.jhiAlertService.error(serverError, { fileName: file['name'] });
                } else {
                    this.jhiAlertService.error('artemisApp.fileUploadSubmission.fileUploadError', { fileName: file['name'] });
                }
                this.fileInput.nativeElement.value = '';
                this.submissionFile = null;
                this.isSaving = false;
            },
        );
    }

    /**
     * Sets file submission for exercise
     * @param $event {object} Event object which contains the uploaded file
     */
    setFileSubmissionForExercise($event: any): void {
        if ($event.target.files.length) {
            const fileList: FileList = $event.target.files;
            const submissionFile = fileList[0];
            const allowedFileExtensions = this.fileUploadExercise.filePattern.split(',');
            if (!allowedFileExtensions.some(extension => submissionFile.name.toLowerCase().endsWith(extension))) {
                this.jhiAlertService.error('artemisApp.fileUploadSubmission.fileExtensionError', null, undefined);
            } else if (submissionFile.size > MAX_SUBMISSION_FILE_SIZE) {
                this.jhiAlertService.error('artemisApp.fileUploadSubmission.fileTooBigError', { fileName: submissionFile.name });
            } else {
                this.submissionFile = submissionFile;
            }
        }
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, undefined);
    }

    private setSubmittedFile() {
        // clear submitted file so that it is not displayed in the input (this might be confusing)
        this.submissionFile = null;
        const filePath = this.submission!.filePath!.split('/');
        this.submittedFileName = filePath[filePath.length - 1];
        const fileName = this.submittedFileName.split('.');
        this.submittedFileExtension = fileName[fileName.length - 1];
    }

    /**
     * Navigates to previous location
     */
    previous() {
        this.location.back();
    }

    downloadFile(filePath: string) {
        this.fileService.downloadAttachment(filePath);
    }

    /**
     * Returns false if user selected a file, but didn't submit the exercise, true otherwise.
     */
    canDeactivate(): boolean {
        return !(this.submission && !this.submission.submitted && this.submissionFile);
    }

    /**
     * The exercise is still active if it's due date hasn't passed yet.
     */
    get isActive(): boolean {
        return this.fileUploadExercise && (!this.fileUploadExercise.dueDate || moment(this.fileUploadExercise.dueDate).isSameOrAfter(moment()));
    }

    get submitButtonTooltip(): string {
        if (!this.submissionFile) {
            return 'artemisApp.fileUploadSubmission.selectFile';
        }

        if (!this.isLate) {
            if (this.isActive && !this.fileUploadExercise.dueDate) {
                return 'entity.action.submitNoDeadlineTooltip';
            } else if (this.isActive) {
                return 'entity.action.submitTooltip';
            } else {
                return 'entity.action.deadlineMissedTooltip';
            }
        }

        return 'entity.action.submitDeadlineMissedTooltip';
    }
}
