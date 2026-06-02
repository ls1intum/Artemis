import { Component, computed, inject, input, output, viewChild } from '@angular/core';
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

/**
 * Live, quiz-specific information shown in the exercise header during a live or practice quiz participation.
 * This replaces the dedicated quiz header row that used to sit above the questions, so the questions can move up.
 * Computed by the quiz participation component and bridged up through the exercise split panel and header.
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
    /** Date when the results become available. */
    resultsAvailableDate?: dayjs.Dayjs;
}

@Component({
    selector: 'jhi-exercise-headers-information',
    templateUrl: './exercise-headers-information.component.html',
    imports: [
        SubmissionResultStatusComponent,
        InformationBoxComponent,
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
    private sortService = inject(SortService);
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
    /** Optional override for the result history; falls back to the results derived from the participation's submissions. */
    readonly sortedHistoryResultsInput = input<Result[] | undefined>();
    readonly isPractice = input<boolean>(false);
    readonly athenaEnabled = input<boolean>(false);
    readonly feedbackRequestLimit = input<number>(DEFAULT_ATHENA_FEEDBACK_REQUEST_LIMIT);
    /** Live participation status override for the result badge (e.g. PARTICIPATING/SUBMITTED) during a live quiz. */
    readonly quizLiveStatus = input<LiveQuizParticipationStatus>();
    /** Live quiz info to render as extra header boxes; undefined for non-quiz exercises or outside a live/practice participation. */
    readonly quizLiveHeaderInfo = input<QuizLiveHeaderInfo>();

    /** Course resolved from the explicit input, falling back to the exercise's own course. */
    readonly resolvedCourse = computed<Course | undefined>(() => this.course() ?? getCourseFromExercise(this.exercise()));

    readonly dueDate = computed<dayjs.Dayjs | undefined>(() => getExerciseDueDate(this.exercise(), this.studentParticipation()));

    /** Results across all submissions, sorted by id descending (newest first). The updated participation by the websocket is not guaranteed to be sorted. */
    readonly sortedHistoryResults = computed<Result[]>(() => {
        const results = Array.from(this.sortedHistoryResultsInput() ?? getAllResultsOfAllSubmissions(this.studentParticipation()?.submissions));
        this.sortService.sortByProperty(results, 'id', false);
        return results;
    });

    readonly numberOfSubmissions = computed<number>(() => countSubmissions(this.studentParticipation()));

    readonly achievedPoints = computed<number>(() => {
        const latestRatedResult = this.sortedHistoryResults()
            .filter((result) => result.rated)
            .first();
        if (!latestRatedResult) {
            return 0;
        }
        return roundValueSpecifiedByCourseSettings((latestRatedResult.score! * this.exercise().maxPoints!) / 100, this.resolvedCourse()) ?? 0;
    });

    readonly currentFeedbackRequestCount = computed<number>(
        () =>
            getAllResultsOfAllSubmissions(this.studentParticipation()?.submissions)?.filter(
                (result) => result.assessmentType === AssessmentType.AUTOMATIC_ATHENA && result.successful === true,
            ).length ?? 0,
    );

    readonly individualComplaintDueDate = computed<dayjs.Dayjs | undefined>(() => {
        const course = this.resolvedCourse();
        if (!course?.maxComplaintTimeDays) {
            return undefined;
        }
        return ComplaintService.getIndividualComplaintDueDate(
            this.exercise(),
            course.maxComplaintTimeDays,
            getAllResultsOfAllSubmissions(this.studentParticipation()?.submissions).last(),
            this.studentParticipation(),
        );
    });

    /**
     * All header information boxes, in display order. The generic exercise boxes come first; the live quiz boxes
     * (remaining time, results available) are appended last so they always render at the end of the header.
     */
    readonly informationBoxItems = computed<InformationBox[]>(() => {
        const items: InformationBox[] = [...this.getPointsItems(), ...this.getDueDateItems()];
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
        const quizRemainingTimeItem = this.getQuizRemainingTimeItem();
        if (quizRemainingTimeItem) {
            items.push(quizRemainingTimeItem);
        } else {
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

    /**
     * Builds the live remaining-time box from {@link quizLiveHeaderInfo}. Rendered in the due-date slot (via
     * {@link getDueDateItems}) so the countdown sits where the due date would otherwise be. Returns undefined
     * outside a running live/practice quiz participation.
     */
    getQuizRemainingTimeItem(): InformationBox | undefined {
        const info = this.quizLiveHeaderInfo();
        if (!info?.showRemainingTime) {
            return undefined;
        }
        return {
            title: 'artemisApp.quizExercise.remainingTime',
            content: {
                type: 'string',
                value: info.remainingTimeText ?? '',
            },
            contentColor: info.remainingTimeColor,
        };
    }

    /**
     * Builds the trailing live quiz boxes (currently the "results available" date) from {@link quizLiveHeaderInfo}.
     * Returns an empty array outside a live/practice quiz participation. These render last, through the same
     * information-box loop as the generic boxes, using the existing `dateTime` content type.
     */
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
