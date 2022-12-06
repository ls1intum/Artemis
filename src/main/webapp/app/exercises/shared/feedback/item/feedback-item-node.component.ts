import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Component, Input, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { FeedbackItemGroup, isFeedbackItemGroup } from 'app/exercises/shared/feedback/item/feedback-item-group';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { FeedbackItemNode } from 'app/exercises/shared/feedback/item/feedback-item-node';
import { calculateAppliedCredits } from 'app/exercises/shared/feedback/item/feedback-item-util';

@Component({
    selector: 'jhi-feedback-item-node',
    templateUrl: './feedback-item-node.component.html',
    styleUrls: ['./feedback-item-node.scss'],
})
export class FeedbackItemNodeComponent implements OnInit {
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;

    @Input() feedbackItemNode: FeedbackItemNode;
    @Input() course?: Course;

    // This is a workaround for type safety in the template
    feedbackItem: FeedbackItem;
    feedbackItemGroup: FeedbackItemGroup;

    // The applied credits due to credit caps
    appliedCredits: number;

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    ngOnInit(): void {
        if (isFeedbackItemGroup(this.feedbackItemNode)) {
            this.feedbackItemGroup = this.feedbackItemNode;
        } else {
            this.feedbackItem = <FeedbackItem>this.feedbackItemNode;
        }

        this.appliedCredits = calculateAppliedCredits(this.feedbackItemNode);
    }
}
