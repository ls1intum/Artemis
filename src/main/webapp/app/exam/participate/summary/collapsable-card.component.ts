import { Component, Input } from '@angular/core';
import { faAngleRight } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-collapsable-card',
    templateUrl: './collapsable-card.component.html',
    styleUrls: ['../../../course/manage/course-exercise-card.component.scss', '../../../exercises/quiz/shared/quiz.scss', 'exam-result-summary.component.scss'],
})
export class CollapsableCardComponent {
    @Input() isCardContentCollapsed: boolean;
    @Input() toggleCollapse: () => void;

    faAngleRight = faAngleRight;
}
