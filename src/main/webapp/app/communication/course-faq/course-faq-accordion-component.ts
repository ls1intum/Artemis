import { Component, OnDestroy, input } from '@angular/core';
import { Faq } from 'app/entities/faq.model';
import { Subject } from 'rxjs/internal/Subject';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { CustomExerciseCategoryBadgeComponent } from 'app/exercise/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';

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
