import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { firstValueFrom } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { UMLDiagramType, UMLModel, importDiagram } from '@tumaet/apollon';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { getPositiveAndCappedTotalScore, getTotalMaxPoints } from 'app/exercise/util/exercise.utils';
import dayjs from 'dayjs/esm';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission/modeling-submission.service';
import { Feedback, FeedbackHighlightColor, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { assessmentNavigateBack } from 'app/foundation/util/navigate-back.util';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { Submission, getSubmissionResultByCorrectionRound, getSubmissionResultById } from 'app/exercise/shared/entities/submission/submission.model';
import { getExerciseDashboardLink, getLinkToSubmissionAssessment } from 'app/foundation/util/navigation.utils';
import { ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { onError } from 'app/foundation/util/global.utils';
import { AssessmentNotPossibleYetState, alertIfAssessmentNotPossibleYet, getAssessmentNotPossibleYetState } from 'app/assessment/shared/util/assessment-availability.util';
import { parseCorrectionRound } from 'app/assessment/shared/util/correction-round.util';
import { AssessmentNotPossibleYetComponent } from 'app/assessment/shared/assessment-not-possible-yet/assessment-not-possible-yet.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { parseJson } from 'app/foundation/util/json.util';
import { Course } from 'app/course/shared/entities/course.model';
import { isAllowedToModifyFeedback } from 'app/assessment/manage/services/assessment.service';
import { AssessmentAfterComplaint } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { ComplaintsForTutorComponent } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ModelingAssessmentComponent } from '../modeling-assessment.component';
import {
    FeedbackSuggestionsBannerComponent,
    feedbackSuggestionsNotice as resolveFeedbackSuggestionsNotice,
} from 'app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component';
import { ModelingAssessmentTopLeftDirective } from 'app/modeling/manage/assess/modeling-assessment-top-left.directive';
import { ModelingAssessmentTopRightDirective } from 'app/modeling/manage/assess/modeling-assessment-top-right.directive';
import { ModelingAssessmentLegendComponent, ModelingAssessmentLegendHighlight } from 'app/modeling/manage/assess/modeling-assessment-legend/modeling-assessment-legend.component';
import { AssessmentWorkspaceComponent } from 'app/assessment/manage/assessment-workspace/assessment-workspace.component';
import { AssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { AssessmentNoteComponent } from 'app/assessment/manage/assessment-note/assessment-note.component';
import { AssessmentNote } from 'app/assessment/shared/entities/assessment-note.model';
import { TumUiButtonDirective, TumUiMessageComponent } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-modeling-assessment-editor',
    templateUrl: './modeling-assessment-editor.component.html',
    styleUrls: ['./modeling-assessment-editor.component.scss'],
    imports: [
        AssessmentLayoutComponent,
        ComplaintsForTutorComponent,
        TranslateDirective,
        ModelingAssessmentComponent,
        AssessmentWorkspaceComponent,
        AssessmentInstructionsComponent,
        AssessmentNoteComponent,
        UnreferencedFeedbackComponent,
        RouterLink,
        FeedbackSuggestionsBannerComponent,
        ModelingAssessmentTopLeftDirective,
        ModelingAssessmentTopRightDirective,
        ModelingAssessmentLegendComponent,
        AssessmentNotPossibleYetComponent,
        TumUiButtonDirective,
        TumUiMessageComponent,
    ],
})
export class ModelingAssessmentEditorComponent implements OnInit {
    private alertService = inject(AlertService);
    private datePipe = inject(ArtemisDatePipe);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private modelingSubmissionService = inject(ModelingSubmissionService);
    private modelingAssessmentService = inject(ModelingAssessmentService);
    private accountService = inject(AccountService);
    private location = inject(Location);
    private translateService = inject(TranslateService);
    private complaintService = inject(ComplaintService);
    private structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    private submissionService = inject(SubmissionService);
    private exampleSubmissionService = inject(ExampleSubmissionService);
    private athenaService = inject(AthenaService);

    readonly totalScore = signal(0);
    readonly submission = signal<ModelingSubmission | undefined>(undefined);
    readonly model = signal<UMLModel | undefined>(undefined);
    readonly modelingExercise = signal<ModelingExercise | undefined>(undefined);
    readonly course = signal<Course | undefined>(undefined);
    /** Feedback is mutated in place, so equal references must still notify consumers. */
    readonly result = signal<Result | undefined>(undefined, { equal: () => false });
    referencedFeedback: Feedback[] = [];
    readonly unreferencedFeedback = signal<Feedback[]>([]);
    automaticFeedback: Feedback[] = [];
    readonly highlightedElements = signal<Map<string, string>>(undefined!);
    readonly highlightMissingFeedback = signal(false);

    readonly legendHighlights = computed<ModelingAssessmentLegendHighlight[]>(() => {
        const highlights: ModelingAssessmentLegendHighlight[] = [];
        if (this.hasAutomaticFeedback() && !this.result()?.completionDate) {
            highlights.push({
                color: FeedbackHighlightColor.CYAN,
                text: this.isFeedbackSuggestionsEnabled ? 'artemisApp.modelingAssessment.legend.aiFeedbackSuggestions' : 'artemisApp.modelingAssessment.legend.automaticAssessment',
                info: this.isFeedbackSuggestionsEnabled
                    ? 'artemisApp.assessment.feedbackSuggestions.generativeAIAssessmentInfo'
                    : 'artemisApp.assessment.feedbackSuggestions.automaticAssessmentAvailable',
            });
        }
        if (this.highlightMissingFeedback()) {
            highlights.push({ color: FeedbackHighlightColor.RED, text: 'artemisApp.modelingAssessment.legend.missingAssessment' });
        }
        return highlights;
    });

    readonly assessmentsAreValid = signal(false);
    readonly nextSubmissionBusy = signal<boolean>(false);
    courseId!: number;
    examId = 0;
    exerciseId!: number;
    exerciseGroupId!: number;
    readonly exerciseDashboardLink = signal<string[]>([]);
    userId!: number;
    readonly isAssessor = signal(false);
    readonly complaint = signal<Complaint>(undefined!);
    ComplaintType = ComplaintType;
    readonly isLoading = signal(true);
    readonly loadingFeedbackSuggestions = signal(false);
    readonly isTestRun = signal(false);
    readonly hasAutomaticFeedback = signal(false);
    readonly hasAssessmentDueDatePassed = signal<boolean>(false);
    readonly correctionRound = signal(0);
    private correctionRoundFromUrl = 0;
    readonly resultId = signal<number>(0);
    readonly loadingInitialSubmission = signal(true);
    readonly assessmentNotPossibleYet = signal<AssessmentNotPossibleYetState | undefined>(undefined);
    highlightDifferences = false;
    isApollonModelLoaded = false;

    private cancelConfirmationText!: string;

    constructor() {
        const translateService = this.translateService;

        translateService.get('artemisApp.modelingAssessmentEditor.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    private get feedback(): Feedback[] {
        return [...this.referencedFeedback, ...this.unreferencedFeedback()];
    }

    allAssessmentFeedbacks(): Feedback[] {
        return this.feedback;
    }

    readonly getTotalMaxPoints = getTotalMaxPoints;

    onAssessmentNoteChange(assessmentNote: AssessmentNote): void {
        const result = this.result();
        if (result) {
            result.assessmentNote = assessmentNote;
        }
    }

    get isFeedbackSuggestionsEnabled(): boolean {
        return Boolean(this.modelingExercise()?.feedbackSuggestionModule);
    }

    readonly feedbackSuggestionsNotice = computed(() =>
        resolveFeedbackSuggestionsNotice({
            isLoading: this.loadingFeedbackSuggestions(),
            hasAutomaticFeedback: this.hasAutomaticFeedback(),
            isAssessor: this.isAssessor(),
            resultCompletionDate: this.result()?.completionDate,
            isFeedbackSuggestionsEnabled: this.isFeedbackSuggestionsEnabled,
        }),
    );

    ngOnInit() {
        void this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });

        this.route.queryParamMap.subscribe((queryParams) => {
            this.isTestRun.set(queryParams.get('testRun') === 'true');
            // The URL decides the round, and an unusable value means the first one; see parseCorrectionRound for why
            // Number() alone will not do. Only remembered here, not shown yet; see correctionRoundFromUrl.
            this.correctionRoundFromUrl = parseCorrectionRound(queryParams.get('correction-round'));
        });
        this.route.paramMap.subscribe((params) => {
            this.assessmentNotPossibleYet.set(undefined);
            this.courseId = Number(params.get('courseId'));
            this.exerciseId = Number(params.get('exerciseId'));
            if (params.has('examId')) {
                this.examId = Number(params.get('examId'));
                this.exerciseGroupId = Number(params.get('exerciseGroupId'));
            }

            this.exerciseDashboardLink.set(getExerciseDashboardLink(this.courseId, this.exerciseId, this.examId, this.isTestRun()));

            const submissionId = params.get('submissionId');
            this.resultId.set(Number(params.get('resultId')) || 0);
            this.correctionRound.set(this.correctionRoundFromUrl);
            if (submissionId === 'new') {
                this.loadRandomSubmission(this.exerciseId);
            } else {
                this.loadSubmission(Number(submissionId));
            }
        });
    }

    private async loadFeedbackSuggestions(exercise: ModelingExercise, submission: Submission): Promise<Feedback[]> {
        try {
            return (await firstValueFrom(this.athenaService.getModelingFeedbackSuggestions(exercise, submission))) ?? [];
        } catch (error) {
            this.alertService.error('artemisApp.modelingAssessmentEditor.messages.loadFeedbackSuggestionsFailed');
            return [];
        }
    }

    private loadSubmission(submissionId: number): void {
        this.modelingSubmissionService.getSubmission(submissionId, this.correctionRound(), this.resultId()).subscribe({
            next: (submission: ModelingSubmission) => {
                this.handleReceivedSubmission(submission);
                this.validateFeedback();
            },
            error: (error: HttpErrorResponse) => {
                this.handleErrorResponse(error);
            },
        });
    }

    private loadRandomSubmission(exerciseId: number): void {
        this.modelingSubmissionService.getSubmissionWithoutAssessment(exerciseId, true, this.correctionRound()).subscribe({
            next: (submission?: ModelingSubmission) => {
                if (!submission) {
                    this.submission.set(undefined);
                    this.loadingInitialSubmission.set(false);
                    this.isLoading.set(false);
                    return;
                }

                this.handleReceivedSubmission(submission);
                this.validateFeedback();

                const newUrl = this.location.path().replace('/submissions/new/', `/submissions/${this.submission()!.id}/`);
                this.location.go(newUrl);
            },
            error: (error: HttpErrorResponse) => {
                this.handleErrorResponse(error);
            },
        });
    }

    private handleReceivedSubmission(submission: ModelingSubmission): void {
        this.loadingInitialSubmission.set(false);
        this.referencedFeedback = [];
        this.unreferencedFeedback.set([]);
        this.hasAutomaticFeedback.set(false);
        this.loadingFeedbackSuggestions.set(false);
        this.highlightedElements.set(undefined!);
        this.submission.set(submission);
        const studentParticipation = this.submission()!.participation as StudentParticipation;
        this.modelingExercise.set(studentParticipation.exercise);
        this.course.set(getCourseFromExercise(this.modelingExercise()));
        if (this.resultId() > 0) {
            this.result.set(getSubmissionResultById(submission, this.resultId()));
            this.correctionRound.set(this.result()?.correctionRound ?? 0);
        } else {
            this.result.set(getSubmissionResultByCorrectionRound(this.submission(), this.correctionRound()));
        }
        this.hasAssessmentDueDatePassed.set(!!this.modelingExercise()?.assessmentDueDate && dayjs(this.modelingExercise()!.assessmentDueDate).isBefore(dayjs()));

        if (this.submission()!.model) {
            this.model.set(importDiagram(parseJson(this.submission()!.model!)));
        } else {
            this.alertService.closeAll();
            this.alertService.warning('artemisApp.modelingAssessmentEditor.messages.noModel');
        }

        this.checkPermissions();
        this.getComplaint();

        if (this.result() && this.submission()) {
            this.submission()!.results = [this.result()!];
            this.result()!.submission = this.submission();
        }

        if (!this.modelingExercise()!.diagramType) {
            this.modelingExercise()!.diagramType = UMLDiagramType.ClassDiagram;
        }

        if (this.result()?.feedbacks) {
            this.result.set(this.modelingAssessmentService.convertResult(this.result()!));
        } else if (this.result()) {
            this.result()!.feedbacks = [];
            this.result.set(this.result());
        }

        this.handleFeedback(this.result()?.feedbacks);

        if ((!this.result()?.assessor || this.result()?.assessor?.id === this.userId) && !this.result()?.completionDate) {
            this.alertService.closeAll();
            this.alertService.info('artemisApp.modelingAssessmentEditor.messages.lock');
        }

        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound(), this.submission()!);

        this.isLoading.set(false);

        const automaticFeedbackCount = this.result()?.feedbacks?.filter((feedback) => feedback.type === FeedbackType.AUTOMATIC).length ?? 0;
        if (this.modelingExercise()!.feedbackSuggestionModule && (this.result()?.feedbacks?.length ?? 0) === automaticFeedbackCount) {
            void this.fetchAndApplyFeedbackSuggestions();
        }
    }

    private async fetchAndApplyFeedbackSuggestions(): Promise<void> {
        const submissionAtStart = this.submission();
        const resultAtStart = this.result();
        this.loadingFeedbackSuggestions.set(true);
        try {
            const suggestions = await this.loadFeedbackSuggestions(this.modelingExercise()!, submissionAtStart!);
            if (this.submission() !== submissionAtStart || this.result() !== resultAtStart) {
                return;
            }
            // Feedback suggestions are automatically accepted: add them directly to the editable feedback list.
            if (this.result()) {
                this.result()!.feedbacks = [...(this.result()?.feedbacks || []), ...suggestions];
                this.result.set(this.result());
            }
            this.handleFeedback(this.result()?.feedbacks);
        } finally {
            if (this.submission() == submissionAtStart) {
                this.loadingFeedbackSuggestions.set(false);
            }
        }
    }

    private updateApollonEditorWithFeedback(feedbacks: Feedback[]): void {
        this.referencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference);

        if (!this.isApollonModelLoaded) {
            this.isApollonModelLoaded = true;
            this.calculateTotalScore();
            this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound(), this.submission()!);
        }

        this.validateFeedback();
    }

    private getComplaint(): void {
        if (!this.submission()) {
            return;
        }
        this.complaintService.findBySubmissionId(this.submission()!.id!).subscribe({
            next: (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint.set(this.complaintService.convertComplaintFromServer(res.body, this.result()));
            },
            error: () => {
                this.onError();
            },
        });
    }

    private handleFeedback(feedback?: Feedback[]): void {
        if (!feedback || feedback.length === 0) {
            return;
        }

        this.referencedFeedback = feedback.filter((feedbackElement) => feedbackElement.reference);
        this.unreferencedFeedback.set(feedback.filter((feedbackElement) => !feedbackElement.reference));

        this.hasAutomaticFeedback.set(feedback.some((feedbackItem) => feedbackItem.type === FeedbackType.AUTOMATIC));
        this.highlightAutomaticFeedback();

        if (this.highlightMissingFeedback()) {
            this.highlightElementsWithMissingFeedback();
        }

        this.calculateTotalScore();
    }

    private checkPermissions(): void {
        this.isAssessor.set(this.result()?.assessor?.id === this.userId);
    }

    get canOverride(): boolean {
        if (this.modelingExercise()) {
            if (this.modelingExercise()!.isAtLeastInstructor) {
                return true;
            }
            if (this.complaint() && this.isAssessor()) {
                return false;
            }
            let isBeforeAssessmentDueDate = true;
            if (this.modelingExercise()!.assessmentDueDate) {
                isBeforeAssessmentDueDate = dayjs().isBefore(this.modelingExercise()!.assessmentDueDate);
            }
            return this.isAssessor() && isBeforeAssessmentDueDate;
        }
        return false;
    }

    get readOnly(): boolean {
        return !isAllowedToModifyFeedback(this.isTestRun(), this.isAssessor(), this.hasAssessmentDueDatePassed(), this.result(), this.complaint(), this.modelingExercise());
    }

    private handleErrorResponse(error: HttpErrorResponse): void {
        this.loadingInitialSubmission.set(false);
        this.submission.set(undefined);

        this.isLoading.set(false);
        const assessmentNotPossibleYet = getAssessmentNotPossibleYetState(error);
        if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
            this.navigateBack();
        } else if (assessmentNotPossibleYet) {
            this.resetAssessmentState();
            this.assessmentNotPossibleYet.set(assessmentNotPossibleYet);
            this.alertService.closeAll();
        } else {
            this.onError();
        }
    }

    private resetAssessmentState(): void {
        this.submission.set(undefined);
        this.modelingExercise.set(undefined);
        this.result.set(undefined);
        this.model.set(undefined);
    }

    onError(): void {
        this.resetAssessmentState();
        this.alertService.closeAll();
        this.alertService.error('artemisApp.modelingAssessmentEditor.messages.loadSubmissionFailed');
    }

    onSaveAssessment() {
        if (!this.modelingAssessmentService.isFeedbackTextValid(this.feedback)) {
            this.alertService.error('artemisApp.modelingAssessmentEditor.messages.feedbackTextTooLong');
            return;
        }

        this.modelingAssessmentService.saveAssessment(this.result()!.id!, this.feedback, this.submission()!.id!, this.result()!.assessmentNote?.note).subscribe({
            next: (result: Result) => {
                this.result.set(result);
                this.handleFeedback(this.result()!.feedbacks);
                this.alertService.closeAll();
                this.alertService.success('artemisApp.modelingAssessmentEditor.messages.saveSuccessful');
            },
            error: (error: HttpErrorResponse) => {
                if (alertIfAssessmentNotPossibleYet(error, this.alertService, this.datePipe)) {
                    return;
                }
                this.alertService.closeAll();
                this.alertService.error('artemisApp.modelingAssessmentEditor.messages.saveFailed');
            },
        });
    }

    onSubmitAssessment() {
        const totalNumberOfElements = (this.model()?.nodes.length ?? 0) + (this.model()?.edges.length ?? 0);
        if ((this.model() && this.referencedFeedback.length < totalNumberOfElements) || !this.assessmentsAreValid()) {
            const confirmationMessage = this.translateService.instant('artemisApp.modelingAssessmentEditor.messages.confirmSubmission');

            const isBeforeAssessmentDueDate = this.modelingExercise()?.assessmentDueDate && dayjs().isBefore(this.modelingExercise()!.assessmentDueDate);
            if (isBeforeAssessmentDueDate) {
                this.submitAssessment();
            } else {
                const confirm = window.confirm(confirmationMessage);
                if (confirm) {
                    this.submitAssessment();
                } else {
                    this.highlightMissingFeedback.set(true);
                    this.highlightElementsWithMissingFeedback();
                }
            }
        } else {
            this.submitAssessment();
        }
    }

    private submitAssessment() {
        if (!this.modelingAssessmentService.isFeedbackTextValid(this.feedback)) {
            this.alertService.error('artemisApp.modelingAssessmentEditor.messages.feedbackTextTooLong');
            return;
        }

        this.modelingAssessmentService.saveAssessment(this.result()!.id!, this.feedback, this.submission()!.id!, this.result()!.assessmentNote?.note, true).subscribe({
            next: (result: Result) => {
                this.result.set(result);

                this.alertService.closeAll();
                this.alertService.success('artemisApp.modelingAssessmentEditor.messages.submitSuccessful');

                this.highlightMissingFeedback.set(false);
            },
            error: (error: HttpErrorResponse) => {
                if (alertIfAssessmentNotPossibleYet(error, this.alertService, this.datePipe)) {
                    return;
                }
                let errorMessage = 'artemisApp.modelingAssessmentEditor.messages.submitFailed';
                if (error.error && error.error.entityName && error.error.message) {
                    errorMessage = `artemisApp.${error.error.entityName}.${error.error.message}`;
                }
                this.alertService.closeAll();
                this.alertService.error(errorMessage);
            },
        });
    }

    onUpdateAssessmentAfterComplaint(assessmentAfterComplaint: AssessmentAfterComplaint): void {
        this.validateFeedback();
        if (!this.assessmentsAreValid()) {
            this.alertService.error('artemisApp.modelingAssessment.invalidAssessments');
            assessmentAfterComplaint.onError();
            return;
        }

        const feedbacks = this.complaintService.getFeedbacksForUpdateAfterComplaint(this.feedback);
        const complaintResponse = this.complaintService.getComplaintResponseForUpdateAfterComplaint(assessmentAfterComplaint.complaintResponse);

        this.modelingAssessmentService.updateAssessmentAfterComplaint(feedbacks, complaintResponse, this.submission()!.id!, this.result()?.assessmentNote?.note).subscribe({
            next: (response) => {
                assessmentAfterComplaint.onSuccess();
                this.result.set(response.body!);
                this.alertService.closeAll();
                this.alertService.success('artemisApp.modelingAssessmentEditor.messages.updateAfterComplaintSuccessful');
            },
            error: (httpErrorResponse: HttpErrorResponse) => {
                assessmentAfterComplaint.onError();
                this.alertService.closeAll();
                const error = httpErrorResponse.error;
                if (error && error.errorKey && error.errorKey === 'complaintLock') {
                    this.alertService.error(error.message, error.params);
                } else {
                    this.alertService.error('artemisApp.modelingAssessmentEditor.messages.updateAfterComplaintFailed');
                }
            },
        });
    }

    onCancelAssessment() {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.modelingAssessmentService.cancelAssessment(this.submission()!.id!, this.result()?.id).subscribe(() => {
                this.navigateBack();
            });
        }
    }

    onFeedbackChanged(feedback: Feedback[]) {
        this.updateApollonEditorWithFeedback(feedback);
    }

    assessNext() {
        this.isLoading.set(true);
        this.nextSubmissionBusy.set(true);
        this.modelingSubmissionService.getSubmissionWithoutAssessment(this.modelingExercise()!.id!, true, this.correctionRound()).subscribe({
            next: (submission?: ModelingSubmission) => {
                if (!submission) {
                    this.submission.set(undefined);
                    return;
                }

                this.nextSubmissionBusy.set(false);
                this.isLoading.set(false);

                const url = getLinkToSubmissionAssessment(ExerciseType.MODELING, this.courseId, this.exerciseId, undefined, submission.id!, this.examId, this.exerciseGroupId);
                // Merge rather than replace: a supplied queryParams object drops every other parameter, testRun among them.
                void this.router.navigate(url, { queryParams: { 'correction-round': this.correctionRound() }, queryParamsHandling: 'merge' });
            },
            error: (error: HttpErrorResponse) => {
                this.nextSubmissionBusy.set(false);
                this.handleErrorResponse(error);
            },
        });
    }

    validateFeedback() {
        this.calculateTotalScore();
        const hasReferencedFeedback = Feedback.haveCredits(this.referencedFeedback);
        const hasUnreferencedFeedback = Feedback.haveCreditsAndComments(this.unreferencedFeedback());
        this.assessmentsAreValid.set((hasReferencedFeedback && this.unreferencedFeedback().length === 0) || hasUnreferencedFeedback);
        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound(), this.submission()!);
    }

    navigateBack() {
        assessmentNavigateBack(this.location, this.router, this.modelingExercise(), this.submission(), this.isTestRun());
    }

    private highlightElementsWithMissingFeedback() {
        if (!this.model()) {
            return;
        }

        const updatedHighlights = this.highlightedElements()
            ? this.removeHighlightedFeedbackOfColor(this.highlightedElements(), FeedbackHighlightColor.RED)
            : new Map<string, string>();

        const referenceIds = this.referencedFeedback.map((feedback) => feedback.referenceId);
        for (const element of Object.values(this.model()!.nodes)) {
            if (!referenceIds.includes(element.id)) {
                updatedHighlights.set(element.id, FeedbackHighlightColor.RED);
            }
        }
        for (const element of Object.values(this.model()!.edges)) {
            if (!referenceIds.includes(element.id)) {
                updatedHighlights.set(element.id, FeedbackHighlightColor.RED);
            }
        }
        this.highlightedElements.set(updatedHighlights);
    }

    private highlightAutomaticFeedback() {
        if (this.result() && this.result()!.completionDate) {
            return;
        }

        const updatedHighlights = this.highlightedElements()
            ? this.removeHighlightedFeedbackOfColor(this.highlightedElements(), FeedbackHighlightColor.CYAN)
            : new Map<string, string>();

        for (const feedbackItem of this.referencedFeedback) {
            if (feedbackItem.type === FeedbackType.AUTOMATIC && feedbackItem.referenceId) {
                updatedHighlights.set(feedbackItem.referenceId, FeedbackHighlightColor.CYAN);
            }
        }
        this.highlightedElements.set(updatedHighlights);
    }

    private removeHighlightedFeedbackOfColor(highlightedElements: Map<string, string>, color: string) {
        return new Map<string, string>([...highlightedElements].filter(([, value]) => value !== color));
    }

    calculateTotalScore() {
        const maxPoints = getTotalMaxPoints(this.modelingExercise());
        const creditsTotalScore = this.structuredGradingCriterionService.computeTotalScore(this.feedback);
        this.totalScore.set(getPositiveAndCappedTotalScore(creditsTotalScore, maxPoints));
    }

    useStudentSubmissionAsExampleSubmission(): void {
        if (this.submission() && this.modelingExercise()) {
            this.exampleSubmissionService.import(this.submission()!.id!, this.modelingExercise()!.id!).subscribe({
                next: () => this.alertService.success('artemisApp.exampleSubmission.submitSuccessful'),
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
    }
}
