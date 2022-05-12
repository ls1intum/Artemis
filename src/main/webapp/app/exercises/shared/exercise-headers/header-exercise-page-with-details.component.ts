import { Component, Input, OnInit } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, getCourseFromExercise, getIcon, getIconTooltip, IncludedInOverallScore } from 'app/entities/exercise.model';
import { Exam } from 'app/entities/exam.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { faArrowLeft, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ComplaintService } from 'app/complaints/complaint.service';

@Component({
    selector: 'jhi-header-exercise-page-with-details',
    templateUrl: './header-exercise-page-with-details.component.html',
})
export class HeaderExercisePageWithDetailsComponent implements OnInit {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly AssessmentType = AssessmentType;
    readonly getIcon = getIcon;
    readonly getIconTooltip = getIconTooltip;

    @Input() public exercise: Exercise;
    @Input() public studentParticipation?: StudentParticipation;
    @Input() public onBackClick: () => void; // TODO: This can be removed once we are happy with the breadcrumb navigation
    @Input() public title: string;
    @Input() public exam?: Exam;
    @Input() public isTestRun = false;
    @Input() public displayBackButton = true; // TODO: This can be removed once we are happy with the breadcrumb navigation
    @Input() public submissionPolicy?: SubmissionPolicy;

    public exerciseStatusBadge = 'bg-success';
    public exerciseCategories: ExerciseCategory[];
    public isExamMode = false;
    public dueDate?: dayjs.Dayjs;
    public programmingExercise?: ProgrammingExercise;
    public course: Course;
    public individualComplaintDeadline?: dayjs.Dayjs;
    public isNextDueDate: boolean[];
    public canComplainLaterOn: boolean;

    icon: IconProp;

    // Icons
    faArrowLeft = faArrowLeft;
    faQuestionCircle = faQuestionCircle;

    constructor(private complaintService: ComplaintService) {}

    ngOnInit(): void {
        this.exerciseCategories = this.exercise.categories || [];

        this.setIcon(this.exercise.type);
        this.setExerciseStatusBadge();

        this.programmingExercise = this.exercise.type === ExerciseType.PROGRAMMING ? (this.exercise as ProgrammingExercise) : undefined;
        this.course = getCourseFromExercise(this.exercise)!;

        if (this.exam) {
            this.isExamMode = true;
            this.setIsNextDueDateExamMode();
        } else {
            this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);
            this.setIsNextDueDateCourseMode();
            this.individualComplaintDeadline = this.complaintService.getIndividualComplaintDueDate(this.exercise, this.course, this.studentParticipation);
            // The student can either still submit or there is a submission where the student did not have the chance to complain yet
            this.canComplainLaterOn =
                (dayjs().isBefore(this.exercise.dueDate) ||
                    (!!this.studentParticipation?.submissionCount && this.studentParticipation?.submissionCount > 0 && !this.individualComplaintDeadline)) &&
                (this.exercise.allowComplaintsForAutomaticAssessments || (!!this.exercise.assessmentType && this.exercise.assessmentType !== AssessmentType.AUTOMATIC));
        }
    }

    private setExerciseStatusBadge(): void {
        if (this.exercise) {
            if (this.exam) {
                this.exerciseStatusBadge = dayjs().isAfter(dayjs(this.exam.endDate!)) ? 'bg-danger' : 'bg-success';
            } else {
                this.exerciseStatusBadge = hasExerciseDueDatePassed(this.exercise, this.studentParticipation) ? 'bg-danger' : 'bg-success';
            }
        }
    }

    private setIcon(exerciseType?: ExerciseType) {
        if (exerciseType) {
            this.icon = getIcon(exerciseType) as IconProp;
        }
    }

    private setIsNextDueDateExamMode() {
        this.isNextDueDate = [false, false];
        const now = dayjs();
        if (now.isBefore(this.exam?.endDate)) {
            this.isNextDueDate[0] = true;
        } else if (now.isBefore(this.exam?.publishResultsDate)) {
            this.isNextDueDate[1] = true;
        }
    }

    private setIsNextDueDateCourseMode() {
        this.isNextDueDate = [false, false, false, false, false, false];
        const now = dayjs();
        if (now.isBefore(this.dueDate)) {
            this.isNextDueDate[0] = true;
        } else if (now.isBefore(this.programmingExercise?.buildAndTestStudentSubmissionsAfterDueDate)) {
            this.isNextDueDate[1] = true;
        } else if (now.isBefore(this.exercise.exampleSolutionPublicationDate)) {
            this.isNextDueDate[2] = true;
        } else if (now.isBefore(this.exercise.assessmentDueDate)) {
            this.isNextDueDate[3] = true;
        } else if (now.isBefore(this.individualComplaintDeadline)) {
            this.isNextDueDate[4] = true;
        } else if (this.canComplainLaterOn) {
            this.isNextDueDate[5] = true;
        }
    }
}
