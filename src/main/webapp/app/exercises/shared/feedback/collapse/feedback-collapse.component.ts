import { Component, Input, OnInit } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { computeFeedbackPreviewText } from 'app/exercises/shared/feedback/feedback.util';

@Component({
    selector: 'jhi-feedback-collapse',
    styleUrls: ['./feedback-collapse.scss'],
    templateUrl: './feedback-collapse.component.html',
})
/**
 * smallCharacterLimit can be adjusted make smaller or bigger items collapsable
 * isCollapsed tracks whether an item is currently open or closed
 * text is any string passed to the component
 */
export class FeedbackCollapseComponent implements OnInit {
    @Input() text: string; // this is typically feedback.detailText
    previewText?: string;
    isCollapsed = true;

    // Icons
    faAngleDown = faAngleDown;
    faAngleRight = faAngleRight;

    ngOnInit(): void {
        this.previewText = computeFeedbackPreviewText(this.text);
    }
}
