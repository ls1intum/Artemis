import { Component, OnDestroy, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Faq } from 'app/entities/faq.model';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { Subject } from 'rxjs/internal/Subject';

@Component({
    selector: 'jhi-course-faq-accordion',
    templateUrl: './course-faq-accordion-component.html',
    styleUrl: './course-faq-accordion-component.scss',
    standalone: true,

    imports: [TranslateDirective, CustomExerciseCategoryBadgeComponent],
})
export class CourseFaqAccordionComponent implements OnDestroy {
    private ngUnsubscribe = new Subject<void>();
    faq = input.required<Faq>();

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
