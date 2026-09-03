import { Component, DestroyRef, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { SortService } from 'app/foundation/service/sort.service';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, IncludedInOverallScore, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { countSubmissions, getExerciseDueDate } from 'app/exercise/util/exercise.utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { InformationBox, InformationBoxComponent } from 'app/shared-ui/information-box/information-box.component';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { isDateLessThanAWeekInTheFuture } from 'app/foundation/util/date.utils';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CommonModule } from '@angular/common';
import { SubmissionResultStatusComponent } from 'app/course/overview/submission-result-status/submission-result-status.component';
import { DifficultyLevelComponent } from 'app/exercise/difficulty-level/difficulty-level.component';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { LiveQuizParticipationStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ResultHistoryDropdownComponent } from './result-history-dropdown/result-history-dropdown.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DEFAULT_ATHENA_FEEDBACK_REQUEST_LIMIT } from 'app/course/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { UserStoryEffortService } from 'app/programming/shared/services/user-story-effort.service';
import { UserStoryEffort } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { UserStoryEffortFieldComponent } from 'app/programming/overview/user-story-effort/user-story-effort-field.component';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

/**
 * Live, quiz-specific information shown in the exercise header during a live or practice quiz participation,
 * replacing the dedicated quiz header row that used to sit above the questions.
 */
export interface QuizLiveHeaderInfo {
    /** Whether the live remaining-time countdown should be shown (quiz running, not yet submitted). */
    showRemainingTime: boolean;
    /** Humanized remaining-time text, e.g. "12 min 30 s". */
    remainingTimeText?: string;
    /** Bootstrap text color suffix for the remaining time (e.g. 'warning', 'danger'); undefined for the default color. */
    remainingTimeColor?: string;
    /** Whether the "results available" date should be shown (after submit / once time is up). */
    showResultsAvailable: boolean;
    resultsAvailableDate?: dayjs.Dayjs;
    /** Whether the quiz's total duration should be shown (waiting to start, before the countdown begins). */
    showDuration?: boolean;
    /** Humanized duration text, e.g. "45min". */
    durationText?: string;
}

/** Field-wise equality for {@link QuizLiveHeaderInfo}, used as the signal's `equal` so the per-tick rebuild only notifies when a displayed value changes. */
export function quizLiveHeaderInfoEqual(a: QuizLiveHeaderInfo | undefined, b: QuizLiveHeaderInfo | undefined): boolean {
    if (a === b) {
        return true;
    }
    if (!a || !b) {
        return false;
    }
    return (
        a.showRemainingTime === b.showRemainingTime &&
        a.remainingTimeText === b.remainingTimeText &&
        a.remainingTimeColor === b.remainingTimeColor &&
        a.showResultsAvailable === b.showResultsAvailable &&
        (a.resultsAvailableDate === b.resultsAvailableDate || (!!a.resultsAvailableDate && !!b.resultsAvailableDate && a.resultsAvailableDate.isSame(b.resultsAvailableDate))) &&
        a.showDuration === b.showDuration &&
        a.durationText === b.durationText
    );
}

@Component({
    selector: 'jhi-exercise-headers-information',
    templateUrl: './exercise-headers-information.component.html',
    imports: [
        SubmissionResultStatusComponent,
        InformationBoxComponent,
        UserStoryEffortFieldComponent,
        DifficultyLevelComponent,
        ExerciseCategoriesComponent,
        ArtemisDatePipe,
        ArtemisTimeAgoPipe,
        ArtemisTranslatePipe,
        CommonModule,
        ResultHistoryDropdownComponent,
        NgbTooltip,
    ],
    /* Our tsconfig file has `preserveWhitespaces: 'true'` which causes whitespace to affect content projection.
    We need to set it to 'false 'for this component, otherwise the components with the selector [contentComponent]
    will not be projected into their specific slot of the "InformationBoxComponent" component.*/
    preserveWhitespaces: false,
})
export class ExerciseHeadersInformationComponent {
    private readonly destroyRef = inject(DestroyRef);
    private sortService = inject(SortService);
    private readonly userStoryEffortService = inject(UserStoryEffortService);
    private readonly alertService = inject(AlertService);
    private serverDateService = inject(ArtemisServerDateService);

    /** Captured once: the server time used as the reference point for all relative/absolute date displays. */
    private readonly now = this.serverDateService.now();

    readonly resultHistoryDropdown = viewChild(ResultHistoryDropdownComponent);

    readonly viewingSubmissionChange = output<boolean>();

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly dayjs = dayjs;

    readonly exercise = input.required<Exercise>();
    readonly studentParticipation = input<StudentParticipation>();
    /** Explicitly provided course; falls back to the exercise's own course via {@link resolvedCourse}. */
    readonly course = input<Course>();
    readonly submissionPolicy = input<SubmissionPolicy>();
    readonly isPractice = input<boolean>(false);
    /**
     * Whether the result-history trigger reacts to hover and click. False where the header is a read-only preview (e.g.
     * the variant cards), keeping the tooltip and results popover inert without an outside style override.
     */
    readonly interactive = input<boolean>(true);
    readonly athenaEnabled = input<boolean>(false);
    readonly feedbackRequestLimit = input<number>(DEFAULT_ATHENA_FEEDBACK_REQUEST_LIMIT);
    /** Live participation status override for the result badge (e.g. PARTICIPATING/SUBMITTED) during a live quiz. */
    readonly quizLiveStatus = input<LiveQuizParticipationStatus>();
    /** Live quiz info to render as extra header boxes; undefined for non-quiz exercises or outside a live/practice participation. */
    readonly quizLiveHeaderInfo = input<QuizLiveHeaderInfo>();

    /** Course resolved from the explicit input, falling back to the exercise's own course. */
    readonly resolvedCourse = computed<Course | undefined>(() => this.course() ?? getCourseFromExercise(this.exercise()));

    readonly dueDate = computed<dayjs.Dayjs | undefined>(() => getExerciseDueDate(this.exercise(), this.studentParticipation()));

    private readonly allResults = computed<Result[]>(() => getAllResultsOfAllSubmissions(this.studentParticipation()?.submissions));

    /** Results across all submissions, sorted by id descending (newest first). The updated participation by the websocket is not guaranteed to be sorted. */
    readonly sortedHistoryResults = computed<Result[]>(() => {
        // Copy before sorting: allResults() is a shared memoized array and sortByProperty sorts in place.
        const results = Array.from(this.allResults());
        this.sortService.sortByProperty(results, 'id', false);
        return results;
    });

    readonly numberOfSubmissions = computed<number>(() => countSubmissions(this.studentParticipation()));

    readonly achievedPoints = computed<number>(() => {
        const results = this.sortedHistoryResults();
        // Practice results are unrated, so in practice mode use the latest result regardless of the rated flag.
        const latestResult = this.isPractice() ? results.first() : results.filter((result) => result.rated).first();
        if (!latestResult) {
            return 0;
        }
        return roundValueSpecifiedByCourseSettings((latestResult.score! * this.exercise().maxPoints!) / 100, this.resolvedCourse()) ?? 0;
    });

    readonly currentFeedbackRequestCount = computed<number>(
        () => this.allResults().filter((result) => result.assessmentType === AssessmentType.AUTOMATIC_ATHENA && result.successful === true).length,
    );

    readonly individualComplaintDueDate = computed<dayjs.Dayjs | undefined>(() => {
        const course = this.resolvedCourse();
        if (!course?.maxComplaintTimeDays) {
            return undefined;
        }
        return ComplaintService.getIndividualComplaintDueDate(this.exercise(), course.maxComplaintTimeDays, this.allResults().last(), this.studentParticipation());
    });

    /**
     * The effort the student reported for a user story, shown as two header boxes.
     *
     * Loaded here rather than per box so both share one request, and only once a participation exists: without one there
     * is nothing to report on, the server rejects the read, and the student would get an error alert on every visit.
     */
    protected readonly reportedEffort = signal<UserStoryEffort | undefined>(undefined);

    /** Past the due date the server refuses the write, so the boxes are read-only rather than misleadingly clickable. */
    protected readonly isEffortEditable = computed(() => {
        const dueDate = this.exercise().dueDate;
        return !dueDate || dayjs().isBefore(dueDate);
    });

    constructor() {
        effect(() => {
            const exercise = this.exercise();
            const participationExists = this.studentParticipation()?.id !== undefined;
            untracked(() => this.loadReportedEffort(exercise, participationExists));
        });
    }

    private loadReportedEffort(exercise: Exercise, participationExists: boolean): void {
        if (exercise?.type !== ExerciseType.USER_STORY || exercise.id === undefined || !participationExists) {
            this.reportedEffort.set(undefined);
            return;
        }
        this.userStoryEffortService
            .getEffort(exercise.id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (effort) => this.reportedEffort.set(effort),
                error: () => this.reportedEffort.set(undefined),
            });
    }

    /**
     * Stores one of the two reported values, keeping the other as it stands, and confirms the save - without it the
     * student has no way to tell an accepted value from a silently dropped one.
     */
    protected saveReportedEffort(field: 'estimatedEffort' | 'actualEffort', value: number | undefined): void {
        const exerciseId = this.exercise().id;
        if (exerciseId === undefined) {
            return;
        }
        const effort: UserStoryEffort = cloneWith(this.reportedEffort() ?? {}, { [field]: value });
        this.userStoryEffortService.updateEffort(exerciseId, effort).subscribe({
            next: (saved) => {
                this.reportedEffort.set(saved);
                this.editingEffortField.set(undefined);
                this.alertService.success('artemisApp.userStoryEffort.saved');
            },
            error: (error: HttpErrorResponse) => this.alertService.addErrorAlert(error.error?.title ?? error.message, error.error?.message, error.error?.params),
        });
    }

    /** Which of the two effort boxes is currently being edited, if any. Owned here so the whole box is the click target. */
    protected readonly editingEffortField = signal<'estimatedEffort' | 'actualEffort' | undefined>(undefined);

    /**
     * The two effort boxes, shown only once there is a participation to report on. An unreported value gives its box an
     * orange border: a missing estimate is what blocks pushing to the milestone group's shared repository.
     */
    getUserStoryEffortItems(): InformationBox[] {
        if (this.exercise()?.type !== ExerciseType.USER_STORY || this.studentParticipation()?.id === undefined) {
            return [];
        }
        const effort = this.reportedEffort();
        return [
            {
                title: 'artemisApp.userStoryEffort.estimatedEffortShort',
                content: { type: 'userStoryEffort', value: 'estimatedEffort' },
                isContentComponent: true,
                borderColor: effort?.estimatedEffort === undefined ? 'state-warning' : undefined,
            },
            {
                title: 'artemisApp.userStoryEffort.actualEffortShort',
                content: { type: 'userStoryEffort', value: 'actualEffort' },
                isContentComponent: true,
                borderColor: effort?.actualEffort === undefined ? 'state-warning' : undefined,
            },
        ];
    }

    /** Starts editing one effort box; ignored past the due date, when the server would refuse the write anyway. */
    protected startEditingEffort(field: 'estimatedEffort' | 'actualEffort'): void {
        if (!this.isEffortEditable()) {
            return;
        }
        this.editingEffortField.set(field);
    }

    /** All header information boxes, in display order: the generic exercise boxes first, then the live quiz boxes last. */
    readonly informationBoxItems = computed<InformationBox[]>(() => {
        const items: InformationBox[] = [...this.getPointsItems(), ...this.getUserStoryEffortItems(), ...this.getDueDateItems()];
        const startDateItem = this.getStartDateItem();
        if (startDateItem) {
            items.push(startDateItem);
        }
        items.push(this.getSubmissionStatusItem());
        const submissionPolicyItem = this.getSubmissionPolicyItemIfActive();
        if (submissionPolicyItem) {
            items.push(submissionPolicyItem);
        }
        const staticCodeAnalysisItem = this.getStaticCodeAnalysisItemIfEnabled();
        if (staticCodeAnalysisItem) {
            items.push(staticCodeAnalysisItem);
        }
        const aiFeedbackItem = this.getAiFeedbackItemIfEnabled();
        if (aiFeedbackItem) {
            items.push(aiFeedbackItem);
        }
        const difficultyItem = this.getDifficultyItem();
        if (difficultyItem) {
            items.push(difficultyItem);
        }
        const categoryItem = this.getCategoryItem();
        if (categoryItem) {
            items.push(categoryItem);
        }
        items.push(...this.getQuizLiveInfoItems());
        return items;
    });

    getPointsItems(): InformationBox[] {
        const { maxPoints, bonusPoints } = this.exercise();
        if (!maxPoints) {
            return [];
        }
        const achievedPoints = this.achievedPoints();
        if (bonusPoints) {
            let achievedBonusPoints = 0;
            // If the student has more points than the max points, the bonus points are calculated
            if (achievedPoints > maxPoints) {
                achievedBonusPoints = roundValueSpecifiedByCourseSettings(achievedPoints - maxPoints, this.resolvedCourse());
            }
            return [this.getPointsItem('points', maxPoints, achievedPoints - achievedBonusPoints), this.getPointsItem('bonus', bonusPoints, achievedBonusPoints)];
        }
        return [this.getPointsItem('points', maxPoints, achievedPoints)];
    }

    getDueDateItems(): InformationBox[] {
        const items: InformationBox[] = [];
        // During a running live/practice quiz the remaining-time countdown takes the place of the due date.
        // While the quiz participation component hasn't mounted yet, quizLiveHeaderInfo is still undefined; skip the
        // due-date fallback for that brief window too, otherwise the due date flashes before being replaced once the
        // quiz-specific box resolves (the exercise's own due date is known immediately, the quiz box lags behind it).
        const quizTimeItem = this.getQuizTimeItem();
        if (quizTimeItem) {
            items.push(quizTimeItem);
        } else if (!(this.exercise().type === ExerciseType.QUIZ && this.quizLiveHeaderInfo() === undefined)) {
            const dueDateItem = this.getDueDateItem();
            if (dueDateItem) {
                items.push(dueDateItem);
            }
        }
        const exercise = this.exercise();
        // If the due date is in the past and the assessment due date is in the future, show the assessment due date
        if (this.dueDate()?.isBefore(this.now) && exercise.assessmentDueDate?.isAfter(this.now)) {
            items.push({
                title: 'artemisApp.courseOverview.exerciseDetails.assessmentDue',
                content: {
                    type: 'dateTime',
                    value: exercise.assessmentDueDate,
                },
                isContentComponent: true,
                tooltip: 'artemisApp.courseOverview.exerciseDetails.assessmentDueTooltip',
                tooltipParams: { date: exercise.assessmentDueDate.format('lll') },
            });
        }
        // If the assessment due date is in the past and the complaint due date is in the future, show the complaint due date
        const complaintDueDate = this.individualComplaintDueDate();
        if (exercise.assessmentDueDate?.isBefore(this.now) && complaintDueDate?.isAfter(this.now)) {
            items.push({
                title: 'artemisApp.courseOverview.exerciseDetails.complaintDue',
                content: {
                    type: 'dateTime',
                    value: complaintDueDate,
                },
                isContentComponent: true,
                tooltip: 'artemisApp.courseOverview.exerciseDetails.complaintDueTooltip',
                tooltipParams: { date: complaintDueDate.format('lll') },
            });
        }
        return items;
    }

    getDueDateItem(): InformationBox | undefined {
        const dueDate = this.dueDate();
        if (!dueDate) {
            return undefined;
        }
        const isDueDateInThePast = dueDate.isBefore(this.now);
        // If the due date is less than a day away, the color change to red
        const dueDateStatusBadge = dueDate.isBetween(this.now, this.now.add(1, 'day')) ? 'danger' : 'body-color';
        // If the due date is less than a week away, text is displayed relatively e.g. 'in 2 days'
        const shouldDisplayDueDateRelative = isDateLessThanAWeekInTheFuture(dueDate, this.now);

        if (isDueDateInThePast) {
            return {
                title: 'artemisApp.courseOverview.exerciseDetails.submissionDueOver',
                content: {
                    type: 'dateTime',
                    value: dueDate,
                },
                isContentComponent: true,
            };
        }

        return {
            title: 'artemisApp.courseOverview.exerciseDetails.submissionDue',
            content: {
                type: shouldDisplayDueDateRelative ? 'timeAgo' : 'dateTime',
                value: dueDate,
            },
            isContentComponent: true,
            tooltip: shouldDisplayDueDateRelative ? 'artemisApp.courseOverview.exerciseDetails.submissionDueTooltip' : undefined,
            contentColor: dueDateStatusBadge,
            tooltipParams: { date: dueDate.format('lll') },
        };
    }

    getStartDateItem(): InformationBox | undefined {
        const startDate = this.exercise().startDate;
        if (!startDate || !this.now.isBefore(startDate)) {
            return undefined;
        }
        // If the start date is less than a week away, text is displayed relatively e.g. 'in 2 days'
        const shouldDisplayStartDateRelative = isDateLessThanAWeekInTheFuture(startDate, this.now);
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.startDate',
            content: {
                type: shouldDisplayStartDateRelative ? 'timeAgo' : 'dateTime',
                value: startDate,
            },
            isContentComponent: true,
            tooltip: shouldDisplayStartDateRelative ? 'artemisApp.exerciseActions.startExerciseBeforeStartDate' : undefined,
        };
    }

    getDifficultyItem(): InformationBox | undefined {
        const difficulty = this.exercise().difficulty;
        if (!difficulty) {
            return undefined;
        }
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.difficulty',
            content: {
                type: 'difficultyLevel',
                value: difficulty,
            },
            isContentComponent: true,
        };
    }

    getSubmissionStatusItem(): InformationBox {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.status',
            content: {
                type: 'submissionStatus',
                value: this.exercise(),
            },
            isContentComponent: true,
        };
    }

    getCategoryItem(): InformationBox | undefined {
        const exercise = this.exercise();
        const notReleased = exercise.releaseDate?.isAfter(this.now);
        if (notReleased || exercise.includedInOverallScore !== IncludedInOverallScore.INCLUDED_COMPLETELY || exercise.categories?.length) {
            return {
                title: 'artemisApp.courseOverview.exerciseDetails.categories',
                content: {
                    type: 'categories',
                    value: exercise,
                },
                isContentComponent: true,
            };
        }
        return undefined;
    }

    getSubmissionPolicyItemIfActive(): InformationBox | undefined {
        const submissionPolicy = this.submissionPolicy();
        return submissionPolicy?.active && submissionPolicy?.submissionLimit ? this.getSubmissionPolicyItem() : undefined;
    }

    getSubmissionPolicyItem(): InformationBox {
        const submissionPolicy = this.submissionPolicy();
        return {
            title: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle',
            content: {
                type: 'string',
                value: `${this.numberOfSubmissions()} /  ${submissionPolicy?.submissionLimit}`,
            },
            contentColor: submissionPolicy?.submissionLimit ? this.getSubmissionColor() : 'body-color',
            tooltip: 'artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.' + submissionPolicy?.type + '.tooltip',
            tooltipParams: { points: submissionPolicy?.exceedingPenalty?.toString() },
        };
    }

    getSubmissionColor(numberOfSubmissions: number = this.numberOfSubmissions(), submissionLimit: number | undefined = this.submissionPolicy()?.submissionLimit): string {
        // default color should be 'body-color', thats why the default submissionsLeft is 2
        const submissionsLeft = submissionLimit ? submissionLimit - numberOfSubmissions : 2;
        let submissionColor = 'body-color';
        if (submissionsLeft === 1) submissionColor = 'warning';
        // 0 submissions left or limit is already reached
        else if (submissionsLeft <= 0) submissionColor = 'danger';
        return submissionColor;
    }

    getPointsItem(title: string, maxPoints: number, achievedPoints: number): InformationBox {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.' + title,
            content: {
                type: 'string',
                value: `${achievedPoints} / ${maxPoints}`,
            },
        };
    }

    getStaticCodeAnalysisItemIfEnabled(): InformationBox | undefined {
        const exercise = this.exercise();
        return exercise.type === ExerciseType.PROGRAMMING && (exercise as ProgrammingExercise).staticCodeAnalysisEnabled ? this.getStaticCodeAnalysisItem() : undefined;
    }

    getStaticCodeAnalysisItem(): InformationBox {
        const issueCount = this.sortedHistoryResults().first()?.codeIssueCount ?? 0;
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.codeIssues',
            content: {
                type: 'string',
                value: `${issueCount}`,
            },
            contentColor: issueCount > 0 ? 'warning' : 'success',
            tooltip: 'artemisApp.courseOverview.exerciseDetails.codeIssuesTooltip',
        };
    }

    getAiFeedbackItemIfEnabled(): InformationBox | undefined {
        return this.athenaEnabled() && this.exercise().allowFeedbackRequests ? this.getAiFeedbackItem() : undefined;
    }

    getAiFeedbackItem(): InformationBox {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.aiFeedbackRequests',
            content: {
                type: 'string',
                value: `${this.currentFeedbackRequestCount()} / ${this.feedbackRequestLimit()}`,
            },
            contentColor: this.currentFeedbackRequestCount() >= this.feedbackRequestLimit() ? 'danger' : 'warning',
            tooltip: 'artemisApp.courseOverview.exerciseDetails.aiFeedbackRequestsTooltip',
        };
    }

    getQuizTimeItem(): InformationBox | undefined {
        const info = this.quizLiveHeaderInfo();
        if (info?.showRemainingTime) {
            return {
                title: 'artemisApp.quizExercise.remainingTime',
                content: {
                    type: 'string',
                    value: info.remainingTimeText ?? '',
                },
                contentColor: info.remainingTimeColor,
            };
        }
        if (info?.showDuration) {
            return {
                title: 'artemisApp.quizExercise.duration',
                content: {
                    type: 'string',
                    value: info.durationText ?? '',
                },
            };
        }
        return undefined;
    }

    getQuizLiveInfoItems(): InformationBox[] {
        const info = this.quizLiveHeaderInfo();
        if (!info) {
            return [];
        }
        const items: InformationBox[] = [];
        if (info.showResultsAvailable && info.resultsAvailableDate) {
            items.push({
                title: 'artemisApp.quizExercise.resultsAvailable',
                content: {
                    type: 'dateTime',
                    value: info.resultsAvailableDate,
                },
                isContentComponent: true,
            });
        }
        return items;
    }
}
