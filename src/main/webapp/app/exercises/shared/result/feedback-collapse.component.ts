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
    @Input() text: string; // this is typically feedback.detailText
    @Input() previewText?: string; // if this is undefined, the whole text is shown
    smallCharacterLimit = 300;
    isCollapsed = true;
}
