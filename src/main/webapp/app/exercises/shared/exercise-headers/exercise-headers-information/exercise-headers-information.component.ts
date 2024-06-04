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
// import { AssessmentType } from 'app/entities/assessment-type.model';
import { SubmissionType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ExerciseCategoriesModule } from 'app/shared/exercise-categories/exercise-categories.module';

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
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, SubmissionResultStatusModule, ExerciseCategoriesModule],
    styleUrls: ['./exercise-headers-information.component.scss'],
    // Our tsconfig file has `preserveWhitespaces: 'true'` which causes whitespace to affect content projection.
    // We need to set it to 'false 'for this component, otherwise the components with the selector [contentComponent]
    // will not be projected into their specific slot of the "InformationBoxComponent" component.
    preserveWhitespaces: false,
})
export class ExerciseHeadersInformationComponent implements OnInit, OnChanges {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    // readonly AssessmentType = AssessmentType;
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
    // public nextRelevantDate?: dayjs.Dayjs;
    // public nextRelevantDateLabel?: string;
    // public nextRelevantDateStatusBadge?: string;
    dueDateStatusBadge?: string;
    canComplainLaterOn: boolean;
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
        // this.exerciseCategories = this.exercise.categories || [];

        if (this.exercise.type) {
            this.icon = getIcon(this.exercise.type);
        }

        this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);
        //     this.isBeforeStartDate = this.exercise.startDate ? this.exercise.startDate.isAfter(dayjs()) : !!this.exercise.releaseDate?.isAfter(dayjs());
        //     if (this.course?.maxComplaintTimeDays) {
        //         this.individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(
        //             this.exercise,
        //             this.course.maxComplaintTimeDays,
        //             this.studentParticipation?.results?.last(),
        //             this.studentParticipation,
        //         );
        //     }
        //     // There is a submission where the student did not have the chance to complain yet
        //     this.canComplainLaterOn =
        //         !!this.studentParticipation?.submissionCount &&
        //         !this.individualComplaintDueDate &&
        //         (this.exercise.allowComplaintsForAutomaticAssessments || this.exercise.assessmentType !== AssessmentType.AUTOMATIC);

        if (this.dueDate) {
            // If the due date is less than a day away, the color change to red
            this.dueDateStatusBadge = this.dueDate.isBetween(dayjs().add(1, 'day'), dayjs()) ? 'danger' : 'body-color';
            // If the due date is less than a week away, text is displayed relativley e.g. 'in 2 days'
            this.shouldDisplayDueDateRelative = this.dueDate.isBetween(dayjs().add(1, 'week'), dayjs()) ? true : false;
        }
        this.createInformationBoxItems();
    }

    createInformationBoxItems() {
        const notReleased = this.exercise.releaseDate && dayjs(this.exercise.releaseDate).isAfter(dayjs());
        if (this.exercise.maxPoints) this.informationBoxItems.push(this.getPointsItem(this.exercise.maxPoints, 'points'));
        if (this.exercise.bonusPoints) this.informationBoxItems.push(this.getPointsItem(this.exercise.bonusPoints, 'bonus'));

        if (this.exercise.dueDate) this.informationBoxItems.push(this.getDueDateItem());
        this.informationBoxItems.push(this.getDifficultyItem());
        // (exercise.releaseDate && dayjs(exercise.releaseDate).isAfter(dayjs()))
        if (notReleased || this.exercise.includedInOverallScore !== IncludedInOverallScore.INCLUDED_COMPLETELY || this.exercise.categories?.length)
            this.informationBoxItems.push(this.getCategoryItems());
        // this.informationBoxItems.push(this.getNextRelevantDateItem());
        // if (this.submissionPolicy?.active) this.informationBoxItems.push(this.getSubmissionPolicyItem());
        this.informationBoxItems.push(this.getSubmissionStatusItem());

        // if (this.exercise.assessmentType && this.exercise.type === ExerciseType.PROGRAMMING) this.informationBoxItems.push(this.getAssessmentTypeItem());
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
            //  less than a week make time relative to now
            content: this.dueDate,
            contentComponent: this.shouldDisplayDueDateRelative ? 'timeAgo' : 'dateTime',
            tooltip: this.shouldDisplayDueDateRelative ? 'artemisApp.courseOverview.exerciseDetails.submissionDueTooltip' : undefined,
            contentColor: this.dueDateStatusBadge,
            tooltipParams: { date: this.dueDate?.format('lll') },
        };
    }
    //  Status: Not released, no graded, graded, submitted, reviewed, assessed, complaint, complaint response, complaint applied, complaint resolved
    // getStatusItem(): InformationBox {

    // }

    // getAssessmentTypeItem(): InformationBox {
    //     return {
    //         title: 'artemisApp.courseOverview.exerciseDetails.assessmentType',
    //         content: this.capitalize(this.exercise?.assessmentType),
    //         tooltip: 'artemisApp.AssessmentType.tooltip.' + this.exercise.assessmentType,
    //     };
    // }

    capitalize(title?: string) {
        if (!title) return '-';
        return title.toString().charAt(0).toUpperCase() + title.slice(1).toLowerCase();
    }
    getDifficultyItem(): InformationBox {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.difficulty',
            content: this.exercise.difficulty,
            contentComponent: 'difficultyLevel',
        };
    }
    getSubmissionStatusItem(): InformationBox {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.submissionStatus',
            content: this.studentParticipation,
            contentComponent: 'submissionStatus',
        };
    }
    getCategoryItems(): InformationBox {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.categories',
            content: this.exercise,
            contentComponent: 'categories',
        };
    }

    getSubmissionPolicyItem(): InformationBox {
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

    // Can be visible in the tooltip above a status
    // getNextRelevantDateItem(): InformationBox {
    //     console.log('get Next Relevant Date Item')
    //     console.log(this.nextRelevantDateLabel)
    //     // {{ 'artemisApp.courseOverview.exerciseDetails.' + nextRelevantDateLabel | artemisTranslate }}
    //     return {
    //         title: this.nextRelevantDateLabel ? this.nextRelevantDateLabel : 'Next Relevant Date',
    //         content: this.nextRelevantDate?.format('lll') ?? '-',
    //         icon: faQuestionCircle,
    //     };

    // }

    // separate Points and Bonus Points
    // DO one function with input
    getPointsItem(points: number | undefined, title: string): InformationBox {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.' + title,
            content: this.achievedPoints !== undefined ? this.achievedPoints + ' / ' + points : '0 / ' + points,
        };
    }

    // getDefaultItems(): InformationBox[] {
    //     const exercisesItem: InformationBox = {
    //         title: `${this.baseResource}`,
    //         icon: faEye,
    //         content: 'entity.action.view',
    //     };

    //     const statisticsItem: InformationBox = {
    //         routerLink: `${this.baseResource}scores`,
    //         icon: faTable,
    //         translation: 'entity.action.scores',
    //     };

    //     return [exercisesItem, statisticsItem];
    // }

    // Check what I really need

    ngOnChanges() {
        this.course = this.course ?? getCourseFromExercise(this.exercise);

        if (this.submissionPolicy?.active && this.submissionPolicy?.submissionLimit) {
            console.log('Changes Submission');
            this.countSubmissions();
            // need to push and pop the submission policy item to update the number of submissions
            this.informationBoxItems.push(this.getSubmissionPolicyItem());
            // if (this.submissionPolicy?.exceedingPenalty) {
            //     this.informationBoxItems.push(this.getExceedingPenalty());
            // }
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
