import { Component, DestroyRef, ElementRef, afterNextRender, afterRenderEffect, computed, inject, input, model, output, viewChild } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faExclamationTriangle, faMinus, faPlus, faQuestionCircle, faTimes, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
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
import { GradingInstructionLinkIconComponent } from 'app/shared-ui/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { FeedbackSuggestionBadgeComponent } from 'app/exercise/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/manage/unreferenced-feedback-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { FormsModule } from '@angular/forms';

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
    imports: [
        NgClass,
        FaIconComponent,
        TumUiTooltipDirective,
        FormsModule,
        ConfirmIconComponent,
        GradingInstructionLinkIconComponent,
        FeedbackSuggestionBadgeComponent,
        AssessmentCorrectionRoundBadgeComponent,
        ArtemisTranslatePipe,
    ],
})
export class UnifiedFeedbackComponent {
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);
    private localeConversionService = inject(LocaleConversionService);
    private destroyRef = inject(DestroyRef);

    constructor() {
        // afterRenderEffect (not effect): the textarea DOM read/write here must happen after the view -
        // and its viewChild query - is guaranteed to be resolved, which a plain effect() does not promise
        // on its first flush. A brand-new feedback item (e.g. an AI suggestion appended to the list) mounts
        // with feedbackDetail already populated, so that first flush is exactly when this matters.
        afterRenderEffect(() => {
            this.feedbackDetail();
            this.feedbackTitle();
            if (this.editable()) {
                this.autogrowDetailTextarea();
                this.autogrowTitleTextarea();
            }
        });

        // Re-measuring only on feedbackDetail/feedbackTitle changes misses the case where a textarea's own
        // available width shrinks later (e.g. a modeling exercise's Apollon diagram finishes laying out after an
        // AI feedback suggestion already landed) - the text then wraps into more lines than the height that was
        // computed at mount time, with nothing left to trigger a re-measure. Watch width specifically (not
        // the observed box as a whole) so our own height writes below don't retrigger this callback.
        afterNextRender(() => {
            const detailTextarea = this.detailTextarea()?.nativeElement;
            const titleTextarea = this.titleTextarea()?.nativeElement;
            if (!detailTextarea && !titleTextarea) {
                return;
            }
            let lastDetailWidth = detailTextarea?.clientWidth;
            let lastTitleWidth = titleTextarea?.clientWidth;
            const resizeObserver = new ResizeObserver((entries) => {
                for (const entry of entries) {
                    const width = entry.contentRect.width;
                    if (entry.target === detailTextarea && width !== lastDetailWidth) {
                        lastDetailWidth = width;
                        this.autogrowDetailTextarea();
                    } else if (entry.target === titleTextarea && width !== lastTitleWidth) {
                        lastTitleWidth = width;
                        this.autogrowTitleTextarea();
                    }
                }
            });
            if (detailTextarea) {
                resizeObserver.observe(detailTextarea);
            }
            if (titleTextarea) {
                resizeObserver.observe(titleTextarea);
            }
            this.destroyRef.onDestroy(() => resizeObserver.disconnect());
        });
    }

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
    readOnly = input<boolean>(false);
    highlightDifferences = input<boolean>(false);
    /**
     * Whether the title may be edited while {@link editable} is true. Consumers that derive the title themselves on
     * save (e.g. the programming inline feedback, which auto-generates "File X at line Y" for non-suggestions) set
     * this to false so the UI never invites editing a value that would be discarded. Defaults to true.
     */
    titleEditable = input<boolean>(true);

    feedbackTitle = model<string | undefined>(undefined);
    feedbackDetail = model<string | undefined>(undefined);
    feedbackCredits = model<number>(0);

    readonly onDelete = output<void>();

    private readonly detailTextarea = viewChild<ElementRef<HTMLTextAreaElement>>('detailTextarea');
    private readonly titleTextarea = viewChild<ElementRef<HTMLTextAreaElement>>('titleTextarea');
    private readonly confirmIcon = viewChild(ConfirmIconComponent);

    private readonly feedbackTypeConfigs: Record<FeedbackType, FeedbackTypeConfig> = {
        correct: { icon: faCheck, alertClass: 'alert-success' },
        needs_revision: { icon: faMinus, alertClass: 'alert-primary' },
        not_attempted: { icon: faMinus, alertClass: 'alert-secondary' },
        non_compliant: { icon: faTimes, alertClass: 'alert-danger' },
    };

    private readonly feedbackTypeTitleKeys: Record<FeedbackType, string> = {
        correct: 'artemisApp.feedback.type.positive',
        needs_revision: 'artemisApp.feedback.type.feedback',
        not_attempted: 'artemisApp.feedback.type.notAttempted',
        non_compliant: 'artemisApp.feedback.type.needsRevision',
    };

    private readonly effectivePoints = computed(() => (this.editable() ? (this.feedbackCredits() ?? 0) : this.points()));

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

    /** Signed, compact points label for the read-only pill, e.g. `+10`, `-5`, `0` (no "Point(s)" word). */
    readonly pointsLabel = computed(() => {
        const points = this.points();
        const formatted = this.localeConversionService.toLocaleString(points, this.scoreAccuracy());
        return points > 0 ? `+${formatted}` : formatted;
    });

    /** Read-only pill color: follows the sign of the points value directly, independent of the inferred/explicit type. */
    readonly pointsSeverity = computed<'positive' | 'negative' | 'neutral'>(() => {
        const points = this.points();
        if (points > 0) {
            return 'positive';
        }
        if (points < 0) {
            return 'negative';
        }
        return 'neutral';
    });

    readonly displayTitle = computed(() => this.stripFeedbackSuggestionPrefix(this.feedbackTitle() ?? ''));

    readonly defaultTitlePlaceholder = computed(() => this.artemisTranslatePipe.transform(this.feedbackTypeTitleKeys[this.inferredType()]));

    readonly canDismissWithoutConfirm = computed(
        () =>
            (this.feedbackCredits() ?? 0) === 0 &&
            (this.feedbackDetail() ?? '').length === 0 &&
            this.displayTitle().length === 0 &&
            !this.feedback()?.gradingInstruction &&
            !this.feedback()?.id,
    );

    readonly detailPlaceholder = computed(() => this.artemisTranslatePipe.transform('artemisApp.assessment.feedbackCommentPlaceholder'));
    readonly isDetailMissing = computed(() => this.editable() && !this.feedback()?.reference && !this.feedbackDetail());
    readonly rubricHint = computed(() => this.artemisTranslatePipe.transform('artemisApp.assessment.feedbackHint'));
    readonly dismissTooltip = computed(() => this.artemisTranslatePipe.transform('artemisApp.textAssessment.feedbackEditor.dismissFeedback'));
    readonly dismissConfirmTooltip = computed(() => this.artemisTranslatePipe.transform('artemisApp.textAssessment.feedbackEditor.dismissFeedbackConfirmation'));
    readonly pointsAriaLabel = computed(() => this.artemisTranslatePipe.transform('artemisApp.exercise.score'));
    readonly feedbackDetailAriaLabel = computed(() => this.artemisTranslatePipe.transform('artemisApp.assessment.feedback'));
    /**
     * A plain method, not a computed: consumers (drag-and-drop rubric assignment, the rubric dropdown) mutate
     * `feedback().gradingInstruction` in place rather than replacing the feedback object, so a computed signal
     * keyed on the `feedback` input would never see its dependency change and would keep returning a stale value.
     */
    gradingInstructionText(): string | undefined {
        return this.feedback()?.gradingInstruction?.feedback;
    }
    readonly correctionStatusLabel = computed(() => {
        const status = this.feedback()?.correctionStatus;
        return status ? this.artemisTranslatePipe.transform(`artemisApp.exampleSubmission.feedback.${status}`) : undefined;
    });
    readonly isCorrectionStatusCorrect = computed(() => this.feedback()?.correctionStatus === 'CORRECT');

    /** A non-graded feedback suggestion (an Athena hint with no credits) has no points pill in read-only mode. */
    readonly showPoints = computed(() => {
        const feedback = this.feedback();
        return !feedback || !Feedback.isNonGradedFeedbackSuggestion(feedback);
    });

    protected readonly Feedback = Feedback;
    protected readonly faTimes = faTimes;
    protected readonly faTrashAlt = faTrashAlt;
    protected readonly faCheck = faCheck;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faMinus = faMinus;
    protected readonly faPlus = faPlus;

    /** Points are graded in half steps throughout Artemis, so the stepper moves in the same increments. */
    protected readonly CREDITS_STEP = 0.5;

    /** Plain method, not computed: see {@link gradingInstructionText} for why this must re-read on every call. */
    protected stepCreditsDisabled(): boolean {
        return this.readOnly() || !!this.feedback()?.gradingInstruction;
    }

    private currentTitlePrefix(): string {
        const raw = this.feedbackTitle() ?? '';
        for (const prefix of [FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER, FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER]) {
            if (raw.startsWith(prefix)) {
                return prefix;
            }
        }
        return '';
    }

    /**
     * The prefix to write on the next edit: an accepted suggestion transitions to adapted the moment it is
     * touched; every other state (already adapted, not a suggestion, or the unreachable bare "suggested") is
     * left as-is. This is a one-way, sticky transition — it never reverts even if the edit is undone later.
     */
    private nextTitlePrefix(): string {
        const current = this.currentTitlePrefix();
        return current === FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER ? FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER : current;
    }

    /**
     * Rewrites feedbackTitle's suggestion prefix from accepted to adapted, if applicable, without changing the
     * title text itself. Called whenever the description or the score changes, since neither of those edits
     * goes through onTitleInput.
     */
    private markAdaptedIfSuggestion(): void {
        const current = this.currentTitlePrefix();
        if (current !== FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER) {
            return;
        }
        const title = (this.feedbackTitle() ?? '').slice(current.length);
        this.feedbackTitle.set(`${FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER}${title}`);
    }

    onTitleInput(value: string): void {
        this.feedbackTitle.set(`${this.nextTitlePrefix()}${value}`);
    }

    onTitleTextareaInput(): void {
        this.autogrowTitleTextarea();
    }

    onDetailChange(value: string): void {
        this.feedbackDetail.set(value);
        this.markAdaptedIfSuggestion();
    }

    onCreditsChange(value: number): void {
        this.feedbackCredits.set(this.normalizedCredits(value));
        this.markAdaptedIfSuggestion();
    }

    /**
     * Increments or decrements the points by one half-point step, mirroring what typing into the field does. A
     * hand-typed value is snapped onto the half-point grid in the direction of travel first, so stepping up from
     * 1.3 lands on 1.5 (the next grid point) rather than skipping to 2.
     * @param delta the signed step to apply
     */
    stepCredits(delta: number): void {
        if (this.stepCreditsDisabled()) {
            return;
        }
        const base = this.feedbackCredits() ?? 0;
        const snapped = (delta > 0 ? Math.floor(base / this.CREDITS_STEP) : Math.ceil(base / this.CREDITS_STEP)) * this.CREDITS_STEP;
        this.onCreditsChange(snapped + delta);
    }

    private normalizedCredits(value: number | null | undefined): number {
        if (value === null || value === undefined || !Number.isFinite(value)) {
            return 0;
        }
        return Math.round(value / this.CREDITS_STEP) * this.CREDITS_STEP;
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

    private autogrowDetailTextarea(): void {
        const textarea = this.detailTextarea()?.nativeElement;
        if (!textarea) {
            return;
        }
        textarea.style.height = '0px';
        textarea.style.height = `${textarea.scrollHeight}px`;
    }

    private autogrowTitleTextarea(): void {
        const textarea = this.titleTextarea()?.nativeElement;
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
