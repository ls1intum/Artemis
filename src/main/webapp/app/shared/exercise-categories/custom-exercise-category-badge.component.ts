import { Component, Input } from '@angular/core';
import type { ExerciseCategory } from 'app/entities/exercise-category.model';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-custom-exercise-category-badge',
    templateUrl: './custom-exercise-category-badge.component.html',
    styleUrls: ['./exercise-categories.component.scss'],
    standalone: true,
    imports: [CommonModule],
})
export class CustomExerciseCategoryBadgeComponent {
    @Input() category: ExerciseCategory;
}
