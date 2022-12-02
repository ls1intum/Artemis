import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Component, Input, OnInit } from '@angular/core';
import { FeedbackItemGroup } from 'app/exercises/shared/feedback/item/programming/programming-feedback-item-groups';
import { FeedbackItem, FeedbackItemType } from 'app/exercises/shared/result/detail/result-detail.component';
import { Course } from 'app/entities/course.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-feedback-item-node',
    templateUrl: './feedback-item-node.component.html',
    styleUrls: ['./feedback-item-node.scss'],
})
export class FeedbackItemNode implements OnInit {
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly FeedbackItemType = FeedbackItemType;

    @Input() feedbackNode: FeedbackItem | FeedbackItemGroup;
    @Input() course?: Course;

    // This is a workaround for type safety in the template
    feedbackItem: FeedbackItem;
    feedbackItemGroup: FeedbackItemGroup;

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    ngOnInit(): void {
        if (!this.isFeedbackItemGroup(this.feedbackNode)) {
            this.feedbackItem = this.feedbackNode;
        } else {
            this.feedbackItemGroup = this.feedbackNode;
        }
    }

    private isFeedbackItemGroup(node: FeedbackItem | FeedbackItemGroup): node is FeedbackItemGroup {
        return (node as FeedbackItemGroup).members !== undefined;
    }

    /**
     * Handles the coloring of each feedback items based on its type and credits.
     * @param feedback The feedback item
     */
    getClassNameForFeedbackItem(feedback: FeedbackItem): string {
        if (feedback.type === FeedbackItemType.Issue) {
            return 'alert-warning';
        } else if (feedback.type === FeedbackItemType.Test) {
            return feedback.positive ? 'alert-success' : 'alert-danger';
        } else if (feedback.type === FeedbackItemType.Subsequent) {
            return 'alert-secondary';
        } else {
            if (feedback.credits === 0) {
                return 'alert-warning';
            } else {
                return feedback.positive || (feedback.credits && feedback.credits > 0) ? 'alert-success' : 'alert-danger';
            }
        }
    }
}
