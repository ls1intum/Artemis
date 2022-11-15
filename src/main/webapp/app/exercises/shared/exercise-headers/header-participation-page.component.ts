import { Component, Input, OnChanges, OnInit, ViewEncapsulation } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise, IncludedInOverallScore, getCourseFromExercise, getIcon } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ButtonType } from 'app/shared/components/button.component';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-header-participation-page',
    templateUrl: './header-participation-page.component.html',
    styleUrls: ['./header-participation-page.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class HeaderParticipationPageComponent implements OnInit, OnChanges {
    readonly ButtonType = ButtonType;
    readonly IncludedInOverallScore = IncludedInOverallScore;
    @Input() title: string;
    @Input() exercise: Exercise;
    @Input() participation: StudentParticipation;

    public exerciseStatusBadge = 'bg-success';
    public exerciseCategories: ExerciseCategory[];
    public achievedPoints?: number;

    dueDate?: dayjs.Dayjs;
    getIcon = getIcon;

    /**
     * Sets the status badge and categories of the exercise on init
     */
    ngOnInit(): void {
        this.ngOnChanges();
    }

    /**
     * Returns false if it is an exam exercise and the publishResultsDate is in the future, true otherwise
     */
    get resultsPublished(): boolean {
        if (this.exercise?.exerciseGroup?.exam) {
            if (this.exercise.exerciseGroup.exam.publishResultsDate) {
                return dayjs().isAfter(this.exercise.exerciseGroup.exam.publishResultsDate);
            }
            // default to false if it is an exam exercise but the publishResultsDate is not set
            return false;
        }
        return true;
    }

    /**
     * Sets the status badge and categories of the exercise on changes
     */
    ngOnChanges(): void {
        if (this.exercise) {
            this.exerciseStatusBadge = hasExerciseDueDatePassed(this.exercise, this.participation) ? 'bg-danger' : 'bg-success';
            this.exerciseCategories = this.exercise.categories || [];
            this.dueDate = getExerciseDueDate(this.exercise, this.participation);
            if (this.participation?.results?.[0]?.rated) {
                this.achievedPoints = roundValueSpecifiedByCourseSettings(
                    (this.participation.results?.[0].score! * this.exercise.maxPoints!) / 100,
                    getCourseFromExercise(this.exercise),
                );
            }
        }
    }
}
