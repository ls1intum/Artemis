import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-feedback-collapse',
    styleUrls: ['./result-detail.scss'],
    templateUrl: './feedback-collapse.component.html',
})
/**
 * smallCharacterLimit can be adjusted make smaller or bigger items collapsable
 * isCollapsed tracks whether an item is currently open or closed
 * text is any string passed to the component
 */
export class FeedbackCollapseComponent {
    @Input() text: string;
    smallCharacterLimit = 200;
    isCollapsed = true;
}
