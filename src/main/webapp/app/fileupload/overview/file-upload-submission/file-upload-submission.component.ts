import { Component, ElementRef, Input, OnInit, ViewChild, inject } from '@angular/core';
import { UpperCasePipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { HeaderParticipationPageComponent } from 'app/exercise/exercise-headers/participation-page/header-participation-page.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { FileUploadSubmissionService } from 'app/fileupload/overview/file-upload-submission.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { omit } from 'lodash-es';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { getFirstResultWithComplaint, getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { addParticipationToResult, getManualUnreferencedFeedback } from 'app/exercise/result/result.utils';
import { Feedback, checkSubsequentFeedbackInAssessment } from 'app/assessment/shared/entities/feedback.model';
import { onError } from 'app/shared/util/global.utils';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { FileService } from 'app/shared/service/file.service';

@Component({
    selector: 'jhi-file-upload-submission',
    templateUrl: './file-upload-submission.component.html',
    imports: [
        HeaderParticipationPageComponent,
        ButtonComponent,
        ResizeableContainerComponent,
        TranslateDirective,
        ExerciseActionButtonComponent,
        UnifiedFeedbackComponent,
        RatingComponent,
        ComplaintsStudentViewComponent,
        FaIconComponent,
        UpperCasePipe,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        HtmlForMarkdownPipe,
    ],
})
export class FileUploadSubmissionComponent implements OnInit, ComponentCanDeactivate {
    private route = inject(ActivatedRoute);
    private fileUploadSubmissionService = inject(FileUploadSubmissionService);
    private alertService = inject(AlertService);
    private fileService = inject(FileService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private fileUploadAssessmentService = inject(FileUploadAssessmentService);
    private accountService = inject(AccountService);

    readonly addParticipationToResult = addParticipationToResult;
    @ViewChild('fileInput', { static: false }) fileInput: ElementRef;

    @Input() participationId?: number;
    @Input() displayHeader = true;
    @Input() expandProblemStatement = true;
    @Input() displayedInExamSummary = false;

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

    // Icons
    farListAlt = faListAlt;

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
                    const tmpResult = getLatestSubmissionResult(submission);
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
            return getManualUnreferencedFeedback(this.result.feedbacks);
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
