import { Component, Input, OnChanges, OnDestroy, OnInit, Optional, SimpleChanges } from '@angular/core';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import {
    MissingResultInformation,
    ResultTemplateStatus,
    evaluateTemplateStatus,
    getResultIconClass,
    getTextColorClass,
    isAthenaAIResult,
} from 'app/exercises/shared/result/result.utils';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import dayjs from 'dayjs/esm';
import { isProgrammingExerciseStudentParticipation, isResultPreliminary } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { Participation, ParticipationType, getExercise } from 'app/entities/participation/participation.model';
import { ProgrammingSubmission } from 'app/entities/programming/programming-submission.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';
import { Result } from 'app/entities/result.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { captureException } from '@sentry/angular';
import { faCircleNotch, faExclamationCircle, faExclamationTriangle, faFile } from '@fortawesome/free-solid-svg-icons';
import { faCircle } from '@fortawesome/free-regular-svg-icons';
import { Badge, ResultService } from 'app/exercises/shared/result/result.service';
import { ExerciseCacheService } from 'app/exercises/shared/exercise/exercise-cache.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { isPracticeMode } from 'app/entities/participation/student-participation.model';
import { prepareFeedbackComponentParameters } from 'app/exercises/shared/feedback/feedback.utils';
import { CsvDownloadService } from 'app/shared/util/CsvDownloadService';

@Component({
    selector: 'jhi-result',
    templateUrl: './result.component.html',
    styleUrls: ['./result.component.scss'],
})

/**
 * When using the result component make sure that the reference to the participation input is changed if the result changes
 * e.g. by using Object.assign to trigger ngOnChanges which makes sure that the result is updated
 */
export class ResultComponent implements OnInit, OnChanges, OnDestroy {
    // make constants available to html
    readonly ResultTemplateStatus = ResultTemplateStatus;
    readonly MissingResultInfo = MissingResultInformation;
    readonly ParticipationType = ParticipationType;
    readonly ExerciseType = ExerciseType;
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly getCourseFromExercise = getCourseFromExercise;
    protected readonly AssessmentType = AssessmentType;

    @Input() participation: Participation;
    @Input() isBuilding: boolean;
    @Input() short = true;
    @Input() result?: Result;
    @Input() showUngradedResults = false;
    @Input() showBadge = false;
    @Input() showIcon = true;
    @Input() isInSidebarCard = false;
    @Input() missingResultInfo = MissingResultInformation.NONE;
    @Input() exercise?: Exercise;

    textColorClass: string;
    resultIconClass: IconProp;
    resultString: string;
    templateStatus: ResultTemplateStatus;
    submission?: Submission;
    badge: Badge;
    resultTooltip?: string;
    latestDueDate: dayjs.Dayjs | undefined;

    // Icons
    readonly faCircleNotch = faCircleNotch;
    readonly faFile = faFile;
    readonly farCircle = faCircle;
    readonly faExclamationCircle = faExclamationCircle;
    readonly faExclamationTriangle = faExclamationTriangle;

    private resultUpdateSubscription?: ReturnType<typeof setTimeout>;

    constructor(
        private participationService: ParticipationService,
        private translateService: TranslateService,
        private modalService: NgbModal,
        private exerciseService: ExerciseService,
        @Optional() private exerciseCacheService: ExerciseCacheService,
        private resultService: ResultService,
        private csvDownloadService: CsvDownloadService,
        private router: Router,
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
                if (!this.showUngradedResults) {
                    const firstRatedResult = this.participation.results.find((result) => result.rated);
                    if (firstRatedResult) {
                        this.result = firstRatedResult;
                        this.result.participation = this.participation;
                    }
                } else {
                    this.result = this.participation.results[0];
                    this.result.participation = this.participation;
                }
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

        this.evaluate();

        this.translateService.onLangChange.subscribe(() => {
            if (this.resultString) {
                this.resultString = this.resultService.getResultString(this.result, this.exercise, this.short);
            }
        });

        if (this.showBadge && this.result) {
            this.badge = ResultService.evaluateBadge(this.participation, this.result);
        }
    }

    ngOnDestroy(): void {
        if (this.resultUpdateSubscription) {
            clearTimeout(this.resultUpdateSubscription);
        }
    }

    /**
     * Executed when changes happen sets the corresponding template status to display a message.
     * @param changes The hashtable of the occurred changes as SimpleChanges object.
     */
    ngOnChanges(changes: SimpleChanges) {
        if (changes.participation || changes.result) {
            // If the participation or result changes, we need to re-initialize the component.
            this.ngOnInit();
        }

        if (changes.isBuilding?.currentValue) {
            // If it's building, we change the templateStatus to building regardless of any other settings.
            this.templateStatus = ResultTemplateStatus.IS_BUILDING;
        } else if (changes.missingResultInfo || changes.isBuilding?.previousValue) {
            // If ...
            // ... the result was building and is not building anymore, or
            // ... the missingResultInfo changed
            // we evaluate the result status.

            this.evaluate();
        }
    }

    /**
     * Sets the corresponding icon, styling and message to display results.
     */
    evaluate() {
        this.templateStatus = evaluateTemplateStatus(this.exercise, this.participation, this.result, this.isBuilding, this.missingResultInfo);
        if (this.templateStatus === ResultTemplateStatus.LATE) {
            this.textColorClass = getTextColorClass(this.result, this.templateStatus);
            this.resultIconClass = getResultIconClass(this.result, this.templateStatus);
            this.resultString = this.resultService.getResultString(this.result, this.exercise, this.short);
        } else if (
            this.result &&
            ((this.result.score !== undefined && (this.result.rated || this.result.rated == undefined || this.showUngradedResults)) || isAthenaAIResult(this.result))
        ) {
            this.textColorClass = getTextColorClass(this.result, this.templateStatus);
            this.resultIconClass = getResultIconClass(this.result, this.templateStatus);
            this.resultString = this.resultService.getResultString(this.result, this.exercise, this.short);
            this.resultTooltip = this.buildResultTooltip();
        } else if (this.templateStatus !== ResultTemplateStatus.MISSING) {
            // make sure that we do not display results that are 'rated=false' or that do not have a score
            // this state is only possible if no rated results are available at all, so we show the info that no graded result is available
            this.templateStatus = ResultTemplateStatus.NO_RESULT;
            this.result = undefined;
            this.resultString = '';
        }

        if (this.templateStatus === ResultTemplateStatus.IS_GENERATING_FEEDBACK && this.result?.completionDate) {
            const dueTime = -dayjs().diff(this.result.completionDate, 'milliseconds');
            this.resultUpdateSubscription = setTimeout(() => {
                this.evaluate();
                if (this.resultUpdateSubscription) {
                    clearTimeout(this.resultUpdateSubscription);
                }
            }, dueTime);
        }
    }

    /**
     * Gets the tooltip text that should be displayed next to the result string. Not required.
     */
    buildResultTooltip(): string | undefined {
        // Only show the 'preliminary' tooltip for programming student participation results and if the buildAndTestAfterDueDate has not passed.
        const programmingExercise = this.exercise as ProgrammingExercise;

        // Automatically generated feedback section
        if (this.result) {
            if (this.templateStatus === ResultTemplateStatus.FEEDBACK_GENERATION_FAILED) {
                return 'artemisApp.result.resultString.automaticAIFeedbackFailedTooltip';
            } else if (this.templateStatus === ResultTemplateStatus.FEEDBACK_GENERATION_TIMED_OUT) {
                return 'artemisApp.result.resultString.automaticAIFeedbackTimedOutTooltip';
            } else if (this.templateStatus === ResultTemplateStatus.IS_GENERATING_FEEDBACK) {
                return 'artemisApp.result.resultString.automaticAIFeedbackInProgressTooltip';
            } else if (this.templateStatus === ResultTemplateStatus.HAS_RESULT && isAthenaAIResult(this.result)) {
                return 'artemisApp.result.resultString.automaticAIFeedbackSuccessfulTooltip';
            }
        }
        if (
            this.participation &&
            isProgrammingExerciseStudentParticipation(this.participation) &&
            !isPracticeMode(this.participation) &&
            isResultPreliminary(this.result!, programmingExercise)
        ) {
            if (programmingExercise?.assessmentType !== AssessmentType.AUTOMATIC) {
                return 'artemisApp.result.preliminaryTooltipSemiAutomatic';
            }
            return 'artemisApp.result.preliminaryTooltip';
        }
    }

    /**
     * Show details of a result.
     * @param result Result object whose details will be displayed.
     */
    showDetails(result: Result) {
        const exerciseService = this.exerciseCacheService ?? this.exerciseService;
        if (this.exercise?.type === ExerciseType.TEXT) {
            const courseId = getCourseFromExercise(this.exercise)?.id;
            let submissionId = result.submission?.id;
            // In case of undefined result submission try the latest submission as this can happen before reloading the component
            if (!submissionId) {
                submissionId = result.participation?.submissions?.last()?.id;
            }
            this.router.navigate(['/courses', courseId, 'exercises', 'text-exercises', this.exercise?.id, 'participate', result.participation?.id, 'submission', submissionId]);
            return undefined;
        }

        const feedbackComponentParameters = prepareFeedbackComponentParameters(this.exercise, result, this.participation, this.templateStatus, this.latestDueDate, exerciseService);

        if (this.exercise?.type === ExerciseType.QUIZ) {
            // There is no feedback for quiz exercises.
            // Instead, the scoring is showed next to the different questions
            return undefined;
        }

        const modalRef = this.modalService.open(FeedbackComponent, { keyboard: true, size: 'xl' });
        const modalComponentInstance: FeedbackComponent = modalRef.componentInstance;

        modalComponentInstance.exercise = this.exercise;
        modalComponentInstance.result = result;
        if (feedbackComponentParameters.exerciseType) {
            modalComponentInstance.exerciseType = feedbackComponentParameters.exerciseType;
        }
        if (feedbackComponentParameters.showScoreChart) {
            modalComponentInstance.showScoreChart = feedbackComponentParameters.showScoreChart;
        }
        if (feedbackComponentParameters.messageKey) {
            modalComponentInstance.messageKey = feedbackComponentParameters.messageKey;
        }
        if (feedbackComponentParameters.latestDueDate) {
            this.latestDueDate = feedbackComponentParameters.latestDueDate;
            modalComponentInstance.latestDueDate = feedbackComponentParameters.latestDueDate;
        }
        if (feedbackComponentParameters.showMissingAutomaticFeedbackInformation) {
            modalComponentInstance.showMissingAutomaticFeedbackInformation = feedbackComponentParameters.showMissingAutomaticFeedbackInformation;
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
                this.csvDownloadService.downloadArtifact(artifact.fileContent, artifact.fileName);
            });
        }
    }
}
