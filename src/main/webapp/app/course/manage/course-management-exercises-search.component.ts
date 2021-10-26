import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';
import { exerciseTypes } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-course-management-exercises-search',
    templateUrl: './course-management-exercises-search.component.html',
})
export class CourseManagementExercisesSearchComponent implements OnInit {
    typeOptions: string[];
    exerciseNameSearch: string;
    exerciseCategorySearch: string;
    exerciseTypeSearch: string;
    @Output() exerciseFilter = new EventEmitter<ExerciseFilter>();

    constructor() {}

    ngOnInit(): void {
        this.exerciseNameSearch = '';
        this.exerciseCategorySearch = '';
        this.exerciseTypeSearch = 'all';
        this.typeOptions = ['all'];
        this.typeOptions.push(...exerciseTypes);
    }

    sendUpdate() {
        this.exerciseFilter.emit(new ExerciseFilter(this.exerciseNameSearch, this.exerciseCategorySearch, this.exerciseTypeSearch));
    }
}
