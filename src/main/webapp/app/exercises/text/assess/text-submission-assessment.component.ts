import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Location } from '@angular/common';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextSubmission } from 'app/entities/text/text-submission.model';
import { TextExercise } from 'app/entities/text/text-exercise.model';
import { Result } from 'app/entities/result.model';
import { Complaint } from 'app/entities/complaint.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { notUndefined, onError } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { NEW_ASSESSMENT_PATH } from 'app/exercises/text/assess/text-submission-assessment.route';
import { assessmentNavigateBack } from 'app/exercises/shared/navigate-back.util';
import {
    getLatestSubmissionResult,
    getSubmissionResultByCorrectionRound,
    getSubmissionResultById,
    setLatestSubmissionResult,
    setSubmissionResultByCorrectionRound,
} from 'app/entities/submission.model';
import { TextAssessmentBaseComponent } from 'app/exercises/text/assess/text-assessment-base.component';
import { getExerciseDashboardLink, getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { Course } from 'app/entities/course.model';
import { isAllowedToModifyFeedback } from 'app/assessment/assessment.service';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { AssessmentAfterComplaint } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { TextBlockRef } from 'app/entities/text/text-block-ref.model';
import { AthenaService } from 'app/assessment/athena.service';
import { TextBlock } from 'app/entities/text/text-block.model';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-text-submission-assessment',
    templateUrl: './text-submission-assessment.component.html',
    styleUrls: ['./text-submission-assessment.component.scss'],
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

    /*
     * The instance of this component is REUSED for multiple assessments if using the "Assess Next" button!
     * All properties must be initialized with a default value (or null) in the resetComponent() method.
     * For traceability: Keep order in resetComponent() consistent with declaration.
     */

    participation?: StudentParticipation;
    result?: Result;
    unreferencedFeedback: Feedback[];
    complaint?: Complaint;
    totalScore: number;
    isTestRun = false;
    isLoading: boolean;
    saveBusy: boolean;
    submitBusy: boolean;
    cancelBusy: boolean;
    nextSubmissionBusy: boolean;
    isAssessor: boolean;
    assessmentsAreValid: boolean;
    noNewSubmissions: boolean;
    hasAssessmentDueDatePassed: boolean;
    correctionRound: number;
    resultId: number;
    loadingInitialSubmission = true;
    highlightDifferences = false;

    /*
     * Non-reset properties:
     * These properties are not reset on purpose, as they cannot change between assessments.
     */
    private cancelConfirmationText: string;

    // ExerciseId is updated from Route Subscription directly.
    exerciseId: number;
    courseId: number;
    course?: Course;
    examId = 0;
    exerciseGroupId: number;
    exerciseDashboardLink: string[];
    isExamMode = false;

    private feedbackSuggestionsObservable?: Subscription;

    private get referencedFeedback(): Feedback[] {
        return this.textBlockRefs.map(({ feedback }) => feedback).filter(notUndefined) as Feedback[];
    }

    private get assessments(): Feedback[] {
        return [...this.referencedFeedback, ...this.unreferencedFeedback];
    }

    // Icons
    farListAlt = faListAlt;

    constructor() {
        super();
        this.translateService.get('artemisApp.textAssessment.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
        this.correctionRound = 0;
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
        this.result = undefined;
        this.unreferencedFeedback = [];
        this.textBlockRefs = [];
        this.unusedTextBlockRefs = [];
        this.complaint = undefined;
        this.totalScore = 0;

        this.isLoading = true;
        this.saveBusy = false;
        this.submitBusy = false;
        this.cancelBusy = false;
        this.nextSubmissionBusy = false;
        this.isAssessor = false;
        this.assessmentsAreValid = false;
        this.noNewSubmissions = false;
        this.highlightDifferences = false;
    }

    /**
     * Life cycle hook to indicate component creation is done
     */
    async ngOnInit(): Promise<void> {
        await super.ngOnInit();
        this.route.queryParamMap.subscribe((queryParams) => {
            this.isTestRun = queryParams.get('testRun') === 'true';
            this.correctionRound = Number(queryParams.get('correction-round'));
        });

        this.activatedRoute.paramMap.subscribe((paramMap) => {
            this.exerciseId = Number(paramMap.get('exerciseId'));
            this.resultId = Number(paramMap.get('resultId')) ?? 0;
            this.courseId = Number(paramMap.get('courseId'));
            if (paramMap.has('examId')) {
                this.examId = Number(paramMap.get('examId'));
                this.exerciseGroupId = Number(paramMap.get('exerciseGroupId'));
                this.isExamMode = true;
            }
            this.exerciseDashboardLink = getExerciseDashboardLink(this.courseId, this.exerciseId, this.examId, this.isTestRun);
        });
        this.activatedRoute.data.subscribe(({ studentParticipation }) => {
            this.setPropertiesFromServerResponse(studentParticipation);
            this.validateFeedback();
        });
    }

    ngOnDestroy(): void {
        this.feedbackSuggestionsObservable?.unsubscribe();
    }

    private setPropertiesFromServerResponse(studentParticipation?: StudentParticipation) {
        this.resetComponent();
        this.loadingInitialSubmission = false;
        if (!studentParticipation) {
            // Show "No New Submission" banner on .../submissions/new/assessment route
            this.noNewSubmissions = this.isNewAssessmentRoute;
            return;
        }

        this.participation = studentParticipation;
        this.submission = this.participation?.submissions?.last() as TextSubmission;
        this.exercise = this.participation?.exercise as TextExercise;
        this.course = getCourseFromExercise(this.exercise);
        setLatestSubmissionResult(this.submission, getLatestSubmissionResult(this.submission));

        if (this.resultId > 0) {
            this.result = getSubmissionResultById(this.submission, this.resultId);
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            this.correctionRound = this.submission.results?.findIndex((result) => result.id === this.resultId)!;
        } else {
            this.result = getSubmissionResultByCorrectionRound(this.submission, this.correctionRound);
        }

        this.hasAssessmentDueDatePassed = !!this.exercise!.assessmentDueDate && dayjs(this.exercise!.assessmentDueDate).isBefore(dayjs());

        this.prepareTextBlocksAndFeedbacks();
        this.getComplaint();
        this.updateUrlIfNeeded();

        this.checkPermissions(this.result);
        this.totalScore = this.computeTotalScore(this.assessments);
        this.isLoading = false;

        this.loadFeedbackSuggestions();

        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound, this.submission);
    }

    private updateUrlIfNeeded() {
        if (this.isNewAssessmentRoute) {
            // Update the url with the new id, without reloading the page, to make the history consistent
            const newUrl = this.router
                .createUrlTree(
                    getLinkToSubmissionAssessment(
                        ExerciseType.TEXT,
                        this.courseId,
                        this.exerciseId,
                        this.participation!.id!,
                        this.submission!.id!,
                        this.examId,
                        this.exerciseGroupId,
                    ),
                )
                .toString();
            this.location.go(newUrl);
        }
    }

    private get isNewAssessmentRoute(): boolean {
        return this.activatedRoute.routeConfig?.path === NEW_ASSESSMENT_PATH;
    }

    private checkPermissions(result?: Result): void {
        this.isAssessor = result?.assessor?.id === this.userId;
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
                }
            }
        }

        // Add the text block to add
        newTextBlockRefs.push(refToAdd);

        // Sort the new text block refs by their start index
        newTextBlockRefs.sort((ref1, ref2) => ref1.block!.startIndex! - ref2.block!.startIndex!);

        // Update the text on all text block refs
        for (const blockRef of newTextBlockRefs) {
            blockRef.block!.text = this.submission!.text!.substring(blockRef.block!.startIndex!, blockRef.block!.endIndex!);
        }

        this.textBlockRefs = newTextBlockRefs;
        this.submission!.blocks = this.textBlockRefs.map((blockRef) => blockRef.block!);
        this.result!.feedbacks = this.textBlockRefs.map((blockRef) => blockRef.feedback).filter((feedback) => feedback != undefined) as Feedback[];
    }

    /**
     * Start loading feedback suggestions from Athena
     * (only if this is a fresh submission, i.e. no assessments exist yet)
     */
    loadFeedbackSuggestions(): void {
        if (this.assessments.length > 0) {
            return;
        }
        this.feedbackSuggestionsObservable = this.athenaService.getTextFeedbackSuggestions(this.exercise!, this.submission!).subscribe((feedbackSuggestions) => {
            feedbackSuggestions.forEach((suggestion) => {
                if (suggestion instanceof TextBlockRef) {
                    // referenced feedback suggestion - add to existing text blocks but avoid conflicts
                    this.addAutomaticTextBlockRef(suggestion);
                } else {
                    // unreferenced feedback suggestion - we can just add it
                    this.result!.feedbacks ??= [];
                    this.result!.feedbacks = [...this.result!.feedbacks, suggestion];
                    // the unreferencedFeedback variable does not auto-update and needs to be updated manually
                    this.unreferencedFeedback = [...this.unreferencedFeedback, suggestion];
                }
            });
            this.validateFeedback();
        });
    }

    /**
     * Save the assessment
     */
    save(): void {
        this.saveBusy = true;
        this.assessmentsService.save(this.participation!.id!, this.result!.id!, this.assessments, this.textBlocksWithFeedback, this.result!.assessmentNote?.note).subscribe({
            next: (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.saveSuccessful'),
            error: (error: HttpErrorResponse) => this.handleError(error),
        });
    }

    /**
     * Submit the assessment
     */
    submit(): void {
        if (!this.result?.id) {
            return; // We need to have saved the result before
        }

        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        this.submitBusy = true;
        this.assessmentsService.submit(this.participation!.id!, this.result!.id!, this.assessments, this.textBlocksWithFeedback, this.result!.assessmentNote?.note).subscribe({
            next: (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.submitSuccessful'),
            error: (error: HttpErrorResponse) => this.handleError(error),
        });
    }

    protected handleSaveOrSubmitSuccessWithAlert(response: HttpResponse<Result>, translationKey: string): void {
        super.handleSaveOrSubmitSuccessWithAlert(response, translationKey);
        this.result = response.body!;
        setSubmissionResultByCorrectionRound(this.submission!, this.result, this.correctionRound);
        this.saveBusy = this.submitBusy = false;
    }

    /**
     * Cancel the assessment
     */
    cancel(): void {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        this.cancelBusy = true;
        if (confirmCancel && this.exercise && this.submission) {
            this.assessmentsService.cancelAssessment(this.participation!.id!, this.submission!.id!).subscribe(() => this.navigateBack());
        }
    }

    /**
     * Go to next submission
     */
    async nextSubmission(): Promise<void> {
        const url = getLinkToSubmissionAssessment(ExerciseType.TEXT, this.courseId, this.exerciseId, this.participation!.id!, 'new', this.examId, this.exerciseGroupId);
        this.nextSubmissionBusy = true;
        await this.router.navigate(url, { queryParams: { 'correction-round': this.correctionRound } });
    }

    /**
     * Sends the current (updated) assessment to the server to update the original assessment after a complaint was accepted.
     * The corresponding complaint response is sent along with the updated assessment to prevent additional requests.
     *
     * @param assessmentAfterComplaint the response to the complaint that is sent to the server along with the assessment update along with onSuccess and onError callbacks
     */
    updateAssessmentAfterComplaint(assessmentAfterComplaint: AssessmentAfterComplaint): void {
        this.validateFeedback();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.textAssessment.error.invalidAssessments');
            assessmentAfterComplaint.onError();
            return;
        }

        this.assessmentsService
            .updateAssessmentAfterComplaint(
                this.assessments,
                this.textBlocksWithFeedback,
                assessmentAfterComplaint.complaintResponse,
                this.submission?.id!, // eslint-disable-line @typescript-eslint/no-non-null-asserted-optional-chain
                this.participation?.id!, // eslint-disable-line @typescript-eslint/no-non-null-asserted-optional-chain
                this.result?.assessmentNote?.note,
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
        assessmentNavigateBack(this.location, this.router, this.exercise!, this.submission!, this.isTestRun);
    }

    /**
     * Validate the feedback of the assessment
     */
    validateFeedback(): void {
        const hasReferencedFeedback = Feedback.haveCredits(this.referencedFeedback);
        const hasUnreferencedFeedback = Feedback.haveCreditsAndComments(this.unreferencedFeedback);
        // When unreferenced feedback is set, it has to be valid (score + detailed text)
        this.assessmentsAreValid = (hasReferencedFeedback && this.unreferencedFeedback.length === 0) || hasUnreferencedFeedback;

        this.totalScore = this.computeTotalScore(this.assessments);
        this.submissionService.handleFeedbackCorrectionRoundTag(this.correctionRound, this.submission!);
    }

    private prepareTextBlocksAndFeedbacks(): void {
        if (!this.result) {
            return;
        }
        const feedbacks = this.result.feedbacks || [];
        this.unreferencedFeedback = feedbacks.filter((feedbackElement) => feedbackElement.reference == undefined && feedbackElement.type === FeedbackType.MANUAL_UNREFERENCED);

        const matchBlocksWithFeedbacks = TextAssessmentService.matchBlocksWithFeedbacks(this.submission?.blocks || [], feedbacks);
        this.sortAndSetTextBlockRefs(matchBlocksWithFeedbacks, this.textBlockRefs, this.unusedTextBlockRefs, this.submission);
    }

    private getComplaint(): void {
        if (!this.submission) {
            return;
        }

        this.isLoading = true;
        this.complaintService.findBySubmissionId(this.submission.id!).subscribe({
            next: (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint = res.body;
                this.isLoading = false;
            },
            error: (err: HttpErrorResponse) => {
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
            if (this.complaint && this.isAssessor) {
                // If there is a complaint, the original assessor cannot override the result anymore.
                return false;
            }
            let isBeforeAssessmentDueDate = true;
            // Add check as the assessmentDueDate must not be set for exercises
            if (this.exercise.assessmentDueDate) {
                isBeforeAssessmentDueDate = dayjs().isBefore(this.exercise.assessmentDueDate!);
            }
            // tutors are allowed to override one of their assessments before the assessment due date.
            return this.isAssessor && isBeforeAssessmentDueDate;
        }
        return false;
    }

    get readOnly(): boolean {
        return !isAllowedToModifyFeedback(this.isTestRun, this.isAssessor, this.hasAssessmentDueDatePassed, this.result, this.complaint, this.exercise);
    }

    protected handleError(error: HttpErrorResponse): void {
        super.handleError(error);
        this.saveBusy = this.submitBusy = false;
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
