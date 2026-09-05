import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { notUndefined } from 'app/foundation/util/string-pure.utils';
import { onError } from 'app/foundation/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { NEW_ASSESSMENT_PATH } from 'app/text/manage/assess/text-submission-assessment.route';
import { assessmentNavigateBack } from 'app/foundation/util/navigate-back.util';
import {
    getLatestSubmissionResult,
    getSubmissionResultByCorrectionRound,
    getSubmissionResultById,
    setLatestSubmissionResult,
    setSubmissionResultByCorrectionRound,
} from 'app/exercise/shared/entities/submission/submission.model';
import { TextAssessmentBaseComponent } from 'app/text/manage/assess/assessment-base/text-assessment-base.component';
import { getExerciseDashboardLink, getLinkToSubmissionAssessment } from 'app/foundation/util/navigation.utils';
import { ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { getTotalMaxPoints } from 'app/exercise/util/exercise.utils';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { Course } from 'app/course/shared/entities/course.model';
import { isAllowedToModifyFeedback } from 'app/assessment/manage/services/assessment.service';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { AssessmentAfterComplaint } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { Subscription } from 'rxjs';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { ResizeableContainerComponent } from 'app/shared-ui/resizeable-container/resizeable-container.component';
import { ScoreDisplayComponent } from 'app/exercise/score-display/score-display.component';
import { TextAssessmentAreaComponent } from 'app/text/manage/assess/text-assessment-area/text-assessment-area.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { FeedbackSuggestionsBannerComponent } from 'app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component';
import { AssessmentNotPossibleYetComponent } from 'app/assessment/shared/assessment-not-possible-yet/assessment-not-possible-yet.component';
import { AssessmentNotPossibleYetState } from 'app/assessment/shared/util/assessment-availability.util';
import { TextAssessmentRouteData } from 'app/text/manage/assess/service/text-submission-assessment-resolve.service';
import { AiExperienceOptInService } from 'app/logos/ai-experience-opt-in.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATHENA } from 'app/app.constants';

@Component({
    selector: 'jhi-text-submission-assessment',
    templateUrl: './text-submission-assessment.component.html',
    styleUrls: ['./text-submission-assessment.component.scss'],
    imports: [
        AssessmentLayoutComponent,
        ResizeableContainerComponent,
        ScoreDisplayComponent,
        TextAssessmentAreaComponent,
        FaIconComponent,
        TranslateDirective,
        AssessmentInstructionsComponent,
        UnreferencedFeedbackComponent,
        AssessmentNotPossibleYetComponent,
        RouterLink,
        FeedbackSuggestionsBannerComponent,
    ],
})
export class TextSubmissionAssessmentComponent extends TextAssessmentBaseComponent implements OnInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private location = inject(Location);
    private route = inject(ActivatedRoute);
    private complaintService = inject(ComplaintService);
    private submissionService = inject(SubmissionService);
    private exampleSubmissionService = inject(ExampleSubmissionService);
    private athenaService = inject(AthenaService);
    private translateService = inject(TranslateService);
    private aiExperienceOptInService = inject(AiExperienceOptInService);
    private profileService = inject(ProfileService);

    /*
     * The instance of this component is REUSED for multiple assessments if using the "Assess Next" button!
     * All properties must be initialized with a default value (or null) in the resetComponent() method.
     * For traceability: Keep order in resetComponent() consistent with declaration.
     */

    participation?: StudentParticipation;
    readonly result = signal<Result | undefined>(undefined);
    readonly unreferencedFeedback = signal<Feedback[]>([]);
    readonly complaint = signal<Complaint | undefined>(undefined);
    readonly totalScore = signal<number>(0);
    readonly isTestRun = signal(false);
    isLoading = signal(true);
    readonly loadingFeedbackSuggestions = signal(false);
    readonly saveBusy = signal<boolean>(false);
    readonly submitBusy = signal<boolean>(false);
    readonly cancelBusy = signal<boolean>(false);
    readonly nextSubmissionBusy = signal<boolean>(false);
    readonly isAssessor = signal<boolean>(false);
    readonly assessmentsAreValid = signal<boolean>(false);
    readonly noNewSubmissions = signal<boolean>(false);
    readonly hasAutomaticFeedback = signal(false);
    readonly hasAssessmentDueDatePassed = signal<boolean>(false);
    readonly correctionRound = signal<number>(0);
    readonly resultId = signal<number>(0);
    readonly loadingInitialSubmission = signal(true);
    // Set when the server refused to open the assessment because the exam is not over yet: the submission exists, so the
    // page explains the wait instead of claiming that it was not found.
    readonly assessmentNotPossibleYet = signal<AssessmentNotPossibleYetState | undefined>(undefined);
    readonly highlightDifferences = signal(false);

    /*
     * Non-reset properties:
     * These properties are not reset on purpose, as they cannot change between assessments.
     */
    private cancelConfirmationText!: string; // set in constructor from the (already-loaded) translation before cancel() reads it

    // ExerciseId is updated from Route Subscription directly.
    exerciseId!: number; // set in ngOnInit() from the route paramMap before any read
    courseId!: number; // set in ngOnInit() from the route paramMap before any read
    readonly course = signal<Course | undefined>(undefined);
    examId = 0;
    exerciseGroupId!: number; // set in ngOnInit() from the route paramMap in exam mode before it is read
    readonly exerciseDashboardLink = signal<string[]>([]);
    isExamMode = false;

    private feedbackSuggestionsObservable?: Subscription;

    private get referencedFeedback(): Feedback[] {
        return this.textBlockRefs.map(({ feedback }) => feedback).filter(notUndefined);
    }

    private get assessments(): Feedback[] {
        return [...this.referencedFeedback, ...this.unreferencedFeedback()];
    }

    /** Full assessment feedback for the unreferenced-feedback score summary. */
    allAssessmentFeedbacks(): Feedback[] {
        return this.assessments;
    }

    readonly getTotalMaxPoints = getTotalMaxPoints;

    // Icons
    farListAlt = faListAlt;

    constructor() {
        super();
        this.translateService.get('artemisApp.textAssessment.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
        this.resetComponent();
    }

    /**
     * This method is called before the component is REUSED!
     * All properties MUST be set to a default value (e.g. null) to prevent data corruption by state leaking into following new assessments.
     */
    private resetComponent(): void {
        this.participation = undefined;
        this.submission = undefined;
        this.exercise = undefined;
        this.result.set(undefined);
        this.unreferencedFeedback.set([]);
        this.textBlockRefs = [];
        this.unusedTextBlockRefs = [];
        this.complaint.set(undefined);
        this.totalScore.set(0);

        this.isLoading.set(true);
        this.loadingFeedbackSuggestions.set(false);
        this.saveBusy.set(false);
        this.submitBusy.set(false);
        this.cancelBusy.set(false);
        this.nextSubmissionBusy.set(false);
        this.isAssessor.set(false);
        this.assessmentsAreValid.set(false);
        this.noNewSubmissions.set(false);
        this.hasAutomaticFeedback.set(false);
        this.highlightDifferences.set(false);
        this.assessmentNotPossibleYet.set(undefined);
    }

    /**
     * Life cycle hook to indicate component creation is done
     */
    override async ngOnInit(): Promise<void> {
        await super.ngOnInit();
        this.route.queryParamMap.subscribe((queryParams) => {
            this.isTestRun.set(queryParams.get('testRun') === 'true');
        });

        this.activatedRoute.paramMap.subscribe((paramMap) => {
            this.exerciseId = Number(paramMap.get('exerciseId'));
            this.resultId.set(Number(paramMap.get('resultId')) || 0);
            this.courseId = Number(paramMap.get('courseId'));
            if (paramMap.has('examId')) {
                this.examId = Number(paramMap.get('examId'));
                this.exerciseGroupId = Number(paramMap.get('exerciseGroupId'));
                this.isExamMode = true;
            }
            this.exerciseDashboardLink.set(getExerciseDashboardLink(this.courseId, this.exerciseId, this.examId, this.isTestRun()));
        });
        this.activatedRoute.data.subscribe(({ textAssessmentData }) => {
            this.setPropertiesFromServerResponse(textAssessmentData);
            this.validateFeedback();
        });
    }

    ngOnDestroy(): void {
        this.feedbackSuggestionsObservable?.unsubscribe();
    }

    private setPropertiesFromServerResponse(routeData?: TextAssessmentRouteData) {
        this.resetComponent();
        this.loadingInitialSubmission.set(false);
        // The round comes from the resolver, which requested the participation for it, rather than from the URL again:
        // the results below are indexed by the round, so reading the parameter a second time here would let the page
        // index a round the resolver never loaded. This also matters when the router reuses this component for the next
        // submission, where the round must follow the newly resolved data instead of staying on the previous one.
        this.correctionRound.set(routeData?.correctionRound ?? 0);
        const studentParticipation = routeData?.participation;
        if (!studentParticipation) {
            // The resolver swallows load errors, so a missing participation can also mean that the exam is still running.
            // Saying so keeps the page from claiming that a submission which does exist was not found.
            this.assessmentNotPossibleYet.set(routeData?.assessmentNotPossibleYet);
            // Show "No New Submission" banner on .../submissions/new/assessment route
            this.noNewSubmissions.set(this.isNewAssessmentRoute);
            return;
        }

        this.participation = studentParticipation;
        this.submission = this.participation?.submissions?.last();
        this.exercise = this.participation?.exercise;
        this.course.set(getCourseFromExercise(this.exercise));
        setLatestSubmissionResult(this.submission, getLatestSubmissionResult(this.submission));

        if (this.resultId() > 0) {
            this.result.set(getSubmissionResultById(this.submission, this.resultId()));
            // Read off the result, not off its position in the results array.
            this.correctionRound.set(this.result()?.correctionRound ?? 0);
        } else {
            this.result.set(getSubmissionResultByCorrectionRound(this.submission, this.correctionRound()));
        }

        this.hasAssessmentDueDatePassed.set(!!this.exercise!.assessmentDueDate && dayjs(this.exercise!.assessmentDueDate).isBefore(dayjs()));

        this.prepareTextBlocksAndFeedbacks();
        this.getComplaint();
        this.updateUrlIfNeeded();

        this.checkPermissions(this.result());
        this.totalScore.set(this.computeTotalScore(this.assessments));
        this.isLoading.set(false);

        if (this.isFeedbackSuggestionsEnabled() && !this.requiresAiExperienceOptIn()) {
            this.loadFeedbackSuggestions();
        }

        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound(), this.submission!);
    }

    private updateUrlIfNeeded() {
        if (this.isNewAssessmentRoute) {
            // Update the url with the new id, without reloading the page, to make the history consistent
            // Keep the query parameters. The correction round is carried only in the URL, so rebuilding the URL from
            // the route commands alone dropped it and the next load of this page started the second correction round
            // as the first one (#13396). The modeling and file upload editors rewrite the hash in place and therefore
            // never lost it.
            const newUrl = this.router
                .createUrlTree(
                    getLinkToSubmissionAssessment(
                        ExerciseType.TEXT,
                        this.courseId,
                        this.exerciseId,
                        this.participation!.id,
                        this.submission!.id!,
                        this.examId,
                        this.exerciseGroupId,
                    ),
                    { queryParams: this.route.snapshot.queryParams },
                )
                .toString();
            this.location.go(newUrl);
        }
    }

    private get isNewAssessmentRoute(): boolean {
        return this.activatedRoute.routeConfig?.path === NEW_ASSESSMENT_PATH;
    }

    readonly isFeedbackSuggestionsEnabled = computed(() => Boolean(this.exercise?.feedbackSuggestionModule) && this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATHENA));

    readonly requiresAiExperienceOptIn = computed(() => this.isFeedbackSuggestionsEnabled() && !this.aiExperienceOptInService.hasAcceptedAiUsage());

    onOptInToAiFeedbackSuggestions(): void {
        this.aiExperienceOptInService.promptForAiUsage(() => this.loadFeedbackSuggestions());
    }

    private checkPermissions(result?: Result): void {
        this.isAssessor.set(result?.assessor?.id === this.userId);
    }

    /**
     * Adds a TextBlockRef, adjusting existing automatic text blocks to fit around the new text block if necessary (and possible).
     * Example: There already are 2 text blocks:
     *          - block 1 from index 0 to 10 (automatically generated)
     *          - block 2 from index 10 to 20 (automatically generated)
     *          Now, we add a new text block ref with feedback from index 5 to 15.
     *          Then, we have three text blocks: 0-5, 5-15, 15-20.
     * If the split conflicts with a manual feedback, we don't add the TextBlockRef at all.
     *
     * @param refToAdd The TextBlockRef to add (text block + feedback on it)
     */
    private addAutomaticTextBlockRef(refToAdd: TextBlockRef) {
        const newTextBlockRefs: TextBlockRef[] = [];
        const [start, end] = [refToAdd.block!.startIndex!, refToAdd.block!.endIndex!];
        for (const existingBlockRef of this.textBlockRefs) {
            const [exStart, exEnd] = [existingBlockRef.block!.startIndex!, existingBlockRef.block!.endIndex!];
            if (exStart === start && exEnd === end) {
                // existing: |---|
                // to add:   |---|
                // -> replace existing block (don't add existing one)
            } else if (exEnd <= start || exStart >= end) {
                // existing: |---|  or   |---|
                // to add:         |---|
                // -> no overlap, just add
                newTextBlockRefs.push(existingBlockRef);
            } else {
                if (exStart < start) {
                    // Existing text block starts before text block to add
                    if (exEnd > end) {
                        // existing: |----------|
                        // to add:      |---|
                        // ->        |--|---|---|
                        //          (|ex|add|new|)
                        // (split into three text blocks)
                        const newBlockRef = new TextBlockRef(new TextBlock(), undefined);
                        newBlockRef.block!.startIndex = end;
                        newBlockRef.block!.endIndex = exEnd;
                        newBlockRef.block!.submissionId = this.submission?.id;

                        existingBlockRef.block!.endIndex = start;
                        newTextBlockRefs.push(existingBlockRef);
                        newTextBlockRefs.push(newBlockRef);
                    } else {
                        // existing: |-----|
                        // to add:      |-----|
                        // ->        |--|-----|
                        // ("squish" the existing text block)
                        existingBlockRef.block!.endIndex = start;
                        newTextBlockRefs.push(existingBlockRef);
                    }
                } else if (exEnd > end) {
                    // existing:       |-----|
                    // to add:    |------|
                    // ->         |------|---|
                    // ("squish" the existing text block)
                    existingBlockRef.block!.startIndex = end;
                    newTextBlockRefs.push(existingBlockRef);
                } else if (exEnd == end) {
                    // existing:       |-----|
                    // to add:    |----------|
                    // ->         |-add--|ex-|
                    // ("squish" the new text block)
                    refToAdd.block!.endIndex = exStart;
                    newTextBlockRefs.push(existingBlockRef);
                }
            }
        }

        // Add the text block to add
        newTextBlockRefs.push(refToAdd);

        // Sort the new text block refs by their start index
        newTextBlockRefs.sort((ref1, ref2) => ref1.block!.startIndex! - ref2.block!.startIndex!);

        // Update the text on all text block refs
        for (const blockRef of newTextBlockRefs) {
            blockRef.block!.text = this.submission!.text!.substring(blockRef.block!.startIndex!, blockRef.block!.endIndex);
        }

        this.textBlockRefs = newTextBlockRefs;
        this.submission!.blocks = this.textBlockRefs.map((blockRef) => blockRef.block!);
        this.result()!.feedbacks = this.textBlockRefs.map((blockRef) => blockRef.feedback).filter((feedback) => feedback != undefined);
    }

    /**
     * Start loading feedback suggestions from Athena
     * (only if this is a fresh submission, i.e. no assessments exist yet)
     */
    loadFeedbackSuggestions(): void {
        // Without a result there is nothing to attach a suggestion to. This happens for a correction round the tutor has
        // not started yet, where the submission is opened before a result exists.
        if (this.assessments.length > 0 || !this.result()) {
            return;
        }
        this.loadingFeedbackSuggestions.set(true);

        this.feedbackSuggestionsObservable = this.athenaService.getTextFeedbackSuggestions(this.exercise!, this.submission!).subscribe({
            next: (feedbackSuggestions) => {
                feedbackSuggestions.forEach((suggestion) => {
                    if (suggestion instanceof TextBlockRef) {
                        // referenced feedback suggestion - add to existing text blocks but avoid conflicts
                        this.addAutomaticTextBlockRef(suggestion);
                    } else {
                        // unreferenced feedback suggestion - we can just add it
                        this.result()!.feedbacks ??= [];
                        this.result()!.feedbacks = [...(this.result()!.feedbacks ?? []), suggestion];
                        // the unreferencedFeedback variable does not auto-update and needs to be updated manually
                        this.unreferencedFeedback.set([...this.unreferencedFeedback(), suggestion]);
                    }
                });
                this.validateFeedback();
                this.hasAutomaticFeedback.set(feedbackSuggestions.length > 0);
                this.loadingFeedbackSuggestions.set(false);
            },
            error: () => this.loadingFeedbackSuggestions.set(false),
        });
    }

    /**
     * Save the assessment
     */
    save(): void {
        this.saveBusy.set(true);
        this.assessmentsService.save(this.participation!.id!, this.result()!.id!, this.assessments, this.textBlocksWithFeedback, this.result()!.assessmentNote?.note).subscribe({
            next: (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.saveSuccessful'),
            error: (error: HttpErrorResponse) => this.handleError(error),
        });
    }

    /**
     * Submit the assessment
     */
    submit(): void {
        if (!this.result()?.id) {
            return; // We need to have saved the result before
        }

        if (!this.assessmentsAreValid()) {
            this.alertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        this.submitBusy.set(true);
        this.assessmentsService.submit(this.participation!.id!, this.result()!.id!, this.assessments, this.textBlocksWithFeedback, this.result()!.assessmentNote?.note).subscribe({
            next: (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.submitSuccessful'),
            error: (error: HttpErrorResponse) => this.handleError(error),
        });
    }

    protected override handleSaveOrSubmitSuccessWithAlert(response: HttpResponse<Result>, translationKey: string): void {
        super.handleSaveOrSubmitSuccessWithAlert(response, translationKey);
        this.result.set(response.body!);
        setSubmissionResultByCorrectionRound(this.submission!, this.result()!, this.correctionRound());
        this.saveBusy.set(false);
        this.submitBusy.set(false);
    }

    /**
     * Cancel the assessment
     */
    cancel(): void {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        this.cancelBusy.set(true);
        if (confirmCancel && this.exercise && this.submission) {
            this.assessmentsService.cancelAssessment(this.participation!.id!, this.submission.id!, this.result()?.id).subscribe(() => this.navigateBack());
        }
    }

    /**
     * Go to next submission
     */
    async nextSubmission(): Promise<void> {
        const url = getLinkToSubmissionAssessment(ExerciseType.TEXT, this.courseId, this.exerciseId, this.participation!.id, 'new', this.examId, this.exerciseGroupId);
        this.nextSubmissionBusy.set(true);
        // Merge rather than replace: a supplied queryParams object drops every other parameter, testRun among them.
        await this.router.navigate(url, { queryParams: { 'correction-round': this.correctionRound() }, queryParamsHandling: 'merge' });
    }

    /**
     * Sends the current (updated) assessment to the server to update the original assessment after a complaint was accepted.
     * The corresponding complaint response is sent along with the updated assessment to prevent additional requests.
     *
     * @param assessmentAfterComplaint the response to the complaint that is sent to the server along with the assessment update along with onSuccess and onError callbacks
     */
    updateAssessmentAfterComplaint(assessmentAfterComplaint: AssessmentAfterComplaint): void {
        this.validateFeedback();
        if (!this.assessmentsAreValid()) {
            this.alertService.error('artemisApp.textAssessment.error.invalidAssessments');
            assessmentAfterComplaint.onError();
            return;
        }

        const feedbacks = this.complaintService.getFeedbacksForUpdateAfterComplaint(this.assessments);
        const complaintResponse = this.complaintService.getComplaintResponseForUpdateAfterComplaint(assessmentAfterComplaint.complaintResponse);

        this.assessmentsService
            .updateAssessmentAfterComplaint(
                feedbacks,
                this.textBlocksWithFeedback,
                complaintResponse,
                this.submission?.id!, // eslint-disable-line @typescript-eslint/no-non-null-asserted-optional-chain
                this.participation?.id!, // eslint-disable-line @typescript-eslint/no-non-null-asserted-optional-chain
                this.result()?.assessmentNote?.note,
            )
            .subscribe({
                next: (response) => {
                    assessmentAfterComplaint.onSuccess();
                    this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.updateAfterComplaintSuccessful');
                },
                error: (httpErrorResponse: HttpErrorResponse) => {
                    assessmentAfterComplaint.onError();
                    this.alertService.closeAll();
                    const error = httpErrorResponse.error;
                    if (error && error.errorKey && error.errorKey === 'complaintLock') {
                        this.alertService.error(error.message, error.params);
                    } else {
                        this.alertService.error('artemisApp.textAssessment.updateAfterComplaintFailed');
                    }
                },
            });
    }

    navigateBack() {
        assessmentNavigateBack(this.location, this.router, this.exercise, this.submission, this.isTestRun());
    }

    /**
     * Validate the feedback of the assessment
     */
    validateFeedback(updatedFeedbacks?: Feedback[]): void {
        if (updatedFeedbacks) {
            this.unreferencedFeedback.set(updatedFeedbacks);
        }
        const hasReferencedFeedback = Feedback.haveCredits(this.referencedFeedback);
        const hasUnreferencedFeedback = Feedback.haveCreditsAndComments(this.unreferencedFeedback());
        // When unreferenced feedback is set, it has to be valid (score + detailed text)
        this.assessmentsAreValid.set((hasReferencedFeedback && this.unreferencedFeedback().length === 0) || hasUnreferencedFeedback);

        this.totalScore.set(this.computeTotalScore(this.assessments));
        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound(), this.submission!);
    }

    private prepareTextBlocksAndFeedbacks(): void {
        if (!this.result()) {
            return;
        }
        const feedbacks = this.result()?.feedbacks || [];
        this.unreferencedFeedback.set(feedbacks.filter((feedbackElement) => feedbackElement.reference == undefined && feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED));

        const matchBlocksWithFeedbacks = TextAssessmentService.matchBlocksWithFeedbacks(this.submission?.blocks || [], feedbacks);
        this.sortAndSetTextBlockRefs(matchBlocksWithFeedbacks, this.textBlockRefs, this.unusedTextBlockRefs, this.submission);
    }

    private getComplaint(): void {
        if (!this.submission) {
            return;
        }

        this.isLoading.set(true);
        this.complaintService.findBySubmissionId(this.submission.id!).subscribe({
            next: (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint.set(this.complaintService.convertComplaintFromServer(res.body, this.result()));
                this.isLoading.set(false);
            },
            error: (err: HttpErrorResponse) => {
                this.isLoading.set(false);
                this.handleError(err.error);
            },
        });
    }

    /**
     * Boolean which determines whether the user can override a result.
     * If no exercise is loaded, for example during loading between exercises, we return false.
     * Instructors can always override a result.
     * Tutors can override their own results within the assessment due date, if there is no complaint about their assessment.
     * They cannot override a result anymore, if there is a complaint. Another tutor must handle the complaint.
     */
    get canOverride(): boolean {
        if (this.exercise) {
            if (this.exercise.isAtLeastInstructor) {
                // Instructors can override any assessment at any time.
                return true;
            }
            if (this.complaint() && this.isAssessor()) {
                // If there is a complaint, the original assessor cannot override the result anymore.
                return false;
            }
            let isBeforeAssessmentDueDate = true;
            // Add check as the assessmentDueDate must not be set for exercises
            if (this.exercise.assessmentDueDate) {
                isBeforeAssessmentDueDate = dayjs().isBefore(this.exercise.assessmentDueDate);
            }
            // tutors are allowed to override one of their assessments before the assessment due date.
            return this.isAssessor() && isBeforeAssessmentDueDate;
        }
        return false;
    }

    get readOnly(): boolean {
        return !isAllowedToModifyFeedback(this.isTestRun(), this.isAssessor(), this.hasAssessmentDueDatePassed(), this.result(), this.complaint(), this.exercise);
    }

    protected override handleError(error: HttpErrorResponse): void {
        super.handleError(error);
        this.saveBusy.set(false);
        this.submitBusy.set(false);
    }

    /**
     * Invokes exampleSubmissionService when useAsExampleSubmission is emitted in assessment-layout
     */
    useStudentSubmissionAsExampleSubmission(): void {
        if (this.submission && this.exercise) {
            this.exampleSubmissionService.import(this.submission.id!, this.exercise.id!).subscribe({
                next: () => this.alertService.success('artemisApp.exampleSubmission.submitSuccessful'),
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
    }
}
