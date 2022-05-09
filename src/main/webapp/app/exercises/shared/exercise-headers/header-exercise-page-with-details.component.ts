import { Component, Input, OnChanges } from '@angular/core';
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

@Component({
    selector: 'jhi-header-exercise-page-with-details',
    templateUrl: './header-exercise-page-with-details.component.html',
    styleUrls: ['./header-exercise-page-with-details.component.scss'],
})
export class HeaderExercisePageWithDetailsComponent implements OnChanges {
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

    icon: IconProp;

    // Icons
    faArrowLeft = faArrowLeft;
    faQuestionCircle = faQuestionCircle;

    /**
     * Sets the status badge and categories of the exercise on changes
     */
    ngOnChanges(): void {
        this.exerciseCategories = this.exercise.categories || [];

        if (this.exercise) {
            this.setIcon(this.exercise.type);
        }

        if (this.exam) {
            this.isExamMode = true;
        }

        if (this.exercise && !this.isExamMode) {
            this.dueDate = getExerciseDueDate(this.exercise, this.studentParticipation);
        }

        this.setExerciseStatusBadge();

        this.programmingExercise = this.exercise.type === ExerciseType.PROGRAMMING ? (this.exercise as ProgrammingExercise) : undefined;

        this.course = getCourseFromExercise(this.exercise)!;
        this.individualComplaintDeadline = this.getIndividualComplaintDueDate();
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

    private getIndividualComplaintDueDate(): dayjs.Dayjs | undefined {
        const assessmentDueDate = this.exercise.assessmentDueDate;
        if (!assessmentDueDate) {
            return undefined;
        }
        const lastResult = this.studentParticipation?.results?.last();
        const now = dayjs();

        let complaintDueDate;
        if (!lastResult || !lastResult?.rated) {
            complaintDueDate = assessmentDueDate.isBefore(now) ? now : assessmentDueDate;
        } else {
            complaintDueDate = assessmentDueDate.isBefore(lastResult.completionDate) ? lastResult.completionDate! : assessmentDueDate;
        }

        return complaintDueDate.add(this.course.maxComplaintTimeDays!, 'days');
    }
}
