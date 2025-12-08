import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
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
import { checkSubsequentFeedbackInAssessment } from 'app/assessment/shared/entities/feedback.model';
import { onError } from 'app/shared/util/global.utils';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { FileService } from 'app/shared/service/file.service';
import { map } from 'rxjs/operators';
import { firstValueFrom } from 'rxjs';

@Component({
    selector: 'jhi-file-upload-submission',
    templateUrl: './file-upload-submission.component.html',
    imports: [
        HeaderParticipationPageComponent,
        ButtonComponent,
        ResizeableContainerComponent,
        TranslateDirective,
        ExerciseActionButtonComponent,
        AdditionalFeedbackComponent,
        RatingComponent,
        ComplaintsStudentViewComponent,
        FaIconComponent,
        UpperCasePipe,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        HtmlForMarkdownPipe,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileUploadSubmissionComponent implements ComponentCanDeactivate {
    private route = inject(ActivatedRoute);
    private fileUploadSubmissionService = inject(FileUploadSubmissionService);
    private alertService = inject(AlertService);
    private fileService = inject(FileService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private fileUploadAssessmentService = inject(FileUploadAssessmentService);
    private accountService = inject(AccountService);

    readonly addParticipationToResult = addParticipationToResult;
    readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

    readonly participationId = input<number>();
    readonly displayHeader = input(true);
    readonly expandProblemStatement = input(true);
    readonly displayedInExamSummary = input(false);

    readonly inputExercise = input<FileUploadExercise>();
    readonly inputSubmission = input<FileUploadSubmission>();
    readonly inputParticipation = input<StudentParticipation>();

    submission = signal<FileUploadSubmission | undefined>(undefined);
    fileUploadExercise = signal<FileUploadExercise | undefined>(undefined);
    participation = signal<StudentParticipation | undefined>(undefined);
    result = signal<Result | undefined>(undefined);
    submissionFile = signal<File | undefined>(undefined);

    // Derived state via computed signals
    resultWithComplaint = computed(() => getFirstResultWithComplaint(this.submission()));
    course = computed(() => getCourseFromExercise(this.fileUploadExercise()));
    examMode = computed(() => !!this.fileUploadExercise()?.exerciseGroup);

    submittedFileName = computed(() => {
        const filePath = this.submission()?.filePath;
        return filePath ? filePath.split('/').pop()! : '';
    });

    submittedFileExtension = computed(() => {
        const fileName = this.submittedFileName();
        return fileName ? fileName.split('.').pop()! : '';
    });

    isAfterAssessmentDueDate = computed(() => {
        const exercise = this.fileUploadExercise();
        return !exercise?.assessmentDueDate || dayjs().isAfter(exercise.assessmentDueDate);
    });

    isLate = computed(() => {
        const exercise = this.fileUploadExercise();
        const participation = this.participation();
        return !!(
            exercise &&
            exercise.dueDate &&
            participation?.initializationDate &&
            dayjs(participation.initializationDate).isAfter(getExerciseDueDate(exercise, participation))
        );
    });

    acceptedFileExtensions = computed(() => {
        return (
            this.fileUploadExercise()
                ?.filePattern?.split(',')
                .map((extension) => `.${extension}`)
                .join(',') ?? ''
        );
    });

    isOwnerOfParticipation = signal(false);
    isSaving = signal(false);

    // Converted Getters to Computed
    unreferencedFeedback = computed(() => {
        const result = this.result();
        if (result?.feedbacks) {
            checkSubsequentFeedbackInAssessment(result.feedbacks);
            return getManualUnreferencedFeedback(result.feedbacks);
        }
        return undefined;
    });

    isActive = computed(() => {
        const exercise = this.fileUploadExercise();
        const participation = this.participation();
        return !this.examMode() && !!exercise && !!participation && !hasExerciseDueDatePassed(exercise, participation);
    });

    submitButtonTooltip = computed(() => {
        if (!this.submissionFile()) {
            return 'artemisApp.fileUploadSubmission.selectFile';
        }

        if (!this.isLate()) {
            const exercise = this.fileUploadExercise();
            // Using isActive() computed value
            if (this.isActive() && exercise && !exercise.dueDate) {
                return 'entity.action.submitNoDueDateTooltip';
            } else if (this.isActive()) {
                return 'entity.action.submitTooltip';
            } else {
                return 'entity.action.dueDateMissedTooltip';
            }
        }

        return 'entity.action.submitDueDateMissedTooltip';
    });

    faDownload = faDownload;
    readonly ButtonType = ButtonType;

    // Icons
    farListAlt = faListAlt;

    private routeParticipationId = toSignal(this.route.params.pipe(map((params) => Number(params['participationId']))));

    constructor() {
        effect((onCleanup) => {
            // Priority: Input values -> Route values
            if (this.inputValuesArePresent()) {
                this.setupComponentWithInputValues();
            } else {
                // Check direct input ID (signal) or route ID (signal)
                const pId = this.participationId() ?? this.routeParticipationId();
                if (pId && !Number.isNaN(pId)) {
                    const sub = this.fileUploadSubmissionService.getDataForFileUploadEditor(pId).subscribe({
                        next: (submission: FileUploadSubmission) => {
                            this.handleDataLoad(submission);
                        },
                        error: (error: HttpErrorResponse) => onError(this.alertService, error),
                    });
                    onCleanup(() => sub.unsubscribe());
                } else if (pId && Number.isNaN(pId)) {
                    // Should be handled better, but alert is side effect
                    // We avoid alerting inside effect loop unless guarded
                }
            }
        });
    }

    private handleDataLoad(submission: FileUploadSubmission) {
        const tmpResult = getLatestSubmissionResult(submission);
        const participation = submission.participation as StudentParticipation;

        // reconnect participation <--> submission
        participation.submissions = [omit(submission, 'participation') as FileUploadSubmission];

        this.submission.set(submission);
        this.result.set(tmpResult);
        const exercise = participation.exercise as FileUploadExercise;
        this.fileUploadExercise.set(exercise);
        exercise.studentParticipations = [participation];
        this.participation.set(participation);

        if (this.submission()?.submitted) {
            this.setSubmittedFile();
        }
        if (this.submission()?.submitted && this.result()?.completionDate) {
            const submissionId = this.submission()?.id;
            if (submissionId) {
                // Nested subscribe - acceptable for dependent data fetch
                this.fileUploadAssessmentService.getAssessment(submissionId).subscribe((assessmentResult: Result) => {
                    this.result.set(assessmentResult);
                });
            }
        }
        this.isOwnerOfParticipation.set(this.accountService.isOwnerOfParticipation(participation));
    }

    private inputValuesArePresent(): boolean {
        // Signals are tracked here
        return !!(this.inputExercise() || this.inputSubmission() || this.inputParticipation());
    }

    private setupComponentWithInputValues() {
        if (this.inputExercise()) {
            this.fileUploadExercise.set(this.inputExercise()!);
        }
        if (this.inputSubmission()) {
            this.submission.set(this.inputSubmission()!);
        }
        if (this.inputParticipation()) {
            this.participation.set(this.inputParticipation()!);
        }

        if (this.submission()?.submitted) {
            this.setSubmittedFile();
        }
    }

    /**
     * Uploads a submission file and submits File Upload Exercise
     */
    async submitExercise() {
        if (this.isSaving()) {
            return;
        }

        const file = this.submissionFile();
        const submission = this.submission();
        const currentExercise = this.fileUploadExercise();

        if (!submission || !file || !currentExercise || !currentExercise.id) {
            return;
        }
        this.isSaving.set(true);

        try {
            const res = await firstValueFrom(this.fileUploadSubmissionService.update(submission, currentExercise.id, file));
            const newSubmission = res.body;

            if (newSubmission) {
                this.submission.set(newSubmission);
                const participation = newSubmission.participation as StudentParticipation;
                if (participation) {
                    newSubmission.participation!.submissions = [newSubmission];
                    this.participationWebsocketService.addParticipation(participation, currentExercise);
                    this.participation.set(participation);
                    this.fileUploadExercise.update((exercise) => {
                        if (exercise) {
                            exercise.studentParticipations = [participation];
                        }
                        return exercise;
                    });
                }

                this.result.set(getLatestSubmissionResult(newSubmission));
                this.setSubmittedFile();
                if (this.isActive()) {
                    this.alertService.success('artemisApp.fileUploadExercise.submitSuccessful');
                } else {
                    this.alertService.warning('artemisApp.fileUploadExercise.submitDueDateMissed');
                }
            }
        } catch (error: unknown) {
            this.submission.update((sub) => {
                if (sub) sub.submitted = false;
                return sub;
            });
            let serverError: string | null = null;
            if (error instanceof HttpErrorResponse) {
                serverError = error.headers?.get('X-artemisApp-error');
            }

            if (serverError) {
                this.alertService.error(serverError, { fileName: file.name });
            } else {
                this.alertService.error('artemisApp.fileUploadSubmission.fileUploadError', { fileName: file.name });
            }
            const fileInput = this.fileInput();
            if (fileInput && fileInput.nativeElement) {
                fileInput.nativeElement.value = '';
            }
            this.submissionFile.set(undefined);
        } finally {
            this.isSaving.set(false);
        }
    }

    /**
     * Sets file submission for exercise
     */
    setFileSubmissionForExercise(event: Event): void {
        const target = event.target as HTMLInputElement;
        if (target.files && target.files.length) {
            const fileList: FileList = target.files;
            const submissionFile = fileList[0];
            const exercise = this.fileUploadExercise();
            const allowedFileExtensions = exercise?.filePattern?.split(',') ?? [];

            if (!allowedFileExtensions.some((extension) => submissionFile.name.toLowerCase().endsWith(extension.trim()))) {
                this.alertService.error('artemisApp.fileUploadSubmission.fileExtensionError');
            } else if (submissionFile.size > MAX_SUBMISSION_FILE_SIZE) {
                this.alertService.error('artemisApp.fileUploadSubmission.fileTooBigError', { fileName: submissionFile.name });
            } else {
                this.submissionFile.set(submissionFile);
            }
        }
    }

    private setSubmittedFile() {
        this.submissionFile.set(undefined);
    }

    downloadFile(filePath: string) {
        this.fileService.downloadFile(filePath);
    }

    /**
     * Returns false if user selected a file, but didn't submit the exercise, true otherwise.
     */
    canDeactivate(): boolean {
        const submission = this.submission();
        return !(submission && !submission.submitted && this.submissionFile());
    }
}
