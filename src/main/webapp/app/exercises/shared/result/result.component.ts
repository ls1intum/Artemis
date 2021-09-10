import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { initializedResultWithScore } from 'app/exercises/shared/result/result-utils';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import dayjs from 'dayjs';
import { isProgrammingExerciseStudentParticipation, isResultPreliminary } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { getExercise, Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { isModelingOrTextOrFileUpload, isParticipationInDueTime, isProgrammingOrQuiz } from 'app/overview/participation-utils';
import { ExerciseType } from 'app/entities/exercise.model';
import { ResultDetailComponent } from 'app/exercises/shared/result/result-detail.component';
import { Result } from 'app/entities/result.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { round } from 'app/shared/util/utils';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

/**
 * Enumeration object representing the possible options that
 * the status of the result's template can be in.
 */
enum ResultTemplateStatus {
    IS_BUILDING = 'IS_BUILDING',
    HAS_RESULT = 'HAS_RESULT',
    NO_RESULT = 'NO_RESULT',
    SUBMITTED = 'SUBMITTED', // submitted and can still continue to submit
    SUBMITTED_WAITING_FOR_GRADING = 'SUBMITTED_WAITING_FOR_GRADING', // submitted and can no longer submit, not yet graded
    LATE_NO_FEEDBACK = 'LATE_NO_FEEDBACK', // started, submitted too late, not graded
    LATE = 'LATE', // submitted too late, graded
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
    // make constants available to html for comparison
    readonly ResultTemplateStatus = ResultTemplateStatus;
    readonly round = round;

    @Input() participation: Participation;
    @Input() isBuilding: boolean;
    @Input() short = false;
    @Input() result?: Result;
    @Input() showUngradedResults: boolean;
    @Input() showGradedBadge = false;
    @Input() showTestDetails = false;

    ParticipationType = ParticipationType;
    textColorClass: string;
    hasFeedback: boolean;
    resultIconClass: IconProp;
    resultString: string;
    templateStatus: ResultTemplateStatus;
    submission?: Submission;
    onlyShowSuccessfulCompileStatus: boolean;

    resultTooltip: string;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private participationService: ParticipationService,
        private translate: TranslateService,
        private http: HttpClient,
        private modalService: NgbModal,
    ) {}

    /**
     * Executed on initialization. It retrieves the results of a given
     * participation and displays the corresponding message.
     */
    ngOnInit(): void {
        if (!this.result && this.participation && this.participation.id) {
            const exercise = getExercise(this.participation);

            if (this.participation.results && this.participation.results.length > 0) {
                if (exercise && exercise.type === ExerciseType.MODELING) {
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
                if (!this.result) {
                    this.result = this.participation.results[0];
                }
                this.result.participation = this.participation;
            }
        }
        // make sure this.participation is initialized in case it was not passed
        if (!this.participation && this.result && this.result.participation) {
            this.participation = this.result.participation;
        }
        if (this.result) {
            this.submission = this.result.submission;
        }
        this.evaluate();
    }

    /**
     * Executed when changes happen sets the corresponding template status to display a message.
     * @param changes The hashtable of the occurred changes as SimpleChanges object.
     */
    ngOnChanges(changes: SimpleChanges) {
        if (changes.participation || changes.result) {
            this.ngOnInit();
            // If is building, we change the templateStatus to building regardless of any other settings.
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
        } else if (this.result && (this.result.score || this.result.score === 0) && (this.result.rated || this.result.rated == undefined || this.showUngradedResults)) {
            this.onlyShowSuccessfulCompileStatus = this.getOnlyShowSuccessfulCompileStatus();
            this.textColorClass = this.getTextColorClass();
            this.hasFeedback = this.getHasFeedback();
            this.resultIconClass = this.getResultIconClass();
            this.resultString = this.buildResultString();
            this.resultTooltip = this.buildResultTooltip();
        } else {
            // make sure that we do not display results that are 'rated=false' or that do not have a score
            // this state is only possible if no rated results are available at all, so we show the info that no graded result is available
            this.templateStatus = ResultTemplateStatus.NO_RESULT;
            this.result = undefined;
        }
    }

    private evaluateTemplateStatus() {
        // Fallback if participation is not set
        const exercise = getExercise(this.participation);
        if (!this.participation || !exercise) {
            if (!this.result) {
                return ResultTemplateStatus.NO_RESULT;
            } else {
                return ResultTemplateStatus.HAS_RESULT;
            }
        }

        // Evaluate status for modeling, text and file-upload exercises
        if (isModelingOrTextOrFileUpload(this.participation)) {
            // Based on its submission we test if the participation is in due time of the given exercise.

            const inDueTime = isParticipationInDueTime(this.participation, exercise);
            const dueDate = ResultComponent.dateAsDayjs(exercise.dueDate);
            const assessmentDueDate = ResultComponent.dateAsDayjs(exercise.assessmentDueDate);

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
     * Gets the build result string.
     */
    buildResultString() {
        if (this.isBuildFailed(this.submission)) {
            return this.isManualResult(this.result) ? this.result!.resultString : this.translate.instant('artemisApp.editor.buildFailed');
            // Only show the 'preliminary' string for programming student participation results and if the buildAndTestAfterDueDate has not passed.
        }

        const buildSuccessful = this.translate.instant('artemisApp.editor.buildSuccessful');
        const resultStringCompiledMessage = this.result!.resultString?.replace('0 of 0 passed', buildSuccessful) ?? buildSuccessful;

        if (
            this.participation &&
            isProgrammingExerciseStudentParticipation(this.participation) &&
            isResultPreliminary(this.result!, getExercise(this.participation) as ProgrammingExercise)
        ) {
            const preliminary = '(' + this.translate.instant('artemisApp.result.preliminary') + ')';
            return `${resultStringCompiledMessage} ${preliminary}`;
        } else {
            return resultStringCompiledMessage;
        }
    }

    /**
     * Only show the 'preliminary' tooltip for programming student participation results and if the buildAndTestAfterDueDate has not passed.
     */
    buildResultTooltip() {
        const programmingExercise = getExercise(this.participation) as ProgrammingExercise;
        if (this.participation && isProgrammingExerciseStudentParticipation(this.participation) && isResultPreliminary(this.result!, programmingExercise)) {
            if (programmingExercise?.assessmentType !== AssessmentType.AUTOMATIC) {
                return this.translate.instant('artemisApp.result.preliminaryTooltipSemiAutomatic');
            }
            return this.translate.instant('artemisApp.result.preliminaryTooltip');
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
        componentInstance.result = result;
        const exercise = getExercise(this.participation);
        componentInstance.showTestDetails = (exercise?.type === ExerciseType.PROGRAMMING && (exercise as ProgrammingExercise).showTestNamesToStudents) || this.showTestDetails;
        if (exercise) {
            componentInstance.exerciseType = exercise.type!;
            componentInstance.showScoreChart = true;
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
        const zeroTestsPassed = this.result?.resultString?.includes('0 of 0 passed') ?? false;
        return this.templateStatus !== ResultTemplateStatus.NO_RESULT && this.templateStatus !== ResultTemplateStatus.IS_BUILDING && zeroTestsPassed;
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

        if (result.score > MIN_SCORE_GREEN) {
            return 'text-success';
        }

        if (result.score > MIN_SCORE_ORANGE) {
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
            return ['far', 'times-circle'];
        }

        if (this.resultIsPreliminary(result)) {
            return ['far', 'question-circle'];
        }

        if (this.onlyShowSuccessfulCompileStatus) {
            return ['far', 'check-circle'];
        }

        if (result.score == undefined) {
            if (result.successful) {
                return ['far', 'check-circle'];
            }
            return ['far', 'times-circle'];
        }
        if (result.score > 80) {
            return ['far', 'check-circle'];
        }
        return ['far', 'times-circle'];
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
    isBuildFailed(submission: Submission | undefined) {
        const isProgrammingSubmission = submission && submission.submissionExerciseType === SubmissionExerciseType.PROGRAMMING;
        return isProgrammingSubmission && (submission as ProgrammingSubmission).buildFailed;
    }

    /**
     * Returns true if the specified result is not automatic.
     * @param result the result.
     */
    isManualResult(result: Result | undefined) {
        return result?.assessmentType !== AssessmentType.AUTOMATIC;
    }
}
