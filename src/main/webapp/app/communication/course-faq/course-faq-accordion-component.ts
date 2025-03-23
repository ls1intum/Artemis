import { Component, OnDestroy, input } from '@angular/core';
import { Faq } from 'app/communication/entities/faq.model';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { Subject } from 'rxjs/internal/Subject';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-course-faq-accordion',
    templateUrl: './course-faq-accordion.component.html',
    styleUrl: './course-faq-accordion.component.scss',
    imports: [CustomExerciseCategoryBadgeComponent, HtmlForMarkdownPipe],
})
export class CourseFaqAccordionComponent implements OnDestroy {
    private ngUnsubscribe = new Subject<void>();
    faq = input.required<Faq>();

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
