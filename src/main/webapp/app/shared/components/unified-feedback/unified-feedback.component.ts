import { Component, computed, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faRedo, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { AssessmentNamesForModelId } from 'app/modeling/manage/assess/modeling-assessment.util';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export type FeedbackType = 'correct' | 'needs_revision' | 'not_attempted';

interface FeedbackTypeConfig {
    icon: any;
    alertClass: string;
    defaultTitle: string;
}

@Component({
    selector: 'jhi-unified-feedback',
    templateUrl: './unified-feedback.component.html',
    styleUrls: ['./unified-feedback.component.scss'],
    imports: [NgClass, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class UnifiedFeedbackComponent {
    feedbackContent = input<string>('');
    points = input<number>(0);
    type = input<FeedbackType | undefined>(undefined);
    title = input<string | undefined>(undefined);
    reference = input<string | undefined>(undefined);
    feedback = input<Feedback | undefined>(undefined);
    assessmentsNames = input<AssessmentNamesForModelId | undefined>(undefined);
    showReference = input<boolean>(true);

    private readonly feedbackTypeConfigs: Record<FeedbackType, FeedbackTypeConfig> = {
        correct: {
            icon: faCheck,
            alertClass: 'alert-success',
            defaultTitle: 'Correct',
        },
        needs_revision: {
            icon: faRedo,
            alertClass: 'alert-warning',
            defaultTitle: 'Needs Revision',
        },
        not_attempted: {
            icon: faTimes,
            alertClass: 'alert-danger',
            defaultTitle: 'Not Attempted',
        },
    };

    readonly inferredType = computed(() => {
        const explicitType = this.type();
        if (explicitType) {
            return explicitType;
        }

        const points = this.points();
        return points > 0 ? 'correct' : 'not_attempted';
    });

    readonly inferredTitle = computed(() => {
        const explicitTitle = this.title();
        if (explicitTitle) {
            return explicitTitle;
        }

        const feedback = this.feedback();
        if (feedback) {
            return this.getReferencedFeedbackTitle(feedback);
        }

        return this.feedbackTypeConfigs[this.inferredType()].defaultTitle;
    });

    readonly inferredReference = computed(() => {
        const explicitReference = this.reference();
        if (explicitReference) {
            return explicitReference;
        }

        const feedback = this.feedback();
        if (feedback) {
            return this.getReferencedFeedbackReference(feedback);
        }

        return undefined;
    });

    readonly inferredIcon = computed(() => {
        return this.feedbackTypeConfigs[this.inferredType()].icon;
    });

    readonly inferredAlertClass = computed(() => {
        return this.feedbackTypeConfigs[this.inferredType()].alertClass;
    });

    private getReferencedFeedbackTitle(feedback: Feedback): string {
        if (feedback.text) {
            return feedback.text;
        }

        if (this.assessmentsNames() && feedback.referenceId) {
            const assessmentName = this.assessmentsNames()![feedback.referenceId];
            if (assessmentName) {
                return `${assessmentName.type}: ${assessmentName.name}`;
            }
        }
        return this.feedbackTypeConfigs[this.inferredType()].defaultTitle;
    }

    private getReferencedFeedbackReference(feedback: Feedback): string | undefined {
        if (this.assessmentsNames() && feedback.referenceId) {
            const assessmentName = this.assessmentsNames()![feedback.referenceId];
            if (assessmentName) {
                return assessmentName.name;
            }
        }
        if (feedback.reference) {
            return feedback.reference;
        }
        return undefined;
    }
}
