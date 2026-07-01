import { AfterViewInit, Component, ElementRef, computed, inject, input, model, output, viewChild } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TooltipModule } from 'primeng/tooltip';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faMessage, faTimes } from '@fortawesome/free-solid-svg-icons';
import {
    FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_IDENTIFIER,
    Feedback,
} from 'app/assessment/shared/entities/feedback.model';
import { AssessmentNamesForModelId } from 'app/modeling/manage/assess/modeling-assessment.util';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LocaleConversionService } from 'app/foundation/service/locale-conversion.service';
import { ConfirmIconComponent } from 'app/shared-ui/confirm-icon/confirm-icon.component';

export type FeedbackType = 'correct' | 'needs_revision' | 'not_attempted' | 'non_compliant';

interface FeedbackTypeConfig {
    icon: IconDefinition;
    alertClass: string;
}

@Component({
    selector: 'jhi-unified-feedback',
    standalone: true,
    templateUrl: './unified-feedback.component.html',
    styleUrls: ['./unified-feedback.component.scss'],
    imports: [NgClass, FaIconComponent, TooltipModule],
})
export class UnifiedFeedbackComponent implements AfterViewInit {
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);
    private localeConversionService = inject(LocaleConversionService);

    feedbackContent = input<string>('');
    points = input<number>(0);
    scoreAccuracy = input<number | undefined>(undefined);
    type = input<FeedbackType | undefined>(undefined);
    title = input<string | undefined>(undefined);
    reference = input<string | undefined>(undefined);
    feedback = input<Feedback | undefined>(undefined);
    assessmentsNames = input<AssessmentNamesForModelId | undefined>(undefined);
    showReference = input<boolean>(true);

    editable = input<boolean>(false);
    isSuggestion = input<boolean>(false);
    readOnly = input<boolean>(false);
    useDefaultFeedbackSuggestionBadgeText = input<boolean>(false);
    highlightDifferences = input<boolean>(false);

    feedbackTitle = model<string | undefined>(undefined);
    feedbackDetail = model<string | undefined>(undefined);
    feedbackCredits = model<number>(0);

    readonly onDelete = output<void>();
    readonly onAcceptSuggestion = output<void>();
    readonly onDiscardSuggestion = output<void>();

    private readonly detailTextarea = viewChild<ElementRef<HTMLTextAreaElement>>('detailTextarea');
    private readonly confirmIcon = viewChild(ConfirmIconComponent);

    private readonly feedbackTypeConfigs: Record<FeedbackType, FeedbackTypeConfig> = {
        correct: { icon: faCheck, alertClass: 'alert-success' },
        needs_revision: { icon: faMessage, alertClass: 'alert-primary' },
        not_attempted: { icon: faTimes, alertClass: 'alert-secondary' },
        non_compliant: { icon: faTimes, alertClass: 'alert-danger' },
    };

    private readonly feedbackTypeTitleKeys: Record<FeedbackType, string> = {
        correct: 'artemisApp.feedback.type.positive',
        needs_revision: 'artemisApp.feedback.type.feedback',
        not_attempted: 'artemisApp.feedback.type.notAttempted',
        non_compliant: 'artemisApp.feedback.type.needsRevision',
    };

    private readonly effectivePoints = computed(() => (this.editable() ? this.feedbackCredits() : this.points()));

    readonly inferredType = computed(() => {
        const explicitType = this.type();
        if (explicitType) {
            return explicitType;
        }

        if (this.feedback()?.isSubsequent) {
            return 'needs_revision';
        }

        const points = this.effectivePoints();
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

        return this.artemisTranslatePipe.transform(this.feedbackTypeTitleKeys[this.inferredType()]);
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

    readonly formattedPoints = computed(() => {
        const points = this.points();
        const formatted = this.localeConversionService.toLocaleString(points, this.scoreAccuracy());
        const key = `artemisApp.assessment.detail.points.${Math.abs(points) === 1 ? 'one' : 'many'}`;
        return this.artemisTranslatePipe.transform(key, { points: formatted });
    });

    readonly displayTitle = computed(() => this.stripFeedbackSuggestionPrefix(this.feedbackTitle() ?? ''));

    readonly defaultTitlePlaceholder = computed(() => this.artemisTranslatePipe.transform(this.feedbackTypeTitleKeys[this.inferredType()]));

    readonly canDismissWithoutConfirm = computed(() => this.feedbackCredits() === 0 && (this.feedbackDetail() ?? '').length === 0);

    private currentTitlePrefix(): string {
        const raw = this.feedbackTitle() ?? '';
        for (const prefix of [FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER, FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER]) {
            if (raw.startsWith(prefix)) {
                return prefix;
            }
        }
        return '';
    }

    onTitleInput(value: string): void {
        this.feedbackTitle.set(`${this.currentTitlePrefix()}${value}`);
    }

    handleDeleteConfirmed(): void {
        this.onDelete.emit();
    }

    toggleDeleteConfirm(): void {
        if (this.canDismissWithoutConfirm()) {
            this.handleDeleteConfirmed();
        } else {
            this.confirmIcon()?.toggle();
        }
    }

    focusTextarea(): void {
        const textarea = this.detailTextarea()?.nativeElement;
        textarea?.focus();
        this.autogrowDetailTextarea();
    }

    onDetailInput(): void {
        this.autogrowDetailTextarea();
    }

    ngAfterViewInit(): void {
        if (this.editable()) {
            this.autogrowDetailTextarea();
        }
    }

    private autogrowDetailTextarea(): void {
        const textarea = this.detailTextarea()?.nativeElement;
        if (!textarea) {
            return;
        }
        textarea.style.height = '0px';
        textarea.style.height = `${textarea.scrollHeight}px`;
    }

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
                return this.stripFeedbackSuggestionPrefix(feedback.text);
            }
            if (Feedback.isFeedbackSuggestion(feedback)) {
                return this.stripFeedbackSuggestionPrefix(feedback.text);
            }
            // Only use feedback.text as title when detailText exists as separate content;
            // otherwise text is used as content by buildFeedbackTextForReview and would duplicate here.
            if (feedback.detailText) {
                return feedback.text;
            }
            return this.artemisTranslatePipe.transform(this.feedbackTypeTitleKeys[this.inferredType()]);
        }

        if (this.assessmentsNames() && feedback.referenceId) {
            const assessmentName = this.assessmentsNames()![feedback.referenceId];
            if (assessmentName) {
                return `${assessmentName.type}: ${assessmentName.name}`;
            }
        }
        return this.artemisTranslatePipe.transform(this.feedbackTypeTitleKeys[this.inferredType()]);
    }

    private getReferencedFeedbackReference(feedback: Feedback): string | undefined {
        if (this.assessmentsNames() && feedback.referenceId) {
            const assessmentName = this.assessmentsNames()![feedback.referenceId];
            if (assessmentName) {
                return `${assessmentName.type} ${assessmentName.name}`;
            }
        }
        if (feedback.reference) {
            return feedback.reference;
        }
        return undefined;
    }
}
