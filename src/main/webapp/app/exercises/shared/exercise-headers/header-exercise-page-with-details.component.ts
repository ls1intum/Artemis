import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, IncludedInOverallScore, getCourseFromExercise, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { Exam } from 'app/entities/exam/exam.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { SubmissionType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming/programming-submission.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { TranslateService } from '@ngx-translate/core';

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
    selector: 'jhi-header-exercise-page-with-details',
    templateUrl: './header-exercise-page-with-details.component.html',
    styleUrls: ['./header-exercise-page-with-details.component.scss'],
    // Our tsconfig file has `preserveWhitespaces: 'true'` which causes whitespace to affect content projection.
    // We need to set it to 'false 'for this component, otherwise the components with the selecotor [contentComponent]
    // will not be projected into their specific slot of the "InformationBoxComponent" component.
    preserveWhitespaces: false,
})
export class HeaderExercisePageWithDetailsComponent implements OnChanges, OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly AssessmentType = AssessmentType;
    readonly ExerciseType = ExerciseType;
    readonly getIcon = getIcon;
    readonly getIconTooltip = getIconTooltip;
    readonly dayjs = dayjs;

    @Input() public exercise: Exercise;
    @Input() public studentParticipation?: StudentParticipation;
    @Input() public title: string;
    @Input() public exam?: Exam;
    @Input() public course?: Course;
    @Input() public isTestRun = false;
    @Input() public submissionPolicy?: SubmissionPolicy;

    public exerciseCategories: ExerciseCategory[];
    public dueDate?: dayjs.Dayjs;
    public isBeforeStartDate: boolean;
    public programmingExercise?: ProgrammingExercise;
    public individualComplaintDueDate?: dayjs.Dayjs;
    public nextRelevantDate?: dayjs.Dayjs;
    public nextRelevantDateLabel?: string;
    public nextRelevantDateStatusBadge?: string;
    public dueDateStatusBadge?: string;
    public canComplainLaterOn: boolean;
    public achievedPoints?: number;
    public numberOfSubmissions: number;
    public informationBoxItems: InformationBox[] = [];
    public shouldDisplayDueDateRelative = false;

    icon: IconProp;

    // Icons
    faQuestionCircle = faQuestionCircle;

    constructor(
        private sortService: SortService,
        private translateService: TranslateService,
    ) {}

    ngOnInit() {
        this.exerciseCategories = this.exercise.categories || [];

        if (this.exercise.type) {
            this.icon = getIcon(this.exercise.type);
        }

        this.programmingExercise = this.exercise.type === ExerciseType.PROGRAMMING ? (this.exercise as ProgrammingExercise) : undefined;

        if (this.exam) {
            this.determineNextRelevantDateExamMode();
        } else {
            this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);
            this.isBeforeStartDate = this.exercise.startDate ? this.exercise.startDate.isAfter(dayjs()) : !!this.exercise.releaseDate?.isAfter(dayjs());
            if (this.course?.maxComplaintTimeDays) {
                this.individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(
                    this.exercise,
                    this.course.maxComplaintTimeDays,
                    this.studentParticipation?.results?.last(),
                    this.studentParticipation,
                );
            }
            // There is a submission where the student did not have the chance to complain yet
            this.canComplainLaterOn =
                !!this.studentParticipation?.submissionCount &&
                !this.individualComplaintDueDate &&
                (this.exercise.allowComplaintsForAutomaticAssessments || this.exercise.assessmentType !== AssessmentType.AUTOMATIC);

            this.determineNextRelevantDateCourseMode();
        }

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
        if (this.exercise.maxPoints) this.informationBoxItems.push(this.getMaxPointsItem());
        if (this.exercise.bonusPoints) this.informationBoxItems.push(this.getBonusPointsItem());

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

    getAssessmentTypeItem(): InformationBox {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.assessmentType',
            content: this.capitalize(this.exercise?.assessmentType),
            tooltip: 'artemisApp.AssessmentType.tooltip.' + this.exercise.assessmentType,
        };
    }

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
    getMaxPointsItem(): InformationBox {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.points',
            content: this.achievedPoints !== undefined ? this.achievedPoints + ' / ' + this.exercise.maxPoints : '0 / ' + this.exercise.maxPoints,
        };
    }

    getBonusPointsItem(): InformationBox {
        return {
            title: 'artemisApp.courseOverview.exerciseDetails.bonus',
            content: this.achievedPoints !== undefined ? this.achievedPoints + ' / ' + this.exercise.bonusPoints : '0 / ' + this.exercise.bonusPoints,
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

    ngOnChanges() {
        this.course = this.course ?? getCourseFromExercise(this.exercise);

        if (this.submissionPolicy?.active && this.submissionPolicy?.submissionLimit) {
            console.log('Changes Submission');
            this.countSubmissions();
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

    /**
     * Determines the next date of the exam cycle. If none exists the latest date in the past is determined
     */
    private determineNextRelevantDateExamMode() {
        const possibleDates = [this.exam?.endDate, this.exam?.publishResultsDate];
        const possibleDatesLabels = ['endDate', 'publishResultsDate'];

        this.determineNextDate(possibleDates, possibleDatesLabels, dayjs());
    }

    /**
     * Determines the next date of the course exercise cycle. If none exists the latest date in the past is determined
     */
    private determineNextRelevantDateCourseMode() {
        console.log('Hi');
        const possibleDates = [this.exercise.releaseDate, this.exercise.startDate, this.exercise.assessmentDueDate, this.individualComplaintDueDate];
        const possibleDatesLabels = ['releaseDate', 'startDate', 'assessmentDue', 'complaintDue'];

        this.determineNextDate(possibleDates, possibleDatesLabels, dayjs());
    }

    /**
     * Iterates over the given dates and determines the first date that is in the future.
     * If no such date exists, it is determined if the student can complaint later on.
     * If that is also not the case, the latest date in the past is chosen that is after the due date.
     * @param dates that should be iterated over. Can contain undefined if that date does not exist
     * @param dateLabels the labels used to translate the given dates
     * @param now the current date and time
     */
    private determineNextDate(dates: (dayjs.Dayjs | undefined)[], dateLabels: string[], now: dayjs.Dayjs) {
        this.nextRelevantDate = undefined;
        this.nextRelevantDateLabel = undefined;
        this.nextRelevantDateStatusBadge = undefined;
        console.log('Determine Next Date');
        console.log(dates);
        console.log(dateLabels);
        for (let i = 0; i < dates.length; i++) {
            if (dates[i] && now.isBefore(dates[i])) {
                console.log('If Satetment');
                this.nextRelevantDate = dates[i]!;
                this.nextRelevantDateLabel = dateLabels[i];
                this.nextRelevantDateStatusBadge = 'bg-success';
                return;
            }
        }

        console.log(this.nextRelevantDateLabel);
        if (this.canComplainLaterOn) {
            return;
        }
        for (let i = dates.length - 1; i >= 0; i--) {
            console.log(i);
            if (dates[i]) {
                console.log(i);
                console.log('If Satetment3');
                if (this.dueDate && this.dueDate.isAfter(dates[i])) {
                    console.log('If Satetment2');
                    console.log(this.nextRelevantDateLabel);
                    return;
                }

                this.nextRelevantDate = dates[i]!;
                this.nextRelevantDateLabel = dateLabels[i];
                this.nextRelevantDateStatusBadge = 'bg-danger';
                return;
            }
        }
        console.log('Haaaallo');
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
