import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { initializedResultWithScore } from 'app/exercises/shared/result/result.utils';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import dayjs from 'dayjs/esm';
import { isProgrammingExerciseStudentParticipation, isResultPreliminary } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Participation, ParticipationType, getExercise } from 'app/entities/participation/participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { ResultDetailComponent } from 'app/exercises/shared/result/result-detail.component';
import { Result } from 'app/entities/result.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { captureException } from '@sentry/browser';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { faCircleNotch, faExclamationCircle, faFile } from '@fortawesome/free-solid-svg-icons';
import { faCheckCircle, faCircle, faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { isModelingOrTextOrFileUpload, isParticipationInDueTime, isProgrammingOrQuiz } from 'app/exercises/shared/participation/participation.utils';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Feedback } from 'app/entities/feedback.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';

/**
 * Enumeration object representing the possible options that
 * the status of the result's template can be in.
 */
export enum ResultTemplateStatus {
    /**
     * An automatic result is currently being generated and should be available soon.
     * This is currently only relevant for programming exercises.
     */
    IS_BUILDING = 'IS_BUILDING',
    /**
     * A regular, finished result is available.
     * Can be rated (counts toward the score) or not rated (after the deadline for practice).
     */
    HAS_RESULT = 'HAS_RESULT',
    /**
     * There is no result or submission status that could be shown, e.g. because the student just started with the exercise.
     */
    NO_RESULT = 'NO_RESULT',
    /**
     * Submitted and the student can still continue to submit.
     */
    SUBMITTED = 'SUBMITTED',
    /**
     * Submitted and the student can no longer submit, but a result is not yet available.
     */
    SUBMITTED_WAITING_FOR_GRADING = 'SUBMITTED_WAITING_FOR_GRADING',
    /**
     * The student started the exercise but submitted too late.
     * Feedback is not yet available, and a future result will not count toward the score.
     */
    LATE_NO_FEEDBACK = 'LATE_NO_FEEDBACK',
    /**
     * The student started the exercise and submitted too late, but feedback is available.
     */
    LATE = 'LATE',
    /**
     * No latest result available, e.g. because building took too long and the webapp did not receive it in time.
     * This is a distinct state because we want the student to know about this problematic state
     * and not confuse them by showing a previous result that does not match the latest submission.
     */
    MISSING = 'MISSING',
}

/**
 * Information about a missing result to communicate problems and give hints how to respond.
 */
export enum MissingResultInfo {
    NONE = 'NONE',
    FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE = 'FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE',
    FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE = 'FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE',
}

@Component({
    selector: 'jhi-result',
    templateUrl: './result.component.html',
    styles: ['span { display: inline-block; line-height: 1.25 }'],
})

/**
 * When using the result component make sure that the reference to the participation input is changed if the result changes
 * e.g. by using Object.assign to trigger ngOnChanges which makes sure that the result is updated
 */
export class ResultComponent implements OnInit, OnChanges {
    // make constants available to html
    readonly ResultTemplateStatus = ResultTemplateStatus;
    readonly MissingResultInfo = MissingResultInfo;
    readonly ParticipationType = ParticipationType;
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly getCourseFromExercise = getCourseFromExercise;

    @Input() participation: Participation;
    @Input() isBuilding: boolean;
    @Input() short = false;
    @Input() result?: Result;
    @Input() showUngradedResults = false;
    @Input() showBadge = false;
    @Input() showTestDetails = false;
    @Input() showIcon = true;
    @Input() missingResultInfo = MissingResultInfo.NONE;
    @Input() exercise?: Exercise;

    textColorClass: string;
    hasFeedback: boolean;
    resultIconClass: IconProp;
    resultString: string;
    templateStatus: ResultTemplateStatus;
    submission?: Submission;
    onlyShowSuccessfulCompileStatus: boolean;
    badgeClass: string;
    badgeText: string;
    badgeTooltip: string;
    resultTooltip?: string;

    latestIndividualDueDate?: dayjs.Dayjs;

    // Icons
    faCircleNotch = faCircleNotch;
    faFile = faFile;
    farCircle = faCircle;
    faExclamationCircle = faExclamationCircle;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private participationService: ParticipationService,
        private translate: TranslateService,
        private http: HttpClient,
        private modalService: NgbModal,
        private exerciseService: ExerciseService,
        private resultService: ResultService,
    ) {}

    /**
     * Executed on initialization. It retrieves the results of a given
     * participation and displays the corresponding message.
     */
    ngOnInit(): void {
        if (!this.result && this.participation) {
            this.exercise = this.exercise ?? getExercise(this.participation);
            this.participation.exercise = this.exercise;

            if (this.participation.results?.length) {
                if (this.exercise && this.exercise.type === ExerciseType.MODELING) {
                    // sort results by completionDate descending to ensure the newest result is shown
                    // this is important for modeling exercises since students can have multiple tries
                    // think about if this should be used for all types of exercises
                    this.participation.results.sort((r1: Result, r2: Result) => {
                        if (r1.completionDate! > r2.completionDate!) {
                            return -1;
                        }
                        if (r1.completionDate! < r2.completionDate!) {
                            return 1;
                        }
                        return 0;
                    });
                }
                // Make sure result and participation are connected
                this.result = this.participation.results[0];
                this.result.participation = this.participation;
            }
        } else if (!this.participation && this.result && this.result.participation) {
            // make sure this.participation is initialized in case it was not passed
            this.participation = this.result.participation;
            this.exercise = this.exercise ?? getExercise(this.participation);
            this.participation.exercise = this.exercise;
        } else if (this.participation) {
            this.exercise = this.exercise ?? getExercise(this.participation);
            this.participation.exercise = this.exercise;
        } else if (!this.result?.exampleResult) {
            // result of example submission does not have participation
            captureException(new Error('The result component did not get a participation or result as parameter and can therefore not display the score'));
            return;
        }

        // Note: it can still happen here that this.result is undefined, e.g. when this.participation.results.length == 0
        this.submission = this.result?.submission;

        if (this.result) {
            this.result.submission = this.result.submission ?? this.submission;
        }
        this.evaluate();

        this.translate.onLangChange.subscribe(() => {
            if (!!this.resultString) {
                this.resultString = this.resultService.getResultString(this.result, this.exercise, this.short);
            }
        });

        if (this.showBadge && this.result) {
            const badgeData = ResultService.evaluateBadge(this.participation, this.result);
            this.badgeClass = badgeData.badgeClass;
            this.badgeText = badgeData.text;
            this.badgeTooltip = badgeData.tooltip;
        }
    }

    /**
     * Executed when changes happen sets the corresponding template status to display a message.
     * @param changes The hashtable of the occurred changes as SimpleChanges object.
     */
    ngOnChanges(changes: SimpleChanges) {
        if (changes.participation || changes.result) {
            this.ngOnInit();
            // If it's building, we change the templateStatus to building regardless of any other settings.
        } else if (changes.missingResultInfo) {
            this.evaluate();
        } else if (changes.isBuilding && changes.isBuilding.currentValue) {
            this.templateStatus = ResultTemplateStatus.IS_BUILDING;
            // When the result was building and is not building anymore, we evaluate the result status.
        } else if (changes.isBuilding && changes.isBuilding.previousValue && !changes.isBuilding.currentValue) {
            this.evaluate();
        }
    }

    /**
     * Sets the corresponding icon, styling and message to display results.
     */
    evaluate() {
        this.templateStatus = this.evaluateTemplateStatus();

        if (this.templateStatus === ResultTemplateStatus.LATE) {
            this.textColorClass = this.getTextColorClass();
            this.resultIconClass = this.getResultIconClass();
            this.resultString = this.resultService.getResultString(this.result, this.exercise, this.short);
        } else if (this.result && this.result.score !== undefined && (this.result.rated || this.result.rated == undefined || this.showUngradedResults)) {
            this.onlyShowSuccessfulCompileStatus = this.getOnlyShowSuccessfulCompileStatus();
            this.textColorClass = this.getTextColorClass();
            this.hasFeedback = this.getHasFeedback();
            this.resultIconClass = this.getResultIconClass();
            this.resultString = this.resultService.getResultString(this.result, this.exercise, this.short);
            this.resultTooltip = this.buildResultTooltip();
        } else if (this.templateStatus !== ResultTemplateStatus.MISSING) {
            // make sure that we do not display results that are 'rated=false' or that do not have a score
            // this state is only possible if no rated results are available at all, so we show the info that no graded result is available
            this.templateStatus = ResultTemplateStatus.NO_RESULT;
            this.result = undefined;
            this.resultString = '';
        }
    }

    private evaluateTemplateStatus() {
        // Fallback if participation is not set
        if (!this.participation || !this.exercise) {
            if (!this.result) {
                return ResultTemplateStatus.NO_RESULT;
            } else {
                return ResultTemplateStatus.HAS_RESULT;
            }
        }

        // If there is a problem, it has priority, and we show that instead
        if (this.missingResultInfo !== MissingResultInfo.NONE) {
            return ResultTemplateStatus.MISSING;
        }

        // Evaluate status for modeling, text and file-upload exercises
        if (isModelingOrTextOrFileUpload(this.participation)) {
            // Based on its submission we test if the participation is in due time of the given exercise.

            const inDueTime = isParticipationInDueTime(this.participation, this.exercise);
            const dueDate = ResultComponent.dateAsDayjs(getExerciseDueDate(this.exercise, this.participation));
            const assessmentDueDate = ResultComponent.dateAsDayjs(this.exercise.assessmentDueDate);

            if (inDueTime && initializedResultWithScore(this.result)) {
                // Submission is in due time of exercise and has a result with score
                if (!assessmentDueDate || assessmentDueDate.isBefore(dayjs())) {
                    // the assessment due date has passed (or there was none)
                    return ResultTemplateStatus.HAS_RESULT;
                } else {
                    // the assessment period is still active
                    return ResultTemplateStatus.SUBMITTED_WAITING_FOR_GRADING;
                }
            } else if (inDueTime && !initializedResultWithScore(this.result)) {
                // Submission is in due time of exercise and doesn't have a result with score.
                if (!dueDate || dueDate.isSameOrAfter(dayjs())) {
                    // the due date is in the future (or there is none) => the exercise is still ongoing
                    return ResultTemplateStatus.SUBMITTED;
                } else if (!assessmentDueDate || assessmentDueDate.isSameOrAfter(dayjs())) {
                    // the due date is over, further submissions are no longer possible, waiting for grading
                    return ResultTemplateStatus.SUBMITTED_WAITING_FOR_GRADING;
                } else {
                    // the due date is over, further submissions are no longer possible, no result after assessment due date
                    // TODO why is this distinct from the case above? The submission can still be graded and often is.
                    return ResultTemplateStatus.NO_RESULT;
                }
            } else if (initializedResultWithScore(this.result) && (!assessmentDueDate || assessmentDueDate.isBefore(dayjs()))) {
                // Submission is not in due time of exercise, has a result with score and there is no assessmentDueDate for the exercise or it lies in the past.
                // TODO handle external submissions with new status "External"
                return ResultTemplateStatus.LATE;
            } else {
                // Submission is not in due time of exercise and there is actually no feedback for the submission or the feedback should not be displayed yet.
                return ResultTemplateStatus.LATE_NO_FEEDBACK;
            }
        }

        // Evaluate status for programming and quiz exercises
        if (isProgrammingOrQuiz(this.participation)) {
            if (this.isBuilding) {
                return ResultTemplateStatus.IS_BUILDING;
            } else if (initializedResultWithScore(this.result)) {
                return ResultTemplateStatus.HAS_RESULT;
            } else {
                return ResultTemplateStatus.NO_RESULT;
            }
        }

        return ResultTemplateStatus.NO_RESULT;
    }

    private static dateAsDayjs(date: any) {
        if (date == undefined) {
            return undefined;
        }
        return dayjs.isDayjs(date) ? date : dayjs(date);
    }

    /**
     * Gets the tooltip text that should be displayed next to the result string. Not required.
     */
    buildResultTooltip(): string | undefined {
        // Only show the 'preliminary' tooltip for programming student participation results and if the buildAndTestAfterDueDate has not passed.
        const programmingExercise = this.exercise as ProgrammingExercise;
        if (
            this.participation &&
            isProgrammingExerciseStudentParticipation(this.participation) &&
            !(this.participation as ProgrammingExerciseStudentParticipation).testRun &&
            isResultPreliminary(this.result!, programmingExercise)
        ) {
            if (programmingExercise?.assessmentType !== AssessmentType.AUTOMATIC) {
                return 'artemisApp.result.preliminaryTooltipSemiAutomatic';
            }
            return 'artemisApp.result.preliminaryTooltip';
        }
    }

    /**
     * Checks if there is feedback or not for a build result.
     */
    getHasFeedback(): boolean {
        if (this.submission && this.submission.submissionExerciseType === SubmissionExerciseType.PROGRAMMING && (this.submission as ProgrammingSubmission).buildFailed) {
            return true;
        } else if (this.result?.hasFeedback === undefined) {
            return false;
        }
        return this.result.hasFeedback;
    }

    /**
     * Show details of a result.
     * @param result Result object whose details will be displayed.
     */
    showDetails(result: Result) {
        if (!result.participation) {
            result.participation = this.participation;
        }

        const modalRef = this.modalService.open(ResultDetailComponent, { keyboard: true, size: 'xl' });
        const componentInstance: ResultDetailComponent = modalRef.componentInstance;
        componentInstance.exercise = this.exercise;
        componentInstance.result = result;
        componentInstance.showTestDetails =
            (this.exercise?.type === ExerciseType.PROGRAMMING && (this.exercise as ProgrammingExercise).showTestNamesToStudents) || this.showTestDetails;
        if (this.exercise) {
            componentInstance.exerciseType = this.exercise.type!;
            componentInstance.showScoreChart = true;
        }
        if (this.templateStatus === ResultTemplateStatus.MISSING) {
            componentInstance.messageKey = 'artemisApp.result.notLatestSubmission';
        }

        if (
            this.result?.assessmentType === AssessmentType.AUTOMATIC &&
            this.exercise?.type === ExerciseType.PROGRAMMING &&
            hasExerciseDueDatePassed(this.exercise, this.participation)
        ) {
            this.determineShowMissingAutomaticFeedbackInformation(componentInstance);
        }
    }

    /**
     * Checks whether a build artifact exists for a submission.
     */
    hasBuildArtifact() {
        if (this.result && this.submission && this.submission.submissionExerciseType === SubmissionExerciseType.PROGRAMMING) {
            const submission = this.submission as ProgrammingSubmission;
            return submission.buildArtifact;
        }
        return false;
    }

    /**
     * Download the build results of a specific participation.
     * @param participationId The identifier of the participation.
     */
    downloadBuildResult(participationId?: number) {
        if (participationId) {
            this.participationService.downloadArtifact(participationId).subscribe((artifact) => {
                const fileURL = URL.createObjectURL(artifact.fileContent);
                const link = document.createElement('a');
                link.href = fileURL;
                link.target = '_blank';
                link.download = artifact.fileName;
                document.body.appendChild(link);
                link.click();
            });
        }
    }

    /**
     * Checks if only compilation was tested. This is the case, when a successful result is present with 0 of 0 passed tests
     *
     */
    getOnlyShowSuccessfulCompileStatus(): boolean {
        const zeroTestsPassed = (this.result?.feedbacks?.filter((feedback) => Feedback.isStaticCodeAnalysisFeedback(feedback)).length || 0) === 0;
        return (
            this.templateStatus !== ResultTemplateStatus.NO_RESULT &&
            this.templateStatus !== ResultTemplateStatus.IS_BUILDING &&
            !this.isBuildFailed(this.submission) &&
            zeroTestsPassed
        );
    }

    /**
     * Get the css class for the entire text as a string
     *
     * @return {string} the css class
     */
    getTextColorClass() {
        if (this.templateStatus === ResultTemplateStatus.LATE) {
            return 'result--late';
        }

        const result = this.result!;

        // Build failure so return red text.
        if (this.isBuildFailedAndResultIsAutomatic(result)) {
            return 'text-danger';
        }

        if (this.resultIsPreliminary(result)) {
            return 'text-secondary';
        }

        if (result.score == undefined) {
            return result.successful ? 'text-success' : 'text-danger';
        }

        if (result.score >= MIN_SCORE_GREEN) {
            return 'text-success';
        }

        if (result.score >= MIN_SCORE_ORANGE) {
            return 'result-orange';
        }

        return 'text-danger';
    }

    /**
     * Get the icon type for the result icon as an array
     *
     */
    getResultIconClass(): IconProp {
        const result = this.result!;

        // Build failure so return times icon.
        if (this.isBuildFailedAndResultIsAutomatic(result)) {
            return faTimesCircle;
        }

        if (this.resultIsPreliminary(result)) {
            return faQuestionCircle;
        }

        if (this.onlyShowSuccessfulCompileStatus) {
            return faCheckCircle;
        }

        if (result.score == undefined) {
            if (result.successful) {
                return faCheckCircle;
            }
            return faTimesCircle;
        }
        if (result.score >= MIN_SCORE_GREEN) {
            return faCheckCircle;
        }
        return faTimesCircle;
    }

    /**
     * Returns true if the specified result is preliminary.
     * @param result the result. It must include a participation and exercise.
     */
    resultIsPreliminary(result: Result) {
        return (
            result.participation &&
            isProgrammingExerciseStudentParticipation(result.participation) &&
            isResultPreliminary(result, result.participation.exercise as ProgrammingExercise)
        );
    }

    /**
     * Returns true if the submission of the result is of type programming, is automatic, and
     * its build has failed.
     * @param result
     */
    isBuildFailedAndResultIsAutomatic(result: Result) {
        return this.isBuildFailed(result.submission) && !this.isManualResult(result);
    }

    /**
     * Returns true if the specified submission is a programming submissions that has a failed
     * build.
     * @param submission the submission
     */
    isBuildFailed(submission?: Submission) {
        const isProgrammingSubmission = submission && submission.submissionExerciseType === SubmissionExerciseType.PROGRAMMING;
        return isProgrammingSubmission && (submission as ProgrammingSubmission).buildFailed;
    }

    /**
     * Returns true if the specified result is not automatic.
     * @param result the result.
     */
    isManualResult(result?: Result) {
        return result?.assessmentType !== AssessmentType.AUTOMATIC;
    }

    /**
     * Determines if some information about testcases could still be hidden because of later individual due dates
     * @param componentInstance the detailed result view
     */
    private determineShowMissingAutomaticFeedbackInformation(componentInstance: ResultDetailComponent) {
        if (!this.latestIndividualDueDate) {
            this.exerciseService.getLatestDueDate(this.exercise!.id!).subscribe((latestIndividualDueDate?: dayjs.Dayjs) => {
                this.latestIndividualDueDate = latestIndividualDueDate;
                this.initializeMissingAutomaticFeedbackAndLatestIndividualDueDate(componentInstance);
            });
        } else {
            this.initializeMissingAutomaticFeedbackAndLatestIndividualDueDate(componentInstance);
        }
    }

    private initializeMissingAutomaticFeedbackAndLatestIndividualDueDate(componentInstance: ResultDetailComponent) {
        componentInstance.showMissingAutomaticFeedbackInformation = dayjs().isBefore(this.latestIndividualDueDate);
        componentInstance.latestIndividualDueDate = this.latestIndividualDueDate;
    }
}
