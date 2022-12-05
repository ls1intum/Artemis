import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Component, Input, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { FeedbackItemGroup } from 'app/exercises/shared/feedback/item/feedback-item-group';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';

@Component({
    selector: 'jhi-feedback-item-node',
    templateUrl: './feedback-item-node.component.html',
    styleUrls: ['./feedback-item-node.scss'],
})
export class FeedbackItemNode implements OnInit {
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;

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
}
