import { Component, Input, OnChanges, OnInit } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, getCourseFromExercise, getIcon, getIconTooltip, IncludedInOverallScore } from 'app/entities/exercise.model';
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
    public programmingExercise?: ProgrammingExercise;
    public individualComplaintDeadline?: dayjs.Dayjs;
    public isNextDueDate: boolean[];
    public statusBadges: string[];
    public canComplainLaterOn: boolean;
    public achievedPoints?: number;
    public numberOfSubmissions: number;

    icon: IconProp;

    // Icons
    faQuestionCircle = faQuestionCircle;

    constructor(private complaintService: ComplaintService) {}

    ngOnInit(): void {
        this.exerciseCategories = this.exercise.categories || [];

        this.setIcon(this.exercise.type);

        this.programmingExercise = this.exercise.type === ExerciseType.PROGRAMMING ? (this.exercise as ProgrammingExercise) : undefined;
        this.course = this.course ?? getCourseFromExercise(this.exercise);

        if (this.exam) {
            this.setIsNextDueDateExamMode();
        } else {
            this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);
            if (this.course) {
                this.individualComplaintDeadline = this.complaintService.getIndividualComplaintDueDate(this.exercise, this.course, this.studentParticipation);
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

    ngOnChanges(): void {
        if (this.submissionPolicy) {
            this.countSubmissions();
        }
        if (this.studentParticipation?.results?.[0].rated) {
            this.achievedPoints = roundValueSpecifiedByCourseSettings((this.studentParticipation?.results?.[0].score! * this.exercise.maxPoints!) / 100, this.course);
        }
    }

    private setIcon(exerciseType?: ExerciseType) {
        if (exerciseType) {
            this.icon = getIcon(exerciseType);
        }
    }

    /**
     * Determines what element of the header should be highlighted. The highlighted deadline/time is the one being due next
     * Arrays (for badge class (= statusBadges) and highlighting (= isNextDueDate)) consist of
     * 0: Exam End Date
     * 1: Publish Results Date
     */
    private setIsNextDueDateExamMode() {
        this.isNextDueDate = [false, false];
        const now = dayjs();
        if (this.exam?.endDate && now.isBefore(this.exam?.endDate)) {
            this.isNextDueDate[0] = true;
            this.statusBadges = ['bg-success', 'bg-success'];
        } else if (this.exam?.publishResultsDate && now.isBefore(this.exam?.publishResultsDate)) {
            this.isNextDueDate[1] = true;
            this.statusBadges = ['bg-danger', 'bg-success'];
        } else {
            this.statusBadges = ['bg-danger', 'bg-danger'];
        }
    }

    /**
     * Determines what element of the header should be highlighted. The highlighted deadline/time is the one being due next
     * Arrays (for badge class (= statusBadges) and highlighting (= isNextDueDate)) consist of
     * 0: Submission Due Date
     * 1: Assessment Due Date
     * 2: Individual Complaint Deadline
     * 3: Complaint Possible (Yes / No)
     */
    private setIsNextDueDateCourseMode() {
        this.isNextDueDate = [false, false, false, false];
        const now = dayjs();
        if (this.dueDate && now.isBefore(this.dueDate)) {
            this.isNextDueDate[0] = true;
            this.statusBadges = ['bg-success', 'bg-success', 'bg-success'];
        } else if (this.exercise.assessmentDueDate && now.isBefore(this.exercise.assessmentDueDate)) {
            this.isNextDueDate[1] = true;
            this.statusBadges = ['bg-danger', 'bg-success', 'bg-success'];
        } else if (this.individualComplaintDeadline && now.isBefore(this.individualComplaintDeadline)) {
            this.isNextDueDate[2] = true;
            this.statusBadges = ['bg-danger', 'bg-danger', 'bg-success'];
        } else if (this.canComplainLaterOn) {
            this.isNextDueDate[3] = true;
            this.statusBadges = ['bg-danger', 'bg-danger', 'bg-danger'];
        } else {
            this.statusBadges = ['bg-danger', 'bg-danger', 'bg-danger'];
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
