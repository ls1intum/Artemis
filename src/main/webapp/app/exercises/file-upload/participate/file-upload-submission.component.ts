import { Component, ElementRef, Input, OnInit, ViewChild, inject } from '@angular/core';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';
import { FileUploadAssessmentService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { omit } from 'lodash-es';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { FileService } from 'app/shared/http/file.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { ButtonType } from 'app/shared/components/button.component';
import { Result } from 'app/entities/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { getFirstResultWithComplaint, getLatestSubmissionResult } from 'app/entities/submission.model';
import { addParticipationToResult, getUnreferencedFeedback } from 'app/exercises/shared/result/result.utils';
import { Feedback, checkSubsequentFeedbackInAssessment } from 'app/entities/feedback.model';
import { onError } from 'app/shared/util/global.utils';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faDownload } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-file-upload-submission',
    templateUrl: './file-upload-submission.component.html',
})
export class FileUploadSubmissionComponent implements OnInit, ComponentCanDeactivate {
    private route = inject(ActivatedRoute);
    private fileUploadSubmissionService = inject(FileUploadSubmissionService);
    private fileUploaderService = inject(FileUploaderService);
    private resultService = inject(ResultService);
    private alertService = inject(AlertService);
    private location = inject(Location);
    private translateService = inject(TranslateService);
    private fileService = inject(FileService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private fileUploadAssessmentService = inject(FileUploadAssessmentService);
    private accountService = inject(AccountService);

    readonly addParticipationToResult = addParticipationToResult;
    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;

    @Input() participationId?: number;
    @Input() displayHeader: boolean = true;
    @Input() expandProblemStatement?: boolean = true;
    @Input() displayedInExamSummary?: boolean = false;

    @Input() inputExercise?: FileUploadExercise;
    @Input() inputSubmission?: FileUploadSubmission;
    @Input() inputParticipation?: StudentParticipation;

    submission?: FileUploadSubmission;
    submittedFileName: string;
    submittedFileExtension: string;
    fileUploadExercise: FileUploadExercise;
    participation: StudentParticipation;
    result: Result;
    resultWithComplaint?: Result;
    submissionFile?: File;
    course?: Course;
    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;
    isSaving: boolean;
    isOwnerOfParticipation: boolean;
    examMode = false;

    acceptedFileExtensions: string;

    isLate: boolean; // indicates if the submission is late

    faDownload = faDownload;
    readonly ButtonType = ButtonType;

    private submissionConfirmationText: string;

    // Icons
    farListAlt = faListAlt;

    constructor() {
        const translateService = this.translateService;

        translateService.get('artemisApp.fileUploadSubmission.confirmSubmission').subscribe((text) => (this.submissionConfirmationText = text));
    }

    /**
     * Initializes data for file upload editor
     */
    ngOnInit() {
        if (this.inputValuesArePresent()) {
            this.setupComponentWithInputValues();
        } else {
            const participationId = this.participationId ?? Number(this.route.snapshot.paramMap.get('participationId'));
            if (Number.isNaN(participationId)) {
                return this.alertService.error('artemisApp.fileUploadExercise.error');
            }
            this.fileUploadSubmissionService.getDataForFileUploadEditor(participationId).subscribe({
                next: (submission: FileUploadSubmission) => {
                    // reconnect participation <--> result
                    const tmpResult = getLatestSubmissionResult(submission);
                    if (tmpResult) {
                        submission.participation!.results = [tmpResult!];
                    }
                    this.participation = <StudentParticipation>submission.participation;

                    // reconnect participation <--> submission
                    this.participation.submissions = [<FileUploadSubmission>omit(submission, 'participation')];

                    this.submission = submission;
                    this.result = tmpResult!;
                    this.resultWithComplaint = getFirstResultWithComplaint(submission);
                    this.fileUploadExercise = this.participation.exercise as FileUploadExercise;
                    this.examMode = !!this.fileUploadExercise.exerciseGroup;
                    this.fileUploadExercise.studentParticipations = [this.participation];
                    this.course = getCourseFromExercise(this.fileUploadExercise);

                    // checks if the student started the exercise after the due date
                    this.isLate =
                        this.fileUploadExercise &&
                        !!this.fileUploadExercise.dueDate &&
                        !!this.participation.initializationDate &&
                        dayjs(this.participation.initializationDate).isAfter(getExerciseDueDate(this.fileUploadExercise, this.participation));

                    this.acceptedFileExtensions = this.fileUploadExercise
                        .filePattern!.split(',')
                        .map((extension) => `.${extension}`)
                        .join(',');
                    this.isAfterAssessmentDueDate = !this.fileUploadExercise.assessmentDueDate || dayjs().isAfter(this.fileUploadExercise.assessmentDueDate);

                    if (this.submission?.submitted) {
                        this.setSubmittedFile();
                    }
                    if (this.submission?.submitted && this.result?.completionDate) {
                        this.fileUploadAssessmentService.getAssessment(this.submission.id!).subscribe((assessmentResult: Result) => {
                            this.result = assessmentResult;
                        });
                    }
                    this.isOwnerOfParticipation = this.accountService.isOwnerOfParticipation(this.participation);
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
    }

    private inputValuesArePresent(): boolean {
        return !!(this.inputExercise || this.inputSubmission || this.inputParticipation);
    }

    /**
     * Uses values directly passed to this component instead of subscribing to a participation to save resources
     *
     * <i>e.g. used within {@link ExamResultSummaryComponent} and the respective {@link ModelingExamSummaryComponent}
     * as directly after the exam no grading is present and only the student solution shall be displayed </i>
     * @private
     */
    private setupComponentWithInputValues() {
        if (this.inputExercise) {
            this.fileUploadExercise = this.inputExercise;
        }
        if (this.inputSubmission) {
            this.submission = this.inputSubmission;
        }
        if (this.inputParticipation) {
            this.participation = this.inputParticipation;
        }

        if (this.submission?.submitted) {
            this.setSubmittedFile();
        }
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
        this.isSaving = true;
        this.fileUploadSubmissionService.update(this.submission!, this.fileUploadExercise.id!, file).subscribe({
            next: (res) => {
                this.submission = res.body!;
                this.participation = this.submission.participation as StudentParticipation;
                // reconnect so that the submission status is displayed correctly in the result.component
                this.submission.participation!.submissions = [this.submission];
                this.participationWebsocketService.addParticipation(this.participation, this.fileUploadExercise);
                this.fileUploadExercise.studentParticipations = [this.participation];
                this.result = getLatestSubmissionResult(this.submission)!;
                this.setSubmittedFile();
                if (this.isActive) {
                    this.alertService.success('artemisApp.fileUploadExercise.submitSuccessful');
                } else {
                    this.alertService.warning('artemisApp.fileUploadExercise.submitDueDateMissed');
                }
                this.isSaving = false;
            },
            error: (error: HttpErrorResponse) => {
                this.submission!.submitted = false;
                const serverError = error.headers.get('X-artemisApp-error');
                if (serverError) {
                    this.alertService.error(serverError, { fileName: file.name });
                } else {
                    this.alertService.error('artemisApp.fileUploadSubmission.fileUploadError', { fileName: file.name });
                }
                this.fileInput.nativeElement.value = '';
                this.submissionFile = undefined;
                this.isSaving = false;
            },
        });
    }

    /**
     * Sets file submission for exercise
     * @param event {object} Event object which contains the uploaded file
     */
    setFileSubmissionForExercise(event: any): void {
        if (event.target.files.length) {
            const fileList: FileList = event.target.files;
            const submissionFile = fileList[0];
            const allowedFileExtensions = this.fileUploadExercise.filePattern!.split(',');
            if (!allowedFileExtensions.some((extension) => submissionFile.name.toLowerCase().endsWith(extension))) {
                this.alertService.error('artemisApp.fileUploadSubmission.fileExtensionError');
            } else if (submissionFile.size > MAX_SUBMISSION_FILE_SIZE) {
                this.alertService.error('artemisApp.fileUploadSubmission.fileTooBigError', { fileName: submissionFile.name });
            } else {
                this.submissionFile = submissionFile;
            }
        }
    }

    /**
     * Check whether or not a result exists and if, returns the unreferenced feedback of it
     */
    get unreferencedFeedback(): Feedback[] | undefined {
        if (this.result?.feedbacks) {
            checkSubsequentFeedbackInAssessment(this.result.feedbacks);
            return getUnreferencedFeedback(this.result.feedbacks);
        }
        return undefined;
    }

    private setSubmittedFile() {
        // clear submitted file so that it is not displayed in the input (this might be confusing)
        this.submissionFile = undefined;
        this.submittedFileName = '';
        this.submittedFileExtension = '';
        if (this.submission?.filePath) {
            const filePath = this.submission!.filePath!.split('/');
            this.submittedFileName = filePath.last()!;
            const fileName = this.submittedFileName.split('.');
            this.submittedFileExtension = fileName.last()!;
        }
    }

    downloadFile(filePath: string) {
        this.fileService.downloadFile(filePath);
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
        return !this.examMode && this.fileUploadExercise && !hasExerciseDueDatePassed(this.fileUploadExercise, this.participation);
    }

    get submitButtonTooltip(): string {
        if (!this.submissionFile) {
            return 'artemisApp.fileUploadSubmission.selectFile';
        }

        if (!this.isLate) {
            if (this.isActive && !this.fileUploadExercise.dueDate) {
                return 'entity.action.submitNoDueDateTooltip';
            } else if (this.isActive) {
                return 'entity.action.submitTooltip';
            } else {
                return 'entity.action.dueDateMissedTooltip';
            }
        }

        return 'entity.action.submitDueDateMissedTooltip';
    }
}
