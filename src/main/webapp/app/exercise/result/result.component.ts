import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';

import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import dayjs from 'dayjs/esm';
import { Participation, getExercise } from 'app/exercise/shared/entities/participation/participation.model';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch, faExclamationCircle, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ResultProgressBarComponent } from './result-progress-bar/result-progress-bar.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgClass, UpperCasePipe } from '@angular/common';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { Badge, ResultService } from 'app/exercise/result/result.service';
import { MissingResultInformation, ResultTemplateStatus, evaluateTemplateStatus, getResultIconClass, getTextColorClass, isAthenaAIResult } from 'app/exercise/result/result.utils';
import { isProgrammingExerciseStudentParticipation, isResultPreliminary } from 'app/programming/shared/utils/programming-exercise.utils';
import { prepareFeedbackComponentParameters } from 'app/exercise/feedback/feedback.utils';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';

/**
 * Presentational result badge. It renders a single, already-resolved {@link Result} (plus the {@link Exercise} /
 * {@link Participation} context needed to evaluate the display status) — it does NOT pick a result out of a
 * participation's submissions, nor does it derive/mutate its own inputs. Callers that only have a participation must
 * resolve the result first (e.g. via {@code getLatestResultOfStudentParticipation}) and pass it in.
 */
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
export class ResultComponent {
    private translateService = inject(TranslateService);
    private dialogService = inject(DialogService);
    private exerciseService = inject(ExerciseService);
    private exerciseCacheService = inject(ExerciseCacheService, { optional: true });
    private resultService = inject(ResultService);
    private router = inject(Router);

    // make constants available to html
    readonly ResultTemplateStatus = ResultTemplateStatus;
    readonly MissingResultInfo = MissingResultInformation;
    readonly ExerciseType = ExerciseType;

    readonly participation = input<Participation>();
    readonly result = input<Result>();
    readonly exercise = input<Exercise>();
    readonly isBuilding = input(false);
    readonly isQueued = input(false);
    readonly short = input(true);
    readonly showUngradedResults = input(false);
    readonly showBadge = input(false);
    readonly showIcon = input(true);
    readonly isInSidebarCard = input(false);
    readonly showCompletion = input(true);
    readonly missingResultInfo = input(MissingResultInformation.NONE);
    readonly estimatedCompletionDate = input<dayjs.Dayjs>();
    readonly buildStartDate = input<dayjs.Dayjs>();
    readonly showProgressBar = input(false);
    readonly showProgressBarBorder = input(false);

    // Context resolved by trivial object-graph navigation (NOT result-picking): callers may pass the exercise/participation
    // explicitly, otherwise we read them off the participation (or the result's submission's participation).
    readonly resolvedParticipation = computed(() => this.participation() ?? this.result()?.submission?.participation);
    readonly resolvedExercise = computed(() => {
        const participation = this.participation() ?? this.result()?.submission?.participation;
        return this.exercise() ?? (participation ? getExercise(participation) : undefined);
    });

    // True when the passed result is actually displayable as a score (rated, or ungraded allowed, or an Athena AI result).
    private readonly displayableResult = computed(() => {
        const result = this.result();
        return !!result && ((result.score !== undefined && (result.rated || result.rated === undefined || this.showUngradedResults())) || isAthenaAIResult(result));
    });

    // Bumped by the Athena-feedback effect below to force the IS_GENERATING_FEEDBACK -> TIMED_OUT re-evaluation once the timeout elapses.
    private readonly feedbackRecheckTick = signal(0);

    readonly templateStatus = computed<ResultTemplateStatus>(() => {
        this.feedbackRecheckTick();
        // isBuilding/isQueued win unconditionally, mirroring the former ngOnChanges override (which applied regardless of exercise type).
        if (this.isBuilding()) {
            return ResultTemplateStatus.IS_BUILDING;
        }
        if (this.isQueued()) {
            return ResultTemplateStatus.IS_QUEUED;
        }
        const status = evaluateTemplateStatus(this.resolvedExercise(), this.resolvedParticipation(), this.result(), this.isBuilding(), this.missingResultInfo(), this.isQueued());
        if (status === ResultTemplateStatus.LATE || this.displayableResult()) {
            return status;
        }
        // A non-displayable result (unrated / no score, and not an Athena result) is shown as "no (graded) result", except for MISSING.
        return status === ResultTemplateStatus.MISSING ? status : ResultTemplateStatus.NO_RESULT;
    });

    readonly textColorClass = computed(() => {
        const status = this.templateStatus();
        return status === ResultTemplateStatus.LATE || this.displayableResult() ? getTextColorClass(this.result(), this.resolvedParticipation()!, status) : '';
    });

    readonly resultIconClass = computed<IconProp | undefined>(() => {
        const status = this.templateStatus();
        return status === ResultTemplateStatus.LATE || this.displayableResult() ? getResultIconClass(this.result(), this.resolvedParticipation()!, status) : undefined;
    });

    readonly resultString = computed(() => {
        this.currentLang(); // re-translate the string when the UI language changes
        const status = this.templateStatus();
        return status === ResultTemplateStatus.LATE || this.displayableResult()
            ? this.resultService.getResultString(this.result(), this.resolvedExercise(), this.resolvedParticipation(), this.short())
            : '';
    });

    readonly resultTooltip = computed<string | undefined>(() => (this.displayableResult() ? this.buildResultTooltip() : undefined));

    readonly badge = computed<Badge | undefined>(() => {
        const participation = this.resolvedParticipation();
        const result = this.result();
        return this.showBadge() && result && participation ? ResultService.evaluateBadge(participation, result) : undefined;
    });

    latestDueDate: dayjs.Dayjs | undefined;
    readonly estimatedRemaining = signal<number>(0);
    readonly estimatedDuration = signal<number>(0);

    // Icons
    readonly faCircleNotch = faCircleNotch;
    readonly faExclamationCircle = faExclamationCircle;
    readonly faExclamationTriangle = faExclamationTriangle;

    private readonly currentLang = toSignal(this.translateService.onLangChange.pipe(map((event) => event.lang)), { initialValue: this.translateService.getCurrentLang() });

    constructor() {
        // Build-duration countdown for the progress bar. It is second-granularity, so update once immediately and then
        // once per second (an interval without delay would schedule zoneless change detection as fast as the browser allows).
        effect((onCleanup) => {
            const estimatedCompletionDate = this.estimatedCompletionDate();
            const buildStartDate = this.buildStartDate();
            if (!estimatedCompletionDate || !buildStartDate) {
                return;
            }
            const update = () => {
                this.estimatedRemaining.set(Math.max(0, dayjs(estimatedCompletionDate).diff(dayjs(), 'seconds')));
                this.estimatedDuration.set(dayjs(estimatedCompletionDate).diff(dayjs(buildStartDate), 'seconds'));
            };
            update();
            const interval = setInterval(update, 1000);
            onCleanup(() => clearInterval(interval));
        });

        // While Athena feedback is generating, re-check once the result's timeout has elapsed so the status can flip to TIMED_OUT.
        effect((onCleanup) => {
            if (this.templateStatus() === ResultTemplateStatus.IS_GENERATING_FEEDBACK && this.result()?.completionDate) {
                const dueTime = -dayjs().diff(this.result()!.completionDate, 'milliseconds');
                const timeout = setTimeout(() => this.feedbackRecheckTick.update((tick) => tick + 1), dueTime);
                onCleanup(() => clearTimeout(timeout));
            }
        });
    }

    /**
     * Gets the tooltip text that should be displayed next to the result string. Not required.
     */
    buildResultTooltip(): string | undefined {
        // Only show the 'preliminary' tooltip for programming student participation results and if the buildAndTestAfterDueDate has not passed.
        const exercise = this.resolvedExercise();
        const programmingExercise = exercise as ProgrammingExercise;
        const result = this.result();

        // Automatically generated feedback section
        if (result) {
            if (this.templateStatus() === ResultTemplateStatus.FEEDBACK_GENERATION_FAILED) {
                return 'artemisApp.result.resultString.automaticAIFeedbackFailedTooltip';
            } else if (this.templateStatus() === ResultTemplateStatus.FEEDBACK_GENERATION_TIMED_OUT) {
                return 'artemisApp.result.resultString.automaticAIFeedbackTimedOutTooltip';
            } else if (this.templateStatus() === ResultTemplateStatus.IS_GENERATING_FEEDBACK) {
                return 'artemisApp.result.resultString.automaticAIFeedbackInProgressTooltip';
            } else if (this.templateStatus() === ResultTemplateStatus.HAS_RESULT && isAthenaAIResult(result)) {
                return 'artemisApp.result.resultString.automaticAIFeedbackSuccessfulTooltip';
            }
        }
        const participation = this.resolvedParticipation();
        if (
            result &&
            participation &&
            isProgrammingExerciseStudentParticipation(participation) &&
            !isPracticeMode(participation) &&
            isResultPreliminary(result, participation, programmingExercise)
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
        const exercise = this.resolvedExercise();
        const participation = this.resolvedParticipation();
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

        const feedbackComponentParameters = prepareFeedbackComponentParameters(exercise, result, participation!, this.templateStatus(), this.latestDueDate, exerciseService);

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
            breakpoints: {
                '1400px': '75vw',
                '1200px': '85vw',
                '992px': '95vw',
            },
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
