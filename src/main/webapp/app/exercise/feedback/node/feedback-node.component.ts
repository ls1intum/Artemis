import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { Component, OnInit, input } from '@angular/core';
import { Course } from 'app/course/shared/entities/course.model';
import { faAngleDown, faAngleUp, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { FeedbackGroup, isFeedbackGroup } from 'app/exercise/feedback/group/feedback-group';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { FeedbackNode } from 'app/exercise/feedback/node/feedback-node';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FeedbackCollapseComponent } from '../collapse/feedback-collapse.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-feedback-node',
    templateUrl: './feedback-node.component.html',
    styleUrls: ['./feedback-node.scss'],
    imports: [NgClass, FaIconComponent, NgbTooltip, FeedbackCollapseComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class FeedbackNodeComponent implements OnInit {
    readonly roundValueSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;

    readonly feedbackItemNode = input<FeedbackNode>(undefined!);
    readonly course = input<Course>();

    // This is a workaround for type safety in the template
    feedbackItem: FeedbackItem;
    feedbackItemGroup: FeedbackGroup;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    ngOnInit(): void {
        const feedbackItemNode = this.feedbackItemNode();
        if (isFeedbackGroup(feedbackItemNode)) {
            this.feedbackItemGroup = feedbackItemNode;
        } else {
            this.feedbackItem = feedbackItemNode as FeedbackItem;
        }
    }
}
