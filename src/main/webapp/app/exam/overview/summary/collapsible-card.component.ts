import { Component, input } from '@angular/core';
import { faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-collapsible-card',
    templateUrl: './collapsible-card.component.html',
    styleUrls: ['../../../core/course/manage/course-exercise-card.component.scss', '../../../quiz/shared/quiz.scss', 'exam-result-summary.component.scss'],
    imports: [FaIconComponent, NgbCollapse],
})
export class CollapsibleCardComponent {
    isCardContentCollapsed = input.required<boolean>();
    toggleCollapse = input.required<() => void>();

    faAngleRight = faAngleRight;
}
