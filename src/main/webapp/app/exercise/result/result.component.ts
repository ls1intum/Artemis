import { Component, OnChanges, OnDestroy, OnInit, SimpleChanges, inject, input, model } from '@angular/core';

import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import dayjs from 'dayjs/esm';
import { Participation, ParticipationType, getExercise } from 'app/exercise/shared/entities/participation/participation.model';
import { Submission, getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { captureException } from '@sentry/angular';
import { faCircleNotch, faExclamationCircle, faExclamationTriangle, faFile } from '@fortawesome/free-solid-svg-icons';
import { isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ResultProgressBarComponent } from './result-progress-bar/result-progress-bar.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass, UpperCasePipe } from '@angular/common';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { Badge, ResultService } from 'app/exercise/result/result.service';
import { MissingResultInformation, ResultTemplateStatus, evaluateTemplateStatus, getResultIconClass, getTextColorClass, isAthenaAIResult } from 'app/exercise/result/result.utils';
import { isProgrammingExerciseStudentParticipation, isResultPreliminary } from 'app/programming/shared/utils/programming-exercise.utils';
import { prepareFeedbackComponentParameters } from 'app/exercise/feedback/feedback.utils';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';

@Component({
    selector: 'jhi-result',
    templateUrl: './result.component.html',
    styleUrls: ['./result.component.scss'],
    imports: [
        ResultProgressBarComponent,
        FaIconComponent,
        TranslateDirective,
        NgClass,
        NgbTooltip,
        UpperCasePipe,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
        ArtemisDurationFromSecondsPipe,
    ],
})

/**
 * When using the result component make sure that the reference to the participation input is changed if the result changes
 * e.g. by using Object.assign to trigger ngOnChanges which makes sure that the result is updated
 */
export class ResultComponent implements OnInit, OnChanges, OnDestroy {
    private translateService = inject(TranslateService);
    private dialogService = inject(DialogService);
    private exerciseService = inject(ExerciseService);
    private exerciseCacheService = inject(ExerciseCacheService, { optional: true });
    private resultService = inject(ResultService);
    private router = inject(Router);

    // make constants available to html
    readonly ResultTemplateStatus = ResultTemplateStatus;
    readonly MissingResultInfo = MissingResultInformation;
    readonly ParticipationType = ParticipationType;
    readonly ExerciseType = ExerciseType;
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly getCourseFromExercise = getCourseFromExercise;
    protected readonly AssessmentType = AssessmentType;

    // model() rather than input() because ngOnInit normalises these (e.g. derives exercise from participation)
    // and ngOnChanges rewrites result when a newer one arrives via the participation input.
    readonly participation = model<Participation>(undefined!);
    readonly isBuilding = input<boolean>(undefined!);
    readonly isQueued = input(false);
    readonly short = input(true);
    readonly result = model<Result | undefined>(undefined);
    readonly showUngradedResults = input(false);
    readonly showBadge = input(false);
    readonly showIcon = input(true);
    readonly isInSidebarCard = input(false);
    readonly showCompletion = input(true);
    readonly missingResultInfo = input(MissingResultInformation.NONE);
    readonly exercise = model<Exercise | undefined>(undefined);
    readonly estimatedCompletionDate = input<dayjs.Dayjs>();
    readonly buildStartDate = input<dayjs.Dayjs>();
    readonly showProgressBar = input(false);
    readonly showProgressBarBorder = input(false);

    textColorClass: string;
    resultIconClass: IconProp;
    resultString: string;
    templateStatus: ResultTemplateStatus;
    submission?: Submission;
    badge: Badge;
    resultTooltip?: string;
    latestDueDate: dayjs.Dayjs | undefined;

    estimatedDurationInterval?: ReturnType<typeof setInterval>;
    estimatedRemaining: number = 0;
    estimatedDuration: number = 0;

    // Icons
    readonly faCircleNotch = faCircleNotch;
    readonly faFile = faFile;
    readonly faExclamationCircle = faExclamationCircle;
    readonly faExclamationTriangle = faExclamationTriangle;

    private resultUpdateSubscription?: ReturnType<typeof setTimeout>;

    /**
     * Executed on initialization. It retrieves the results of a given
     * participation and displays the corresponding message.
     */
    ngOnInit(): void {
        const participation = this.participation();
        let result = this.result();
        let exercise = this.exercise();
        if (!result && participation) {
            exercise = exercise ?? getExercise(participation);
            participation.exercise = exercise;
            this.exercise.set(exercise);
            const results = getAllResultsOfAllSubmissions(participation.submissions);
            if (results.length) {
                if (exercise && exercise.type === ExerciseType.MODELING) {
                    // sort results by completionDate descending to ensure the newest result is shown
                    // this is important for modeling exercises since students can have multiple tries
                    // think about if this should be used for all types of exercises
                    results.sort((r1: Result, r2: Result) => {
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
                if (!this.showUngradedResults()) {
                    const firstRatedResult = results.find((r) => r?.rated);
                    if (firstRatedResult) {
                        result = firstRatedResult;
                        this.result.set(firstRatedResult);
                    }
                } else {
                    result = getAllResultsOfAllSubmissions(participation.submissions).first();
                    this.result.set(result);
                }
            }
        } else if (!participation && result) {
            // make sure participation is initialized in case it was not passed
            const newParticipation = result.submission!.participation!;
            exercise = exercise ?? getExercise(newParticipation);
            newParticipation.exercise = exercise;
            this.participation.set(newParticipation);
            this.exercise.set(exercise);
        } else if (participation) {
            exercise = exercise ?? getExercise(participation);
            participation.exercise = exercise;
            this.exercise.set(exercise);
        } else if (!result?.exampleResult) {
            // result of example submission does not have participation
            captureException(new Error('The result component did not get a participation or result as parameter and can therefore not display the score'));
            return;
        }
        // Note: it can still happen here that result is undefined, e.g. when participation.submissions.length == 0
        this.submission = this.result()?.submission;

        this.evaluate();

        this.translateService.onLangChange.subscribe(() => {
            if (this.resultString) {
                this.resultString = this.resultService.getResultString(this.result(), this.exercise(), this.participation(), this.short());
            }
        });

        if (this.showBadge() && this.result()) {
            this.badge = ResultService.evaluateBadge(this.participation(), this.result()!);
        }
    }

    ngOnDestroy(): void {
        if (this.resultUpdateSubscription) {
            clearTimeout(this.resultUpdateSubscription);
        }
        if (this.estimatedDurationInterval) {
            clearInterval(this.estimatedDurationInterval);
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

        if (changes.isBuilding?.currentValue && changes.isBuilding?.currentValue === true) {
            // If it's building, we change the templateStatus to building regardless of any other settings.
            this.templateStatus = ResultTemplateStatus.IS_BUILDING;
        } else if (changes.isQueued?.currentValue && changes.isQueued?.currentValue === true) {
            // If it's queued, we change the templateStatus to queued regardless of any other settings.
            this.templateStatus = ResultTemplateStatus.IS_QUEUED;
        } else if (changes.missingResultInfo || changes.isBuilding?.previousValue) {
            // If ...
            // ... the result was building and is not building anymore, or
            // ... the missingResultInfo changed
            // we evaluate the result status.
            this.evaluate();
        }

        clearInterval(this.estimatedDurationInterval);
        if (this.estimatedCompletionDate() && this.buildStartDate()) {
            this.estimatedDurationInterval = setInterval(() => {
                this.estimatedRemaining = Math.max(0, dayjs(this.estimatedCompletionDate()).diff(dayjs(), 'seconds'));
                this.estimatedDuration = dayjs(this.estimatedCompletionDate()).diff(dayjs(this.buildStartDate()), 'seconds');
            });
        }
    }

    /**
     * Sets the corresponding icon, styling and message to display results.
     */
    evaluate() {
        const result = this.result();
        const exercise = this.exercise();
        const participation = this.participation();
        this.templateStatus = evaluateTemplateStatus(exercise, participation, result, this.isBuilding(), this.missingResultInfo(), this.isQueued());
        if (this.templateStatus === ResultTemplateStatus.LATE) {
            this.textColorClass = getTextColorClass(result, participation, this.templateStatus);
            this.resultIconClass = getResultIconClass(result, participation, this.templateStatus);
            this.resultString = this.resultService.getResultString(result, exercise, participation, this.short());
        } else if (result && ((result.score !== undefined && (result.rated || result.rated == undefined || this.showUngradedResults())) || isAthenaAIResult(result))) {
            this.textColorClass = getTextColorClass(result, participation, this.templateStatus);
            this.resultIconClass = getResultIconClass(result, participation, this.templateStatus);
            this.resultString = this.resultService.getResultString(result, exercise, participation, this.short());
            this.resultTooltip = this.buildResultTooltip();
        } else if (this.templateStatus !== ResultTemplateStatus.MISSING) {
            // make sure that we do not display results that are 'rated=false' or that do not have a score
            // this state is only possible if no rated results are available at all, so we show the info that no graded result is available
            this.templateStatus = ResultTemplateStatus.NO_RESULT;
            this.result.set(undefined);
            this.resultString = '';
        }

        if (this.templateStatus === ResultTemplateStatus.IS_GENERATING_FEEDBACK && this.result()?.completionDate) {
            const dueTime = -dayjs().diff(this.result()!.completionDate, 'milliseconds');
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
        const exercise = this.exercise();
        const programmingExercise = exercise as ProgrammingExercise;
        const result = this.result();

        // Automatically generated feedback section
        if (result) {
            if (this.templateStatus === ResultTemplateStatus.FEEDBACK_GENERATION_FAILED) {
                return 'artemisApp.result.resultString.automaticAIFeedbackFailedTooltip';
            } else if (this.templateStatus === ResultTemplateStatus.FEEDBACK_GENERATION_TIMED_OUT) {
                return 'artemisApp.result.resultString.automaticAIFeedbackTimedOutTooltip';
            } else if (this.templateStatus === ResultTemplateStatus.IS_GENERATING_FEEDBACK) {
                return 'artemisApp.result.resultString.automaticAIFeedbackInProgressTooltip';
            } else if (this.templateStatus === ResultTemplateStatus.HAS_RESULT && isAthenaAIResult(result)) {
                return 'artemisApp.result.resultString.automaticAIFeedbackSuccessfulTooltip';
            }
        }
        const participation = this.participation();
        if (
            participation &&
            isProgrammingExerciseStudentParticipation(participation) &&
            !isPracticeMode(participation) &&
            isResultPreliminary(result!, participation, programmingExercise)
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
        const exercise = this.exercise();
        const participation = this.participation();
        if (exercise?.type === ExerciseType.TEXT || exercise?.type === ExerciseType.MODELING) {
            const courseId = getCourseFromExercise(exercise)?.id;
            const submissionId = result.submission?.id;

            const exerciseTypePath = exercise?.type === ExerciseType.TEXT ? 'text-exercises' : 'modeling-exercises';

            this.router.navigate([
                '/courses',
                courseId,
                'exercises',
                exerciseTypePath,
                exercise?.id,
                'participate',
                participation?.id,
                'submission',
                submissionId,
                'result',
                result.id,
            ]);
            return undefined;
        }

        const feedbackComponentParameters = prepareFeedbackComponentParameters(exercise, result, participation, this.templateStatus, this.latestDueDate, exerciseService);

        if (exercise?.type === ExerciseType.QUIZ) {
            // There is no feedback for quiz exercises.
            // Instead, the scoring is showed next to the different questions
            return undefined;
        }

        if (feedbackComponentParameters.latestDueDate) {
            this.latestDueDate = feedbackComponentParameters.latestDueDate;
        }

        this.dialogService.open(FeedbackComponent, {
            header: this.translateService.instant('artemisApp.result.detail.feedback'),
            width: '80rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: true,
            data: {
                exercise,
                result,
                participation,
                exerciseType: feedbackComponentParameters.exerciseType,
                showScoreChart: feedbackComponentParameters.showScoreChart,
                messageKey: feedbackComponentParameters.messageKey,
                latestDueDate: feedbackComponentParameters.latestDueDate,
                showMissingAutomaticFeedbackInformation: feedbackComponentParameters.showMissingAutomaticFeedbackInformation,
            },
        });
    }
}
