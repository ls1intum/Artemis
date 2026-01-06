import { Component, Input, OnChanges, OnInit, inject } from '@angular/core';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';
import { Exercise, IncludedInOverallScore, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { countSubmissions, getExerciseDueDate } from 'app/exercise/util/exercise.utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { InformationBox, InformationBoxComponent } from 'app/shared/information-box/information-box.component';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { isDateLessThanAWeekInTheFuture } from 'app/shared/util/date.utils';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { CommonModule } from '@angular/common';
import { SubmissionResultStatusComponent } from 'app/core/course/overview/submission-result-status/submission-result-status.component';
import { DifficultyLevelComponent } from 'app/exercise/difficulty-level/difficulty-level.component';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';

@Component({
    selector: 'jhi-exercise-headers-information',
    templateUrl: './exercise-headers-information.component.html',
    imports: [SubmissionResultStatusComponent, InformationBoxComponent, DifficultyLevelComponent, ExerciseCategoriesComponent, ArtemisDatePipe, ArtemisTimeAgoPipe, CommonModule],
    styleUrls: ['./exercise-headers-information.component.scss'],
    /* Our tsconfig file has `preserveWhitespaces: 'true'` which causes whitespace to affect content projection.
    We need to set it to 'false 'for this component, otherwise the components with the selector [contentComponent]
    will not be projected into their specific slot of the "InformationBoxComponent" component.*/
    preserveWhitespaces: false,
})
export class ExerciseHeadersInformationComponent implements OnInit, OnChanges {
    private sortService = inject(SortService);
    private serverDateService = inject(ArtemisServerDateService);

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly dayjs = dayjs;

    @Input() exercise: Exercise;
    @Input() studentParticipation?: StudentParticipation;
    @Input() course?: Course;
    @Input() submissionPolicy?: SubmissionPolicy;

    dueDate?: dayjs.Dayjs;
    programmingExercise?: ProgrammingExercise;
    individualComplaintDueDate?: dayjs.Dayjs;
    now: dayjs.Dayjs;
    achievedPoints: number = 0;
    numberOfSubmissions: number;
    informationBoxItems: InformationBox[] = [];

    ngOnInit() {
        this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);
        this.now = this.serverDateService.now();
        const maxComplaintTimeDays = this.course?.complaintConfiguration?.maxComplaintTimeDays;
        if (maxComplaintTimeDays) {
            this.individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(
                this.exercise,
                maxComplaintTimeDays,
                getAllResultsOfAllSubmissions(this.studentParticipation?.submissions).last(),
                this.studentParticipation,
            );
        }
        this.createInformationBoxItems();
    }

    ngOnChanges() {
        this.course = this.course ?? getCourseFromExercise(this.exercise);

        if (this.submissionPolicy?.active && this.submissionPolicy?.submissionLimit) {
            this.updateSubmissionPolicyItem();
        }
        const results = getAllResultsOfAllSubmissions(this.studentParticipation?.submissions);
        if (results.length) {
            // The updated participation by the websocket is not guaranteed to be sorted, find the newest result (highest id)
            this.sortService.sortByProperty(results, 'id', false);

            const latestRatedResult = results.filter((result) => result.rated).first();
            if (latestRatedResult) {
                this.achievedPoints = roundValueSpecifiedByCourseSettings((latestRatedResult.score! * this.exercise.maxPoints!) / 100, this.course) ?? 0;
                this.updatePointsItem();
            }
        }
    }

    createInformationBoxItems() {
        this.addPointsItems();
        this.addDueDateItems();
        this.addStartDateItem();
        this.addSubmissionStatusItem();
        this.addSubmissionPolicyItem();
        this.addDifficultyItem();
        this.addCategoryItems();
    }

    updatePointsItem() {
        const pointsItemIndex = this.informationBoxItems.findIndex((item) => item.title === 'artemisApp.courseOverview.exerciseDetails.points');
        if (pointsItemIndex !== -1) {
            this.informationBoxItems[pointsItemIndex] = this.getPointsItem('points', this.exercise.maxPoints!, this.achievedPoints);
        }
    }

    addPointsItems() {
        const { maxPoints, bonusPoints } = this.exercise;
        if (maxPoints) {
            if (bonusPoints) {
                let achievedBonusPoints: number = 0;
                // If the student has more points than the max points, the bonus points are calculated
                if (this.achievedPoints > maxPoints) {
                    achievedBonusPoints = roundValueSpecifiedByCourseSettings(this.achievedPoints - maxPoints, this.course);
                }
                const achievedPoints = this.achievedPoints - achievedBonusPoints;
                this.informationBoxItems.push(this.getPointsItem('points', maxPoints, achievedPoints));
                this.informationBoxItems.push(this.getPointsItem('bonus', bonusPoints, achievedBonusPoints));
            } else {
                this.informationBoxItems.push(this.getPointsItem('points', maxPoints, this.achievedPoints));
            }
        }
    }

    addDueDateItems() {
        const dueDateItem = this.getDueDateItem();
        if (dueDateItem) {
            this.informationBoxItems.push(dueDateItem);
        }
        // If the due date is in the past and the assessment due date is in the future, show the assessment due date
        if (this.dueDate?.isBefore(this.now) && this.exercise.assessmentDueDate?.isAfter(this.now)) {
            const assessmentDueItem: InformationBox = {
                title: 'artemisApp.courseOverview.exerciseDetails.assessmentDue',
                content: {
                    type: 'dateTime',
                    value: this.exercise.assessmentDueDate,
                },
                isContentComponent: true,
                tooltip: 'artemisApp.courseOverview.exerciseDetails.assessmentDueTooltip',
                tooltipParams: { date: this.exercise.assessmentDueDate.format('lll') },
            };
            this.informationBoxItems.push(assessmentDueItem);
        }
        // // If the assessment due date is in the past and the complaint due date is in the future, show the complaint due date
        if (this.exercise.assessmentDueDate?.isBefore(this.now) && this.individualComplaintDueDate?.isAfter(this.now)) {
            const complaintDueItem: InformationBox = {
                title: 'artemisApp.courseOverview.exerciseDetails.complaintDue',
                content: {
                    type: 'dateTime',
                    value: this.individualComplaintDueDate,
                },
                isContentComponent: true,
                tooltip: 'artemisApp.courseOverview.exerciseDetails.complaintDueTooltip',
                tooltipParams: { date: this.individualComplaintDueDate.format('lll') },
            };
            this.informationBoxItems.push(complaintDueItem);
        }
    }

    getDueDateItem(): InformationBox | undefined {
        if (this.dueDate) {
            const isDueDateInThePast = this.dueDate.isBefore(this.now);
            // If the due date is less than a day away, the color change to red
            const dueDateStatusBadge = this.dueDate.isBetween(this.now.add(1, 'day'), this.now) ? 'danger' : 'body-color';
            // If the due date is less than a week away, text is displayed relatively e.g. 'in 2 days'
            const shouldDisplayDueDateRelative = isDateLessThanAWeekInTheFuture(this.dueDate, this.now);

            if (isDueDateInThePast) {
                return {
                    title: 'artemisApp.courseOverview.exerciseDetails.submissionDueOver',
                    content: {
                        type: 'dateTime',
                        value: this.dueDate,
                    },
                    isContentComponent: true,
                };
            }

            return {
                title: 'artemisApp.courseOverview.exerciseDetails.submissionDue',
                content: {
                    type: shouldDisplayDueDateRelative ? 'timeAgo' : 'dateTime',
                    value: this.dueDate,
                },
                isContentComponent: true,
                tooltip: shouldDisplayDueDateRelative ? 'artemisApp.courseOverview.exerciseDetails.submissionDueTooltip' : undefined,
                contentColor: dueDateStatusBadge,
                tooltipParams: { date: this.dueDate?.format('lll') },
            };
        }
    }

    addStartDateItem() {
        if (this.exercise.startDate && this.now.isBefore(this.exercise.startDate)) {
            // If the start date is less than a week away, text is displayed relatively e.g. 'in 2 days'
            const shouldDisplayStartDateRelative = isDateLessThanAWeekInTheFuture(this.exercise.startDate, this.now);
            const startDateItem: InformationBox = {
                title: 'artemisApp.courseOverview.exerciseDetails.startDate',
                content: {
                    type: shouldDisplayStartDateRelative ? 'timeAgo' : 'dateTime',
                    value: this.exercise.startDate,
                },
                isContentComponent: true,
                tooltip: shouldDisplayStartDateRelative ? 'artemisApp.exerciseActions.startExerciseBeforeStartDate' : undefined,
            };
            this.informationBoxItems.push(startDateItem);
        }
    }

    addDifficultyItem() {
        if (this.exercise.difficulty) {
            const difficultyItem: InformationBox = {
                title: 'artemisApp.courseOverview.exerciseDetails.difficulty',
                content: {
                    type: 'difficultyLevel',
                    value: this.exercise.difficulty,
                },
                isContentComponent: true,
            };
            this.informationBoxItems.push(difficultyItem);
        }
    }

    addSubmissionStatusItem() {
        const submissionStatusItem: InformationBox = {
            title: 'artemisApp.courseOverview.exerciseDetails.status',
            content: {
                type: 'submissionStatus',
                value: this.exercise,
            },
            isContentComponent: true,
        };
        this.informationBoxItems.push(submissionStatusItem);
    }

    addCategoryItems() {
        const notReleased = this.exercise.releaseDate?.isAfter(this.now);

        if (notReleased || this.exercise.includedInOverallScore !== IncludedInOverallScore.INCLUDED_COMPLETELY || this.exercise.categories?.length) {
            const categoryItem: InformationBox = {
                title: 'artemisApp.courseOverview.exerciseDetails.categories',
                content: {
                    type: 'categories',
                    value: this.exercise,
                },
                isContentComponent: true,
            };
            this.informationBoxItems.push(categoryItem);
        }
    }

    addSubmissionPolicyItem() {
        if (this.submissionPolicy?.active && this.submissionPolicy?.submissionLimit) {
            this.informationBoxItems.push(this.getSubmissionPolicyItem());
        }
    }

    getSubmissionPolicyItem(): InformationBox {
        return {
            title: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle',
            content: {
                type: 'string',
                value: `${this.numberOfSubmissions} /  ${this.submissionPolicy?.submissionLimit}`,
            },
            contentColor: this.submissionPolicy?.submissionLimit ? this.getSubmissionColor() : 'body-color',
            tooltip: 'artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.' + this.submissionPolicy?.type + '.tooltip',
            tooltipParams: { points: this.submissionPolicy?.exceedingPenalty?.toString() },
        };
    }

    getSubmissionColor(): string {
        // default color should be 'body-color', thats why the default submissionsLeft is 2
        const submissionsLeft = this.submissionPolicy?.submissionLimit ? this.submissionPolicy?.submissionLimit - this.numberOfSubmissions : 2;
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

    updateSubmissionPolicyItem() {
        this.countSubmissions();

        // need to push and pop the submission policy item to update the number of submissions
        const submissionItemIndex = this.informationBoxItems.findIndex((item) => item.title === 'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle');
        if (submissionItemIndex !== -1) {
            this.informationBoxItems.splice(submissionItemIndex, 1, this.getSubmissionPolicyItem());
        }
    }

    countSubmissions() {
        this.numberOfSubmissions = countSubmissions(this.studentParticipation);
    }
}
