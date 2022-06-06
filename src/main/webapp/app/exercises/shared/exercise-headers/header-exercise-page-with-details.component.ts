import { Component, Input, OnChanges } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType, getIcon, IncludedInOverallScore } from 'app/entities/exercise.model';
import { Exam } from 'app/entities/exam.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-header-exercise-page-with-details',
    templateUrl: './header-exercise-page-with-details.component.html',
})
export class HeaderExercisePageWithDetailsComponent implements OnChanges {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    @Input() public exercise: Exercise;
    @Input() public studentParticipation?: StudentParticipation;
    @Input() public title: string;
    @Input() public exam?: Exam;
    @Input() public isTestRun = false;
    @Input() public submissionPolicy?: SubmissionPolicy;

    public exerciseStatusBadge = 'bg-success';
    public exerciseCategories: ExerciseCategory[];
    public isExamMode = false;
    public dueDate?: dayjs.Dayjs;

    icon: IconProp;

    // Icons
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
}
