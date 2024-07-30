import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';
import { Exercise, IncludedInOverallScore, getCourseFromExercise } from 'app/entities/exercise.model';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { SubmissionType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ExerciseCategoriesModule } from 'app/shared/exercise-categories/exercise-categories.module';
import { InformationBox, InformationBoxComponent } from 'app/shared/information-box/information-box.component';
import { ComplaintService } from 'app/complaints/complaint.service';
import { isDateLessThanAWeekAway } from 'app/utils/date.utils';
import { DifficultyLevelComponent } from 'app/shared/difficulty-level/difficulty-level.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-exercise-headers-information',
    templateUrl: './exercise-headers-information.component.html',
    standalone: true,
    imports: [SubmissionResultStatusModule, ExerciseCategoriesModule, InformationBoxComponent, DifficultyLevelComponent, ArtemisSharedCommonModule],
    styleUrls: ['./exercise-headers-information.component.scss'],
    /* Our tsconfig file has `preserveWhitespaces: 'true'` which causes whitespace to affect content projection.
    We need to set it to 'false 'for this component, otherwise the components with the selector [contentComponent]
    will not be projected into their specific slot of the "InformationBoxComponent" component.*/
    preserveWhitespaces: false,
})
export class ExerciseHeadersInformationComponent implements OnInit, OnChanges {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly dayjs = dayjs;

    @Input() exercise: Exercise;
    @Input() studentParticipation?: StudentParticipation;
    @Input() course?: Course;
    @Input() submissionPolicy?: SubmissionPolicy;

    dueDate?: dayjs.Dayjs;
    programmingExercise?: ProgrammingExercise;
    individualComplaintDueDate?: dayjs.Dayjs;
    achievedPoints?: number;
    numberOfSubmissions: number;
    informationBoxItems: InformationBox[] = [];

    constructor(private sortService: SortService) {}

    ngOnInit() {
        this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);

        if (this.course?.maxComplaintTimeDays) {
            this.individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(
                this.exercise,
                this.course.maxComplaintTimeDays,
                this.studentParticipation?.results?.last(),
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
        if (this.studentParticipation?.results?.length) {
            // The updated participation by the websocket is not guaranteed to be sorted, find the newest result (highest id)
            this.sortService.sortByProperty(this.studentParticipation.results, 'id', false);

            const latestRatedResult = this.studentParticipation.results.filter((result) => result.rated).first();
            if (latestRatedResult) {
                this.achievedPoints = roundValueSpecifiedByCourseSettings((latestRatedResult.score! * this.exercise.maxPoints!) / 100, this.course);
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

    addPointsItems() {
        const { maxPoints, bonusPoints } = this.exercise;
        if (maxPoints) {
            this.informationBoxItems.push(this.getPointsItem(maxPoints, 'points'));
        }
        if (bonusPoints) {
            this.informationBoxItems.push(this.getPointsItem(bonusPoints, 'bonus'));
        }
    }

    addDueDateItems() {
        const now = dayjs();

        const dueDateItem = this.getDueDateItem();
        if (dueDateItem) {
            this.informationBoxItems.push(dueDateItem);
        }
        // If the due date is in the past and the assessment due date is in the future, show the assessment due date
        if (this.dueDate?.isBefore(now) && this.exercise.assessmentDueDate?.isAfter(now)) {
            const assessmentDueItem: InformationBox = {
                title: 'artemisApp.courseOverview.exerciseDetails.assessmentDue',
                content: {
                    type: 'dateTime',
                    value: this.exercise.assessmentDueDate,
                },
                isContentComponent: true,
                tooltip: 'artemisApp.courseOverview.exerciseDetails.assessmentDueTooltip',
            };
            this.informationBoxItems.push(assessmentDueItem);
        }
        // // If the assessment due date is in the past and the complaint due date is in the future, show the complaint due date
        if (this.exercise.assessmentDueDate?.isBefore(now) && this.individualComplaintDueDate?.isAfter(now)) {
            const complaintDueItem: InformationBox = {
                title: 'artemisApp.courseOverview.exerciseDetails.complaintDue',
                content: {
                    type: 'dateTime',
                    value: this.individualComplaintDueDate,
                },
                isContentComponent: true,
                tooltip: 'artemisApp.courseOverview.exerciseDetails.complaintDueTooltip',
            };
            this.informationBoxItems.push(complaintDueItem);
        }
    }

    getDueDateItem(): InformationBox | undefined {
        if (this.dueDate) {
            const isDueDateInThePast = this.dueDate.isBefore(dayjs());
            // If the due date is less than a day away, the color change to red
            const dueDateStatusBadge = this.dueDate.isBetween(dayjs().add(1, 'day'), dayjs()) ? 'danger' : 'body-color';
            // If the due date is less than a week away, text is displayed relatively e.g. 'in 2 days'
            const shouldDisplayDueDateRelative = isDateLessThanAWeekAway(this.dueDate);

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
        if (this.exercise.startDate && dayjs().isBefore(this.exercise.startDate)) {
            // If the start date is less than a week away, text is displayed relatively e.g. 'in 2 days'
            const shouldDisplayStartDateRelative = isDateLessThanAWeekAway(this.exercise.startDate);
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
        const notReleased = this.exercise.releaseDate && dayjs(this.exercise.releaseDate).isAfter(dayjs());

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
        const submissionsLeft = this.submissionPolicy?.submissionLimit ? this.submissionPolicy?.submissionLimit - this.numberOfSubmissions : 2;
        if (submissionsLeft > 1) return 'body-color';
        else {
            return submissionsLeft <= 0 ? 'danger' : 'warning';
        }
    }

    getPointsItem(points: number | undefined, title: string): InformationBox {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.' + title,
            content: {
                type: 'string',
                value: this.achievedPoints ? `${this.achievedPoints} / ${points}` : `0 / ${points}`,
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
        const commitHashSet = new Set<string>();

        this.studentParticipation?.results
            ?.map((result) => result.submission)
            .filter((submission) => submission?.type === SubmissionType.MANUAL)
            .map((submission) => (submission as ProgrammingSubmission).commitHash)
            .forEach((commitHash: string) => commitHashSet.add(commitHash));

        this.numberOfSubmissions = commitHashSet.size;
    }
}
