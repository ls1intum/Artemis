import { Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-course-exercises-grouped-by-category',
    templateUrl: './course-exercises-grouped-by-category.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExercisesGroupedByCategoryComponent {
    @Input() filteredExercises?: Exercise[];
}
