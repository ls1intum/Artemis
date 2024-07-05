import { Component, Input } from '@angular/core';
import type { ExerciseCategory } from 'app/entities/exercise-category.model';
import { CommonModule } from '@angular/common';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

const DEFAULT_ON_CLICK = () => {};

@Component({
    selector: 'jhi-custom-exercise-category-badge',
    templateUrl: './custom-exercise-category-badge.component.html',
    styleUrls: ['./exercise-categories.component.scss'],
    standalone: true,
    imports: [CommonModule, FontAwesomeModule],
})
export class CustomExerciseCategoryBadgeComponent {
    @Input() category: ExerciseCategory;
    @Input() displayRemoveButton: boolean = false;
    @Input() onClick: () => void = DEFAULT_ON_CLICK;

    readonly faTimes = faTimes;
}
