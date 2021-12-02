import { Component, Input, OnChanges } from '@angular/core';
import dayjs from 'dayjs';
import { Exercise, ExerciseType, getIcon, IncludedInOverallScore } from 'app/entities/exercise.model';
import { Exam } from 'app/entities/exam.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { faArrowLeft, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-header-exercise-page-with-details',
    templateUrl: './header-exercise-page-with-details.component.html',
})
export class HeaderExercisePageWithDetailsComponent implements OnChanges {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    @Input() public exercise: Exercise;
    @Input() public onBackClick: () => void; // TODO: This can be removed once we are happy with the breadcrumb navigation
    @Input() public title: string;
    @Input() public exam?: Exam;
    @Input() public isTestRun = false;
    @Input() public displayBackButton = true; // TODO: This can be removed once we are happy with the breadcrumb navigation
    @Input() public submissionPolicy?: SubmissionPolicy;

    public exerciseStatusBadge = 'bg-success';
    public exerciseCategories: ExerciseCategory[];
    public isExamMode = false;

    icon: IconProp;

    // Icons
    faArrowLeft = faArrowLeft;
    faQuestionCircle = faQuestionCircle;

    /**
     * Sets the status badge and categories of the exercise on changes
     */
    ngOnChanges(): void {
        this.setExerciseStatusBadge();
        this.exerciseCategories = this.exercise?.categories || [];

        if (this.exercise) {
            this.setIcon(this.exercise.type);
        }

        if (this.exam) {
            this.isExamMode = true;
        }
    }

    private setExerciseStatusBadge(): void {
        if (this.exercise) {
            if (this.isExamMode) {
                this.exerciseStatusBadge = dayjs(this.exam?.endDate!).isBefore(dayjs()) ? 'bg-danger' : 'bg-success';
            } else {
                this.exerciseStatusBadge = dayjs(this.exercise.dueDate!).isBefore(dayjs()) ? 'bg-danger' : 'bg-success';
            }
        }
    }

    setIcon(exerciseType?: ExerciseType) {
        if (exerciseType) {
            this.icon = getIcon(exerciseType) as IconProp;
        }
    }
}
