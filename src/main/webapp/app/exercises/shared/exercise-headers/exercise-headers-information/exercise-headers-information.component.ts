import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, IncludedInOverallScore, getCourseFromExercise, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SubmissionType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ExerciseCategoriesModule } from 'app/shared/exercise-categories/exercise-categories.module';
import { InformationBoxComponent } from 'app/shared/information-box/information-box.component';
import { ComplaintService } from 'app/complaints/complaint.service';

export interface InformationBox {
    title: string;
    content: string | number | any;
    contentType?: string;
    contentComponent?: any;
    icon?: IconProp;
    tooltip?: string;
    contentColor?: string;
    tooltipParams?: Record<string, string | undefined>;
}
@Component({
    selector: 'jhi-exercise-headers-information',
    templateUrl: './exercise-headers-information.component.html',
    standalone: true,
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, SubmissionResultStatusModule, ExerciseCategoriesModule, InformationBoxComponent],
    styleUrls: ['./exercise-headers-information.component.scss'],
    // Our tsconfig file has `preserveWhitespaces: 'true'` which causes whitespace to affect content projection.
    // We need to set it to 'false 'for this component, otherwise the components with the selector [contentComponent]
    // will not be projected into their specific slot of the "InformationBoxComponent" component.
    preserveWhitespaces: false,
})
export class ExerciseHeadersInformationComponent implements OnInit, OnChanges {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly AssessmentType = AssessmentType;
    readonly ExerciseType = ExerciseType;
    readonly getIcon = getIcon;
    readonly getIconTooltip = getIconTooltip;
    readonly dayjs = dayjs;

    @Input() exercise: Exercise;
    @Input() studentParticipation?: StudentParticipation;
    @Input() title: string;
    @Input() course?: Course;
    @Input() isTestRun = false;
    @Input() submissionPolicy?: SubmissionPolicy;

    exerciseCategories: ExerciseCategory[];
    dueDate?: dayjs.Dayjs;
    isBeforeStartDate: boolean;
    programmingExercise?: ProgrammingExercise;
    individualComplaintDueDate?: dayjs.Dayjs;
    dueDateStatusBadge?: string;
    achievedPoints?: number;
    numberOfSubmissions: number;
    informationBoxItems: InformationBox[] = [];
    shouldDisplayDueDateRelative = false;

    icon: IconProp;

    constructor(
        private sortService: SortService,
        private translateService: TranslateService,
    ) {}

    ngOnInit() {
        if (this.exercise.type) {
            this.icon = getIcon(this.exercise.type);
        }

        this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);

        if (this.dueDate) {
            // If the due date is less than a day away, the color change to red
            this.dueDateStatusBadge = this.dueDate.isBetween(dayjs().add(1, 'day'), dayjs()) ? 'danger' : 'body-color';
            // If the due date is less than a week away, text is displayed relativley e.g. 'in 2 days'
            this.shouldDisplayDueDateRelative = this.dueDate.isBetween(dayjs().add(1, 'week'), dayjs()) ? true : false;
        }
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
        if (this.dueDate) {
            this.informationBoxItems.push(this.getDueDateItem());
        }
        // If the due date is in the past and the assessment due date is in the future, show the assessment due date
        if (this.dueDate?.isBefore(now) && this.exercise.assessmentDueDate?.isAfter(now)) {
            const assessmentDueItem = {
                title: 'artemisApp.courseOverview.exerciseDetails.assessmentDue',
                content: this.exercise.assessmentDueDate,
                contentComponent: 'dateTime',
                tooltip: 'artemisApp.courseOverview.exerciseDetails.assessmentDueTooltip',
            };
            this.informationBoxItems.push(assessmentDueItem);
        }
        // If the assessment due date is in the past and the complaint due date is in the future, show the complaint due date
        if (this.exercise.assessmentDueDate?.isBefore(now) && this.individualComplaintDueDate?.isAfter(now)) {
            const complaintDueItem = {
                title: 'artemisApp.courseOverview.exerciseDetails.complaintDue',
                content: this.individualComplaintDueDate,
                contentComponent: 'dateTime',
                tooltip: 'artemisApp.courseOverview.exerciseDetails.complaintDueTooltip',
            };
            this.informationBoxItems.push(complaintDueItem);
        }
    }

    getDueDateItem(): InformationBox {
        const isDueDateInThePast = this.dueDate?.isBefore(dayjs());

        if (isDueDateInThePast) {
            return {
                title: 'artemisApp.courseOverview.exerciseDetails.submissionDueOver',
                content: this.dueDate,
                contentComponent: 'dateTime',
            };
        }

        return {
            title: 'artemisApp.courseOverview.exerciseDetails.submissionDue',
            content: this.dueDate,
            contentComponent: this.shouldDisplayDueDateRelative ? 'timeAgo' : 'dateTime',
            tooltip: this.shouldDisplayDueDateRelative ? 'artemisApp.courseOverview.exerciseDetails.submissionDueTooltip' : undefined,
            contentColor: this.dueDateStatusBadge,
            tooltipParams: { date: this.dueDate?.format('lll') },
        };
    }

    addStartDateItem() {
        if (this.exercise.startDate && dayjs().isBefore(this.exercise.startDate)) {
            const startDateItem = {
                title: 'artemisApp.courseOverview.exerciseDetails.startDate',
                //  less than a week make time relative to now
                content: this.exercise.startDate,
                contentComponent: 'dateTime',
                tooltip: this.shouldDisplayDueDateRelative ? 'artemisApp.exerciseActions.startExerciseBeforeStartDate' : undefined,
            };
            this.informationBoxItems.push(startDateItem);
        }
    }

    addDifficultyItem() {
        const difficultyItem = {
            title: 'artemisApp.courseOverview.exerciseDetails.difficulty',
            content: this.exercise.difficulty,
            contentComponent: 'difficultyLevel',
        };
        this.informationBoxItems.push(difficultyItem);
    }

    addSubmissionStatusItem() {
        const submissionStatusItem = {
            title: 'artemisApp.courseOverview.exerciseDetails.status',
            content: this.studentParticipation,
            contentComponent: 'submissionStatus',
        };
        this.informationBoxItems.push(submissionStatusItem);
    }
    addCategoryItems() {
        const notReleased = this.exercise.releaseDate && dayjs(this.exercise.releaseDate).isAfter(dayjs());

        if (notReleased || this.exercise.includedInOverallScore !== IncludedInOverallScore.INCLUDED_COMPLETELY || this.exercise.categories?.length) {
            const categoryItem = {
                title: 'artemisApp.courseOverview.exerciseDetails.categories',
                content: this.exercise,
                contentComponent: 'categories',
            };
            this.informationBoxItems.push(categoryItem);
        }
    }

    addSubmissionPolicyItem() {
        if (this.submissionPolicy?.active && this.submissionPolicy?.submissionLimit) {
            this.informationBoxItems.push(this.getSubmissionPolicyItem());
        }
    }

    getSubmissionPolicyItem() {
        return {
            title: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle',
            content: this.numberOfSubmissions + ' / ' + this.submissionPolicy?.submissionLimit,

            contentColor: this.submissionPolicy?.submissionLimit ? this.getSubmissionColor() : 'body-color',
            // content:
            //     this.numberOfSubmissions +
            //     '/' +
            //     this.submissionPolicy?.submissionLimit +
            //     (this.submissionPolicy?.exceedingPenalty
            //         ? ' ' + this.translateService.instant('artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInfoLabel', {
            //               points: this.submissionPolicy.exceedingPenalty,
            //           })
            //         : ''),
            tooltip: 'artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.' + this.submissionPolicy?.type + '.tooltip',
            tooltipParams: { points: this.submissionPolicy?.exceedingPenalty?.toString() },
        };
    }

    getSubmissionColor() {
        const submissionsLeft = this.submissionPolicy?.submissionLimit ? this.submissionPolicy?.submissionLimit - this.numberOfSubmissions : 2;
        if (submissionsLeft > 1) return 'body-color';
        else {
            return submissionsLeft <= 0 ? 'danger' : 'warning';
        }
    }
    getPointsItem(points: number | undefined, title: string): InformationBox {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.' + title,
            content: this.achievedPoints !== undefined ? this.achievedPoints + ' / ' + points : '0 / ' + points,
        };
    }

    updateSubmissionPolicyItem() {
        if (this.submissionPolicy?.active && this.submissionPolicy?.submissionLimit) {
            this.countSubmissions();

            // need to push and pop the submission policy item to update the number of submissions
            const submissionItemIndex = this.informationBoxItems.findIndex((item) => item.title === 'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle');
            if (submissionItemIndex !== -1) {
                this.informationBoxItems.splice(submissionItemIndex, 1, this.getSubmissionPolicyItem());
            }
            // if (this.submissionPolicy?.exceedingPenalty) {
            //     this.informationBoxItems.push(this.getExceedingPenalty());
            // }
        }
    }

    ngOnChanges() {
        this.course = this.course ?? getCourseFromExercise(this.exercise);

        this.updateSubmissionPolicyItem();

        if (this.studentParticipation?.results?.length) {
            // The updated participation by the websocket is not guaranteed to be sorted, find the newest result (highest id)
            this.sortService.sortByProperty(this.studentParticipation.results, 'id', false);

            const latestRatedResult = this.studentParticipation.results.filter((result) => result.rated).first();
            if (latestRatedResult) {
                this.achievedPoints = roundValueSpecifiedByCourseSettings((latestRatedResult.score! * this.exercise.maxPoints!) / 100, this.course);
            }
        }
    }

    private countSubmissions() {
        const commitHashSet = new Set<string>();

        this.studentParticipation?.results
            ?.map((result) => result.submission)
            .filter((submission) => submission?.type === SubmissionType.MANUAL)
            .map((submission) => (submission as ProgrammingSubmission).commitHash)
            .forEach((commitHash: string) => commitHashSet.add(commitHash));

        this.numberOfSubmissions = commitHashSet.size;
    }
}
