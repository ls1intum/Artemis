import { Component, Input, OnChanges, OnInit, Optional, SimpleChanges } from '@angular/core';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { MissingResultInformation, ResultTemplateStatus, evaluateTemplateStatus, getResultIconClass, getTextColorClass } from 'app/exercises/shared/result/result.utils';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import dayjs from 'dayjs/esm';
import { isProgrammingExerciseStudentParticipation, isResultPreliminary } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Participation, ParticipationType, getExercise } from 'app/entities/participation/participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';
import { Result } from 'app/entities/result.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { captureException } from '@sentry/angular-ivy';
import { hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { faCircleNotch, faExclamationCircle, faExclamationTriangle, faFile } from '@fortawesome/free-solid-svg-icons';
import { faCircle } from '@fortawesome/free-regular-svg-icons';
import { Badge, ResultService } from 'app/exercises/shared/result/result.service';
import { ExerciseCacheService } from 'app/exercises/shared/exercise/exercise-cache.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { isPracticeMode } from 'app/entities/participation/student-participation.model';

@Component({
    selector: 'jhi-result',
    templateUrl: './result.component.html',
    styleUrls: ['./result.component.scss'],
})

/**
 * When using the result component make sure that the reference to the participation input is changed if the result changes
 * e.g. by using Object.assign to trigger ngOnChanges which makes sure that the result is updated
 */
export class ResultComponent implements OnInit, OnChanges {
    // make constants available to html
    readonly ResultTemplateStatus = ResultTemplateStatus;
    readonly MissingResultInfo = MissingResultInformation;
    readonly ParticipationType = ParticipationType;
    readonly ExerciseType = ExerciseType;
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly getCourseFromExercise = getCourseFromExercise;

    @Input() participation: Participation;
    @Input() isBuilding: boolean;
    @Input() short = true;
    @Input() result?: Result;
    @Input() showUngradedResults = false;
    @Input() showBadge = false;
    @Input() showIcon = true;
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

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private participationService: ParticipationService,
        private translateService: TranslateService,
        private http: HttpClient,
        private modalService: NgbModal,
        private exerciseService: ExerciseService,
        @Optional() private exerciseCacheService: ExerciseCacheService,
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
        this.templateStatus = evaluateTemplateStatus(this.exercise, this.participation, this.result, this.isBuilding, this.missingResultInfo);

        if (this.templateStatus === ResultTemplateStatus.LATE) {
            this.textColorClass = getTextColorClass(this.result, this.templateStatus);
            this.resultIconClass = getResultIconClass(this.result, this.templateStatus);
            this.resultString = this.resultService.getResultString(this.result, this.exercise, this.short);
        } else if (this.result && this.result.score !== undefined && (this.result.rated || this.result.rated == undefined || this.showUngradedResults)) {
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
        if (!result.participation) {
            result.participation = this.participation;
        }

        if (this.exercise?.type === ExerciseType.QUIZ) {
            // There is no feedback for quiz exercises.
            // Instead, the scoring is showed next to the different questions
            return;
        }

        const modalRef = this.modalService.open(FeedbackComponent, { keyboard: true, size: 'xl' });
        const componentInstance: FeedbackComponent = modalRef.componentInstance;
        componentInstance.exercise = this.exercise;
        componentInstance.result = result;
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
     * Determines if some information about testcases could still be hidden because of later individual due dates
     * @param componentInstance the detailed result view
     */
    private determineShowMissingAutomaticFeedbackInformation(componentInstance: FeedbackComponent) {
        if (this.latestDueDate) {
            this.setShowMissingAutomaticFeedbackInformation(componentInstance, this.latestDueDate);
        } else {
            const service = this.exerciseCacheService ?? this.exerciseService;
            service.getLatestDueDate(this.exercise!.id!).subscribe((latestDueDate) => {
                if (latestDueDate) {
                    this.setShowMissingAutomaticFeedbackInformation(componentInstance, latestDueDate);
                }
            });
        }
    }

    private setShowMissingAutomaticFeedbackInformation(componentInstance: FeedbackComponent, latestDueDate: dayjs.Dayjs) {
        this.latestDueDate = latestDueDate;
        componentInstance.showMissingAutomaticFeedbackInformation = dayjs().isBefore(latestDueDate);
        componentInstance.latestDueDate = this.latestDueDate;
    }
}
