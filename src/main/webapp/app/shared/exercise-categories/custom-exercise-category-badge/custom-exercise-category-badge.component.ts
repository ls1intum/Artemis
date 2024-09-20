import { Component, Input } from '@angular/core';
import type { ExerciseCategory } from 'app/entities/exercise-category.model';
import { CommonModule } from '@angular/common';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { FAQCategory } from 'app/entities/faq-category.model';

type CategoryFontSize = 'default' | 'small';

@Component({
    selector: 'jhi-custom-exercise-category-badge',
    templateUrl: './custom-exercise-category-badge.component.html',
    styleUrls: ['custom-exercise-category-badge.component.scss'],
    standalone: true,
    imports: [CommonModule, FontAwesomeModule],
})
export class CustomExerciseCategoryBadgeComponent {
    protected readonly faTimes = faTimes;

    @Input({ required: true }) category: ExerciseCategory | FAQCategory;
    @Input() displayRemoveButton: boolean = false;
    @Input() onClick: () => void = () => {};
    @Input() fontSize: CategoryFontSize = 'default';
}
