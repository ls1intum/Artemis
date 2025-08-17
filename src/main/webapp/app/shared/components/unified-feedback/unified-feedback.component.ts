import { Component, computed, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faExclamationTriangle, faRedo, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { AssessmentNamesForModelId } from 'app/modeling/manage/assess/modeling-assessment.util';

export type FeedbackType = 'correct' | 'needs_revision' | 'not_attempted';
export type FeedbackIconType = 'retry' | 'success' | 'error';

@Component({
    selector: 'jhi-unified-feedback',
    templateUrl: './unified-feedback.component.html',
    styleUrls: ['./unified-feedback.component.scss'],
    imports: [NgClass, FaIconComponent],
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

    // Icons
    readonly faCheck = faCheck;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faRedo = faRedo;
    readonly faTimes = faTimes;

    // Computed properties for automatic type inference
    readonly inferredType = computed(() => {
        const explicitType = this.type();
        if (explicitType) {
            return explicitType;
        }

        // Infer type based on points
        const points = this.points();
        if (points > 0) {
            return 'correct' as FeedbackType;
        } else {
            return 'not_attempted' as FeedbackType;
        }
    });

    readonly inferredTitle = computed(() => {
        const explicitTitle = this.title();
        if (explicitTitle) {
            return explicitTitle;
        }

        // If we have a feedback object, use the utility function
        const feedback = this.feedback();
        if (feedback) {
            return this.getReferencedFeedbackTitle(feedback);
        }

        // Generate title based on inferred type
        switch (this.inferredType()) {
            case 'correct':
                return 'Correct';
            case 'needs_revision':
                return 'Needs Revision';
            case 'not_attempted':
                return 'Not Attempted';
            default:
                return undefined;
        }
    });

    readonly inferredReference = computed(() => {
        const explicitReference = this.reference();
        if (explicitReference) {
            return explicitReference;
        }

        // If we have a feedback object, use the utility function
        const feedback = this.feedback();
        if (feedback) {
            return this.getReferencedFeedbackReference(feedback);
        }

        return undefined;
    });

    readonly inferredIcon = computed(() => {
        switch (this.inferredType()) {
            case 'correct':
                return this.faCheck;
            case 'needs_revision':
                return this.faRedo;
            case 'not_attempted':
                return this.faTimes;
            default:
                return this.faCheck;
        }
    });

    readonly inferredAlertClass = computed(() => {
        switch (this.inferredType()) {
            case 'correct':
                return 'alert-success';
            case 'needs_revision':
                return 'alert-warning';
            case 'not_attempted':
                return 'alert-danger';
            default:
                return 'alert-info';
        }
    });

    // Legacy support for icon input (deprecated)
    icon = input<FeedbackIconType>('success');

    getIconForType(): any {
        return this.inferredIcon();
    }

    getAlertClass(): string {
        return this.inferredAlertClass();
    }

    /**
     * Generates a title for referenced feedback based on the element type and name
     */
    getReferencedFeedbackTitle(feedback: Feedback): string {
        if (feedback.text) {
            return feedback.text;
        }

        if (this.assessmentsNames() && feedback.referenceId) {
            const assessmentName = this.assessmentsNames()![feedback.referenceId];
            if (assessmentName) {
                return `${assessmentName.type}: ${assessmentName.name}`;
            }
        }

        if (feedback.credits && feedback.credits > 0) {
            return 'Good job!';
        } else if (feedback.credits && feedback.credits < 0) {
            return 'Incorrect';
        } else {
            return feedback.positive ? 'Good effort!' : 'Needs revision';
        }
    }

    /**
     * Generates a reference string for referenced feedback
     */
    getReferencedFeedbackReference(feedback: Feedback): string | undefined {
        if (this.assessmentsNames() && feedback.referenceId) {
            const assessmentName = this.assessmentsNames()![feedback.referenceId];
            if (assessmentName) {
                return `Reference: ${assessmentName.name}`;
            }
        }
        if (feedback.reference) {
            return `Reference: ${feedback.reference}`;
        }
        return undefined;
    }
}
