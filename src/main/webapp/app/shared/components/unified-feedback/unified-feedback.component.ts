import { Component, computed, inject, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faRedo, faTimes } from '@fortawesome/free-solid-svg-icons';
import {
    FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_IDENTIFIER,
    Feedback,
} from 'app/assessment/shared/entities/feedback.model';
import { AssessmentNamesForModelId } from 'app/modeling/manage/assess/modeling-assessment.util';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export type FeedbackType = 'correct' | 'needs_revision' | 'not_attempted' | 'non_compliant';

interface FeedbackTypeConfig {
    icon: IconDefinition;
    alertClass: string;
    defaultTitle: string;
}

@Component({
    selector: 'jhi-unified-feedback',
    standalone: true,
    templateUrl: './unified-feedback.component.html',
    styleUrls: ['./unified-feedback.component.scss'],
    imports: [NgClass, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class UnifiedFeedbackComponent {
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);

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
            defaultTitle: this.artemisTranslatePipe.transform('artemisApp.feedback.type.correct'),
        },
        needs_revision: {
            icon: faRedo,
            alertClass: 'alert-secondary',
            defaultTitle: this.artemisTranslatePipe.transform('artemisApp.feedback.type.needsRevision'),
        },
        not_attempted: {
            icon: faTimes,
            alertClass: 'alert-secondary',
            defaultTitle: this.artemisTranslatePipe.transform('artemisApp.feedback.type.notAttempted'),
        },
        non_compliant: {
            icon: faTimes,
            alertClass: 'alert-danger',
            defaultTitle: this.artemisTranslatePipe.transform('artemisApp.feedback.type.nonCompliant'),
        },
    };

    readonly inferredType = computed(() => {
        const explicitType = this.type();
        if (explicitType) {
            return explicitType;
        }

        const points = this.points();
        if (points > 0) {
            return 'correct';
        }
        if (points < 0) {
            return 'non_compliant';
        }
        return 'needs_revision';
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

    private stripFeedbackSuggestionPrefix(text: string): string {
        for (const prefix of [FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER, FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER]) {
            if (text.startsWith(prefix)) {
                return text.slice(prefix.length);
            }
        }
        return text;
    }

    private getReferencedFeedbackTitle(feedback: Feedback): string {
        if (feedback.text) {
            if (feedback.text.startsWith(FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER)) {
                return this.feedbackTypeConfigs[this.inferredType()].defaultTitle;
            }
            if (Feedback.isFeedbackSuggestion(feedback)) {
                return this.stripFeedbackSuggestionPrefix(feedback.text);
            }
            // Only use feedback.text as title when detailText exists as separate content;
            // otherwise text is used as content by buildFeedbackTextForReview and would duplicate here.
            if (feedback.detailText) {
                return feedback.text;
            }
            return this.feedbackTypeConfigs[this.inferredType()].defaultTitle;
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
