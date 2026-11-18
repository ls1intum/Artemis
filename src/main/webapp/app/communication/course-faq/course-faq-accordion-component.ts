import { Component, input } from '@angular/core';
import { Faq } from 'app/communication/shared/entities/faq.model';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { CustomExerciseCategoryBadgeComponent } from 'app/exercise/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';

@Component({
    selector: 'jhi-course-faq-accordion',
    templateUrl: './course-faq-accordion.component.html',
    imports: [CustomExerciseCategoryBadgeComponent, MarkdownDirective],
})
export class CourseFaqAccordionComponent {
    faq = input.required<Faq>();
}
