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

@Component({
    selector: 'jhi-header-exercise-page-with-details',
    templateUrl: './header-exercise-page-with-details.component.html',
    styleUrls: ['./header-exercise-page-with-details.component.scss'],
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

    icon: IconProp;

    // Icons
    faQuestionCircle = faQuestionCircle;

    constructor(private sortService: SortService) {}

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
            this.dueDateStatusBadge = dayjs().isBefore(this.dueDate) ? 'bg-success' : 'bg-danger';
        }
    }

    ngOnChanges() {
        this.course = this.course ?? getCourseFromExercise(this.exercise);

        if (this.submissionPolicy?.active) {
            this.countSubmissions();
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

        for (let i = 0; i < dates.length; i++) {
            if (dates[i] && now.isBefore(dates[i])) {
                this.nextRelevantDate = dates[i]!;
                this.nextRelevantDateLabel = dateLabels[i];
                this.nextRelevantDateStatusBadge = 'bg-success';
                return;
            }
        }
        if (this.canComplainLaterOn) {
            return;
        }
        for (let i = dates.length - 1; i >= 0; i--) {
            if (dates[i]) {
                if (this.dueDate && this.dueDate.isAfter(dates[i])) {
                    return;
                }

                this.nextRelevantDate = dates[i]!;
                this.nextRelevantDateLabel = dateLabels[i];
                this.nextRelevantDateStatusBadge = 'bg-danger';
                return;
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
