import { Component, Input, OnChanges, OnInit } from '@angular/core';
import * as moment from 'moment';
import { Exercise, ExerciseCategory, ExerciseType, getIcon, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exam } from 'app/entities/exam.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

@Component({
    selector: 'jhi-header-exercise-page-with-details',
    templateUrl: './header-exercise-page-with-details.component.html',
})
export class HeaderExercisePageWithDetailsComponent implements OnInit, OnChanges {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    @Input() public exercise: Exercise;
    @Input() public onBackClick: () => void; // TODO: This can be removed once we are happy with the breadcrumb navigation
    @Input() public title: string;
    @Input() public exam: Exam | null;
    @Input() public isTestRun = false;
    @Input() public displayBackButton = true; // TODO: This can be removed once we are happy with the breadcrumb navigation

    public exerciseStatusBadge = 'badge-success';
    public exerciseCategories: ExerciseCategory[];
    public isExamMode = false;

    icon: IconProp;

    constructor(private exerciseService: ExerciseService) {}

    /**
     * Sets the status badge and categories of the exercise on init
     */
    ngOnInit(): void {
        this.setExerciseStatusBadge();
        this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.exercise);
        this.setIcon(this.exercise.type);
    }

    /**
     * Sets the status badge and categories of the exercise on changes
     */
    ngOnChanges(): void {
        this.setExerciseStatusBadge();
        this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.exercise);
        this.setIcon(this.exercise.type);

        if (this.exam) {
            this.isExamMode = true;
        }
    }

    private setExerciseStatusBadge(): void {
        if (this.exercise) {
            if (this.isExamMode) {
                this.exerciseStatusBadge = moment(this.exam?.endDate!).isBefore(moment()) ? 'badge-danger' : 'badge-success';
            } else {
                this.exerciseStatusBadge = moment(this.exercise.dueDate!).isBefore(moment()) ? 'badge-danger' : 'badge-success';
            }
        }
    }

    setIcon(exerciseType?: ExerciseType) {
        if (exerciseType) {
            this.icon = getIcon(exerciseType) as IconProp;
        }
    }
}
