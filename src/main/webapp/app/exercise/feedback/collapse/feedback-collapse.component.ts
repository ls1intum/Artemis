import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FeedbackTextComponent } from '../text/feedback-text.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-feedback-collapse',
    styleUrls: ['./feedback-collapse.scss'],
    templateUrl: './feedback-collapse.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, FeedbackTextComponent, ArtemisTranslatePipe],
})
/**
 * smallCharacterLimit can be adjusted make smaller or bigger items collapsable
 * isCollapsed tracks whether an item is currently open or closed
 * text is any string passed to the component
 */
export class FeedbackCollapseComponent implements OnInit {
    /**
     * Number of chars at which the text will be cut and the collapse functionality is enabled
     */
    readonly FEEDBACK_PREVIEW_CHARACTER_LIMIT = 300;

    @Input() feedback: FeedbackItem;
    previewText?: string;
    isCollapsed = true;

    // Icons
    faAngleDown = faAngleDown;
    faAngleRight = faAngleRight;

    ngOnInit(): void {
        this.previewText = this.computeFeedbackPreviewText(this.feedback.text);
    }

    /**
     * Computes the feedback preview for feedback texts with multiple lines or feedback that is longer than {@link FEEDBACK_PREVIEW_CHARACTER_LIMIT} characters.
     * @param text The feedback detail text.
     * @return One line of text with at most {@link FEEDBACK_PREVIEW_CHARACTER_LIMIT} characters.
     */
    private computeFeedbackPreviewText(text?: string): string | undefined {
        if (this.feedback.feedbackReference.hasLongFeedbackText) {
            return text?.slice(0, this.FEEDBACK_PREVIEW_CHARACTER_LIMIT);
        }

        if (!text || text.length < this.FEEDBACK_PREVIEW_CHARACTER_LIMIT) {
            return undefined;
        }

        if (text.includes('\n')) {
            // if there are multiple lines, only use the first one
            const firstLine = text.slice(0, text.indexOf('\n'));
            return firstLine.slice(0, this.FEEDBACK_PREVIEW_CHARACTER_LIMIT);
        }

        return text.slice(0, this.FEEDBACK_PREVIEW_CHARACTER_LIMIT);
    }

    toggleCollapse(): void {
        this.isCollapsed = !this.isCollapsed;
    }
}
