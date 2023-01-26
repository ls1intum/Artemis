import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, IncludedInOverallScore, getCourseFromExercise, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { Exam } from 'app/entities/exam.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { SubmissionType } from 'app/entities/submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';

export enum NextDate {
    START_OR_RELEASE_DATE = 'START_OR_RELEASE_DATE',
    SUBMISSION_DUE_DATE = 'SUBMISSION_DUE_DATE',
    ASSESSMENT_DUE_DATE = 'ASSESSMENT_DUE_DATE',
    COMPLAINT = 'COMPLAINT',
    EXAM_END_DATE = 'EXAM_END_DATE',
    RESULT_PUBLISH_DATE = 'RESULT_PUBLISH_DATE',
    NONE = 'NONE',
}

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
    readonly NextDate = NextDate;

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
    public individualComplaintDeadline?: dayjs.Dayjs;
    public nextDueDate = NextDate.NONE;
    public statusBadges: string[];
    public canComplainLaterOn: boolean;
    public achievedPoints?: number;
    public numberOfSubmissions: number;

    icon: IconProp;

    // Icons
    faQuestionCircle = faQuestionCircle;

    constructor(private sortService: SortService) {}

    ngOnInit() {
        this.exerciseCategories = this.exercise.categories || [];

        this.setIcon(this.exercise.type);

        this.programmingExercise = this.exercise.type === ExerciseType.PROGRAMMING ? (this.exercise as ProgrammingExercise) : undefined;

        if (this.exam) {
            this.setIsNextDueDateExamMode();
        } else {
            this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);
            this.isBeforeStartDate = this.exercise.startDate ? this.exercise.startDate.isAfter(dayjs()) : !!this.exercise.releaseDate?.isAfter(dayjs());
            if (this.course?.maxComplaintTimeDays) {
                this.individualComplaintDeadline = ComplaintService.getIndividualComplaintDueDate(
                    this.exercise,
                    this.course.maxComplaintTimeDays,
                    this.studentParticipation?.results?.last(),
                    this.studentParticipation,
                );
            }
            // The student can either still submit or there is a submission where the student did not have the chance to complain yet
            this.canComplainLaterOn =
                (dayjs().isBefore(this.exercise.dueDate) ||
                    (this.studentParticipation?.individualDueDate && dayjs().isBefore(this.studentParticipation.individualDueDate)) ||
                    (!!this.studentParticipation?.submissionCount && !this.individualComplaintDeadline)) &&
                (this.exercise.allowComplaintsForAutomaticAssessments || this.exercise.assessmentType !== AssessmentType.AUTOMATIC);

            this.setIsNextDueDateCourseMode();
        }
    }

    ngOnChanges() {
        this.course = this.course ?? getCourseFromExercise(this.exercise);

        if (this.submissionPolicy) {
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

    private setIcon(exerciseType?: ExerciseType) {
        if (exerciseType) {
            this.icon = getIcon(exerciseType);
        }
    }

    /**
     * Determines what element of the header should be highlighted. The highlighted deadline/time is the one being due next
     * Array for badge class (= statusBadges) consist of
     * 0: Exam End Date
     * 1: Publish Results Date
     */
    private setIsNextDueDateExamMode() {
        const now = dayjs();
        if (this.exam?.endDate && now.isBefore(this.exam?.endDate)) {
            this.nextDueDate = NextDate.EXAM_END_DATE;
            this.statusBadges = ['bg-success', 'bg-success'];
        } else if (this.exam?.publishResultsDate && now.isBefore(this.exam?.publishResultsDate)) {
            this.nextDueDate = NextDate.RESULT_PUBLISH_DATE;
            this.statusBadges = ['bg-danger', 'bg-success'];
        } else {
            this.nextDueDate = NextDate.NONE;
            this.statusBadges = ['bg-danger', 'bg-danger'];
        }
    }

    /**
     * Determines what element of the header should be highlighted. The highlighted deadline/time is the one being due next
     * Array for badge class (= statusBadges) consist of
     * 0: Start Date (either release date or start date if set)
     * 1: Submission Due Date
     * 2: Assessment Due Date
     * 3: Individual Complaint Deadline
     * 4: Complaint Possible (Yes / No)
     */
    private setIsNextDueDateCourseMode() {
        const now = dayjs();
        if (this.isBeforeStartDate) {
            this.nextDueDate = NextDate.START_OR_RELEASE_DATE;
            this.statusBadges = ['bg-success', 'bg-success', 'bg-success', 'bg-success'];
        } else if (this.dueDate && now.isBefore(this.dueDate)) {
            this.nextDueDate = NextDate.SUBMISSION_DUE_DATE;
            this.statusBadges = ['bg-danger', 'bg-success', 'bg-success', 'bg-success'];
        } else if (this.exercise.assessmentDueDate && now.isBefore(this.exercise.assessmentDueDate)) {
            this.nextDueDate = NextDate.ASSESSMENT_DUE_DATE;
            this.statusBadges = ['bg-danger', 'bg-danger', 'bg-success', 'bg-success'];
        } else if (this.individualComplaintDeadline && now.isBefore(this.individualComplaintDeadline)) {
            this.nextDueDate = NextDate.COMPLAINT;
            this.statusBadges = ['bg-danger', 'bg-danger', 'bg-danger', 'bg-success'];
        } else if (this.canComplainLaterOn) {
            this.nextDueDate = NextDate.COMPLAINT;
            this.statusBadges = ['bg-danger', 'bg-danger', 'bg-danger', 'bg-danger'];
        } else {
            this.nextDueDate = NextDate.NONE;
            this.statusBadges = ['bg-danger', 'bg-danger', 'bg-danger', 'bg-danger'];
        }
    }

    private countSubmissions() {
        let submissionCompensation = 0;
        if (this.studentParticipation?.submissions?.length) {
            submissionCompensation = !this.studentParticipation.submissions.first()?.results?.length ? 1 : 0;
        }

        const commitHashSet = new Set<string>();
        this.studentParticipation?.submissions
            ?.filter((submission) => submission.type === SubmissionType.MANUAL && submission.results?.length)
            .map((submission) => (submission as ProgrammingSubmission).commitHash)
            .forEach((commitHash: string) => commitHashSet.add(commitHash));

        this.numberOfSubmissions = submissionCompensation + commitHashSet.size;
    }
}
