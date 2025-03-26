import { Component, Input } from '@angular/core';
import type { ExerciseCategory } from 'app/entities/exercise-category.model';
import { CommonModule } from '@angular/common';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';

type CategoryFontSize = 'default' | 'small';

@Component({
    selector: 'jhi-custom-exercise-category-badge',
    templateUrl: './custom-exercise-category-badge.component.html',
    styleUrls: ['custom-exercise-category-badge.component.scss'],
    imports: [CommonModule, FontAwesomeModule],
})
export class CustomExerciseCategoryBadgeComponent {
    protected readonly faTimes = faTimes;

    @Input({ required: true }) category: ExerciseCategory | FaqCategory;
    @Input() displayRemoveButton = false;
    @Input() onClick: () => void = () => {};
    @Input() fontSize: CategoryFontSize = 'default';
}
