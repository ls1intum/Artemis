import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-feedback-collapse',
    styleUrls: ['./result-detail.scss'],
    templateUrl: './feedback-collapse.component.html',
})
export class FeedbackCollapseComponent {
    @Input() text: string;
    smallFeedbackCharacterLimit = 200;
    isCollapsed = true;
}
