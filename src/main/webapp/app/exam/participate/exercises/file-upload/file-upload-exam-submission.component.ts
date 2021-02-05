import { Component, ElementRef, OnInit, ViewChild, Input, ChangeDetectorRef } from '@angular/core';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import * as moment from 'moment';
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

@Component({
    selector: 'jhi-file-upload-submission-exam',
    templateUrl: './file-upload-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: FileUploadExamSubmissionComponent }],
    styleUrls: ['./file-upload-exam-submission.component.scss'],
    // change deactivation must be triggered manually
})
export class FileUploadExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;

    @Input()
    submission: FileUploadSubmission;
    @Input()
    exercise: FileUploadExercise;

    submittedFileName: string;
    submittedFileExtension: string;
    participation: StudentParticipation;
    result: Result;
    submissionFile?: File;
    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;
    isSaving: boolean;
    isOwnerOfParticipation: boolean;

    acceptedFileExtensions: string;

    isLate: boolean; // indicates if the submission is late

    readonly ButtonType = ButtonType;

    readonly IncludedInOverallScore = IncludedInOverallScore;
    filePath?: string;

    constructor(
        private route: ActivatedRoute,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private fileUploaderService: FileUploaderService,
        private resultService: ResultService,
        private jhiAlertService: JhiAlertService,
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
     * Sets file submission for exercise
     * @param $event {object} Event object which contains the uploaded file
     */
    setFileSubmissionForExercise($event: any): void {
        if ($event.target.files.length) {
            const fileList: FileList = $event.target.files;
            const submissionFile = fileList[0];
            const allowedFileExtensions = this.exercise.filePattern!.split(',');
            if (!allowedFileExtensions.some((extension) => submissionFile.name.toLowerCase().endsWith(extension))) {
                this.jhiAlertService.error('artemisApp.fileUploadSubmission.fileExtensionError');
            } else if (submissionFile.size > MAX_SUBMISSION_FILE_SIZE) {
                this.jhiAlertService.error('artemisApp.fileUploadSubmission.fileTooBigError', { fileName: submissionFile.name });
            } else {
                this.submissionFile = submissionFile;
                console.log(this.submissionFile);
            }
        }
    }

    setSubmittedFile() {
        // clear submitted file so that it is not displayed in the input (this might be confusing)
        this.submissionFile = undefined;
        const filePath = this.submission!.filePath!.split('/');
        this.submittedFileName = filePath[filePath.length - 1];
        const fileName = this.submittedFileName.split('.');
        this.submittedFileExtension = fileName[fileName.length - 1];
    }

    downloadFile(filePath: string) {
        this.fileService.downloadFileWithAccessToken(filePath);
    }

    /**
     * The exercise is still active if it's due date hasn't passed yet.
     */
    get isActive(): boolean {
        return this.exercise && (!this.exercise.dueDate || moment(this.exercise.dueDate).isSameOrAfter(moment()));
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    public hasUnsavedChanges(): boolean {
        return !this.submission.isSynced!;
    }

    getSubmission(): Submission {
        return this.submission;
    }

    // TODO: clarify why this is needed here and also for the other exercise types
    updateSubmissionFromView(): void {
        console.log('updateSubmissionFromView');
        // intentionally left empty
    }

    updateViewFromSubmission(): void {
        console.log('updateViewFromSubmission');
        if (this.submission.isSynced) {
            this.setSubmittedFile();
        }
    }
}
