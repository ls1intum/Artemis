import { Component, Input, OnChanges, OnInit } from '@angular/core';
import * as moment from 'moment';
import { Exercise, ExerciseCategory, getIcon } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-header-exercise-page-with-details',
    templateUrl: './header-exercise-page-with-details.component.html',
})
export class HeaderExercisePageWithDetailsComponent implements OnInit, OnChanges {
    @Input() public exercise: Exercise;
    @Input() public onBackClick: () => void;
    @Input() public title: string;

    public exerciseStatusBadge = 'badge-success';
    public exerciseCategories: ExerciseCategory[];

    getIcon = getIcon;

    constructor(private exerciseService: ExerciseService) {}

    ngOnInit(): void {
        this.setExerciseStatusBadge();
        this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.exercise);
    }

    ngOnChanges(): void {
        this.setExerciseStatusBadge();
        this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.exercise);
    }

    private setExerciseStatusBadge(): void {
        if (this.exercise) {
            this.exerciseStatusBadge = moment(this.exercise.dueDate!).isBefore(moment()) ? 'badge-danger' : 'badge-success';
        }
    }
}
