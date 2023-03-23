import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Component, Input, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { faAngleDown, faAngleUp, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { FeedbackGroup, isFeedbackGroup } from 'app/exercises/shared/feedback/group/feedback-group';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { FeedbackNode } from 'app/exercises/shared/feedback/node/feedback-node';

@Component({
    selector: 'jhi-feedback-node',
    templateUrl: './feedback-node.component.html',
    styleUrls: ['./feedback-node.scss'],
})
export class FeedbackNodeComponent implements OnInit {
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;

    @Input() feedbackItemNode: FeedbackNode;
    @Input() course?: Course;

    // This is a workaround for type safety in the template
    feedbackItem: FeedbackItem;
    feedbackItemGroup: FeedbackGroup;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    ngOnInit(): void {
        if (isFeedbackGroup(this.feedbackItemNode)) {
            this.feedbackItemGroup = this.feedbackItemNode;
        } else {
            this.feedbackItem = this.feedbackItemNode as FeedbackItem;
        }
    }
}
