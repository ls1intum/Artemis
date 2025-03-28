import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';
import { exerciseTypes } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-course-management-exercises-search',
    templateUrl: './course-management-exercises-search.component.html',
    styleUrls: ['./course-management-exercises-search.component.scss'],
    imports: [TranslateDirective, FormsModule, ArtemisTranslatePipe],
})
export class CourseManagementExercisesSearchComponent implements OnInit {
    typeOptions: string[];
    exerciseNameSearch: string;
    exerciseCategorySearch: string;
    exerciseTypeSearch: string;
    @Output() exerciseFilter = new EventEmitter<ExerciseFilter>();

    /**
     * Initializes the attributes to match an empty filter
     */
    ngOnInit(): void {
        const filter = new ExerciseFilter();
        this.exerciseNameSearch = filter.exerciseNameSearch;
        this.exerciseCategorySearch = filter.exerciseCategorySearch;
        this.exerciseTypeSearch = filter.exerciseTypeSearch;
        this.typeOptions = ['all'];
        this.typeOptions.push(...exerciseTypes);
    }

    /**
     * Sends an updated filter through the event emitter
     * Triggered every time the type dropdown is changed or when the user manually presses Enter or the search button
     */
    sendUpdate() {
        this.exerciseFilter.emit(new ExerciseFilter(this.exerciseNameSearch, this.exerciseCategorySearch, this.exerciseTypeSearch));
    }

    /**
     * Resets all inputs to default values
     */
    reset() {
        const filter = new ExerciseFilter();
        this.exerciseNameSearch = filter.exerciseNameSearch;
        this.exerciseCategorySearch = filter.exerciseCategorySearch;
        this.exerciseTypeSearch = filter.exerciseTypeSearch;
        this.sendUpdate();
    }
}
