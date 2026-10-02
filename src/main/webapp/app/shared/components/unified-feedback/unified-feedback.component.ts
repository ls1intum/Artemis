import { Component, DestroyRef, ElementRef, afterNextRender, afterRenderEffect, computed, effect, inject, input, model, output, untracked, viewChild } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faExclamationTriangle, faMinus, faPlus, faQuestionCircle, faTimes, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import {
    FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER,
    FEEDBACK_SUGGESTION_IDENTIFIER,
    Feedback,
    NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER,
    STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER,
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
        TumAetUiTooltipDirective,
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

        // The points choose the default title, and they can change from outside the points field (e.g. a dropped
        // grading instruction), so a default title follows every points change, not only the field's own handlers.
        // Those handlers still refresh it themselves: this effect only runs on the next change detection, while
        // consumers read the title right away when the points change is emitted.
        effect(() => {
            this.feedbackCredits();
            untracked(() => this.refreshDefaultTitle());
        });
    }

    feedbackContent = input<string>('');
    points = input<number>(0);
    scoreAccuracy = input<number | undefined>(undefined);
    type = input<FeedbackType | undefined>(undefined);
    title = input<string | undefined>(undefined);
    reference = input<string | undefined>(undefined);
    // Plain label shown before the reference chip (e.g. "Attribute"), matching how Apollon's own
    // feedback popup separates the element's type from its highlighted name chip.
    referenceType = input<string | undefined>(undefined);
    // Whether the reference chip repeats its text as a tooltip, which only helps when a long reference (e.g. a modeling
    // element name) is cut off. A short one, such as the "Lines 7-11" of programming inline feedback, turns it off.
    referenceTooltip = input<boolean>(true);
    feedback = input<Feedback | undefined>(undefined);
    assessmentsNames = input<AssessmentNamesForModelId | undefined>(undefined);
    showReference = input<boolean>(true);

    editable = input<boolean>(false);
    readOnly = input<boolean>(false);
    highlightDifferences = input<boolean>(false);
    /**
     * Whether a referenced feedback is also flagged when its description is missing. Unreferenced feedback always is;
     * a consumer whose referenced feedback cannot be saved without a description (e.g. programming inline feedback)
     * turns this on so the assessor sees why saving is blocked.
     */
    detailRequired = input<boolean>(false);

    feedbackTitle = model<string | undefined>(undefined);
    feedbackDetail = model<string | undefined>(undefined);
    feedbackCredits = model<number | undefined>(0);

    readonly onDelete = output<void>();

    private readonly detailTextarea = viewChild<ElementRef<HTMLTextAreaElement>>('detailTextarea');
    private readonly titleTextarea = viewChild<ElementRef<HTMLTextAreaElement>>('titleTextarea');
    private readonly creditsInput = viewChild<ElementRef<HTMLInputElement>>('creditsInput');
    private readonly confirmIcon = viewChild(ConfirmIconComponent);

    private readonly feedbackTypeConfigs: Record<FeedbackType, FeedbackTypeConfig> = {
        correct: { icon: faCheck, alertClass: 'unified-feedback--success' },
        needs_revision: { icon: faExclamationTriangle, alertClass: 'unified-feedback--info' },
        not_attempted: { icon: faMinus, alertClass: 'unified-feedback--neutral' },
        non_compliant: { icon: faTimes, alertClass: 'unified-feedback--danger' },
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

    readonly displayTitle = computed(() => Feedback.stripSuggestionPrefix(this.feedbackTitle() ?? ''));

    readonly defaultTitlePlaceholder = computed(() => this.artemisTranslatePipe.transform(this.feedbackTypeTitleKeys[this.inferredType()]));

    /** Plain method, not computed: see {@link gradingInstructionText} for why this must re-read on every call. */
    canDismissWithoutConfirm(): boolean {
        return (
            (this.feedbackCredits() ?? 0) === 0 &&
            (this.feedbackDetail() ?? '').length === 0 &&
            this.displayTitle().length === 0 &&
            !this.feedback()?.gradingInstruction &&
            !this.feedback()?.id
        );
    }

    readonly detailPlaceholder = computed(() => this.artemisTranslatePipe.transform('artemisApp.assessment.feedbackCommentPlaceholder'));

    /** Plain method, not computed: see {@link gradingInstructionText} for why this must re-read on every call. */
    isDetailMissing(): boolean {
        const detailRequired = this.detailRequired() || !this.feedback()?.reference;
        return this.editable() && detailRequired && !this.feedbackDetail() && !this.feedback()?.gradingInstruction?.feedback;
    }

    /**
     * Plain method, not computed: see {@link gradingInstructionText}. Says what the student reads (see getFeedbackBodyText): an AI
     * suggestion with a description shows only that description, any other feedback the criterion's text and its description.
     */
    rubricHint(): string {
        const feedback = this.feedback();
        const showsOnlyDescription = !!feedback && Feedback.isAIFeedback(feedback) && !!this.feedbackDetail();
        return this.artemisTranslatePipe.transform(showsOnlyDescription ? 'artemisApp.assessment.feedbackHintAiSuggestion' : 'artemisApp.assessment.feedbackHint');
    }
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
        const instructionText = this.feedback()?.gradingInstruction?.feedback;
        // Once the criterion's text is in the description, as after dropping it on a tutor's own feedback, the label would repeat it.
        // It stays shown while the description lacks the text, which is exactly when the student reads the two separately.
        return instructionText && !(this.feedbackDetail() ?? '').includes(instructionText) ? instructionText : undefined;
    }

    /**
     * A plain method, not a computed: tutor-training's `markAllFeedbackToCorrect()` and `markWrongFeedback()`
     * mutate `feedback().correctionStatus` in place, so a computed signal keyed on the `feedback` input would
     * never see its dependency change and would keep returning a stale label.
     */
    correctionStatusLabel(): string | undefined {
        const status = this.feedback()?.correctionStatus;
        return status ? this.artemisTranslatePipe.transform(`artemisApp.exampleSubmission.feedback.${status}`) : undefined;
    }

    /** Plain method, not computed: see {@link correctionStatusLabel} for why this must re-read on every call. */
    isCorrectionStatusCorrect(): boolean {
        return this.feedback()?.correctionStatus === 'CORRECT';
    }

    /** A non-graded feedback suggestion (an Athena hint with no credits) has no points pill in read-only mode. */
    readonly showPoints = computed(() => {
        const feedback = this.feedback();
        return !feedback || !Feedback.isNonGradedFeedbackSuggestion(feedback);
    });

    /** Usage-limit-excess feedback whose credits are excluded from the total score, e.g. a subsequent SCA finding beyond the configured limit. */
    readonly isSubsequentFeedback = computed(() => !!this.feedback()?.isSubsequent);

    readonly subsequentFeedbackTooltip = computed(() => this.artemisTranslatePipe.transform('artemisApp.assessment.subsequentFeedback'));

    protected readonly Feedback = Feedback;
    protected readonly faTimes = faTimes;
    protected readonly faTrashAlt = faTrashAlt;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faMinus = faMinus;
    protected readonly faPlus = faPlus;

    /** Points are graded in half steps throughout Artemis, so the stepper moves in the same increments. */
    protected readonly CREDITS_STEP = 0.5;

    /** Bounds for a single feedback's points, so a mistyped large number cannot distort the total score. */
    protected readonly CREDITS_MIN = -100;
    protected readonly CREDITS_MAX = 100;

    /** Matches Apollon's give-feedback title cap, so a title stays a headline well under the 500-character text column. */
    protected readonly TITLE_MAX_LENGTH = 100;

    /** Matches Apollon's give-feedback description cap, shown to the assessor as an `n/500` counter. */
    protected readonly DETAIL_MAX_LENGTH = 500;

    readonly detailLength = computed(() => (this.feedbackDetail() ?? '').length);

    /**
     * The credits input's raw, not-yet-committed text, captured on a stepper button's `mousedown` (which always
     * fires before the button steals focus and blurs the input). Without this, clicking a stepper after typing an
     * off-grid value like `1.3` blurs the input first, which snaps it to `1.5` via {@link onCreditsChange} before
     * the button's own `click` handler runs, and stepping from the already-snapped value overshoots. Consumed
     * exactly once by {@link stepCredits}.
     */
    private pendingRawCredits: string | undefined;

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
        const title = this.feedbackTitle();
        if (title) {
            this.feedbackTitle.set(Feedback.markAdaptedIfAcceptedSuggestion(title));
        }
    }

    onTitleInput(value: string): void {
        this.feedbackTitle.set(`${this.nextTitlePrefix()}${value}`);
    }

    /**
     * Writes the points-based default title if the title is empty, and keeps it following the points until the
     * assessor types one. An edited feedback thus never keeps an empty title: this runs when the assessor leaves the
     * title field, and consumers call it when they commit a feedback whose title field was never visited.
     */
    applyDefaultTitleIfEmpty(): void {
        // While the assessor is still in the title field (e.g. cleared it to type a new one), filling it would put
        // the default in front of what they type; leaving the field fills it instead.
        const titleInput = this.titleTextarea()?.nativeElement;
        const isTyping = !!titleInput && titleInput === titleInput.ownerDocument.activeElement;
        if (!this.isTitleEditable() || this.displayTitle().trim() || isTyping) {
            return;
        }
        this.feedbackTitle.set(`${this.nextTitlePrefix()}${this.defaultTitlePlaceholder()}`);
    }

    /**
     * Keeps a default title in line with the points, which choose it. A title counts as default when it is one of the
     * points-based defaults, not by remembering who wrote it, so this also holds once a new inline feedback is
     * re-rendered after its first commit, or when an assessment is reopened. An untouched (accepted) suggestion is
     * never rewritten, so opening one whose own title happens to match a default does not mark it adapted.
     */
    private refreshDefaultTitle(): void {
        if (!this.isTitleEditable() || (this.feedbackTitle() ?? '').startsWith(FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER)) {
            return;
        }
        const currentTitle = this.displayTitle().trim();
        const defaultTitle = this.defaultTitlePlaceholder();
        if (currentTitle !== defaultTitle && this.isDefaultTitle(currentTitle)) {
            this.feedbackTitle.set(`${this.nextTitlePrefix()}${defaultTitle}`);
        }
    }

    /** The title is only ever written while the assessor can edit it, never for a read-only or displayed-only feedback. */
    private isTitleEditable(): boolean {
        return this.editable() && !this.readOnly();
    }

    private isDefaultTitle(title: string): boolean {
        return Object.values(this.feedbackTypeTitleKeys).some((key) => this.artemisTranslatePipe.transform(key) === title);
    }

    onTitleTextareaInput(): void {
        this.autogrowTitleTextarea();
    }

    onDetailChange(value: string): void {
        this.feedbackDetail.set(value);
        this.markAdaptedIfSuggestion();
        // The moment a feedback gets content (a description or points), an empty title is filled with the default,
        // so a feedback whose title field was never visited is not saved without a heading.
        this.applyDefaultTitleIfEmpty();
    }

    /**
     * Clearing the field must invalidate the score immediately, without waiting for `change` to fire on blur. A value
     * beyond the bounds is pulled back to the bound while typing, like Apollon's points input; snapping onto the
     * half-point grid still waits for `change`, so typing an in-range value is not interrupted.
     */
    onCreditsInput(rawValue: string): void {
        if (rawValue.trim() === '') {
            this.feedbackCredits.set(undefined);
            this.markAdaptedIfSuggestion();
            this.refreshDefaultTitle();
            return;
        }
        const parsed = Number(rawValue);
        if (Number.isFinite(parsed) && (parsed > this.CREDITS_MAX || parsed < this.CREDITS_MIN)) {
            this.onCreditsChange(parsed);
        }
    }

    onCreditsChange(value: number): void {
        const normalized = this.normalizedCredits(value);
        this.feedbackCredits.set(normalized);
        // [ngModel] is one-way here, and set() is a no-op under Object.is when normalization lands back on the
        // value the signal already held (e.g. 1.6 snapping to an already-current 1.5), so the DOM would otherwise
        // keep showing the un-normalized value the tutor typed while the stored/saved credits differ from it.
        const input = this.creditsInput()?.nativeElement;
        if (input) {
            input.value = normalized === undefined ? '' : String(normalized);
        }
        this.markAdaptedIfSuggestion();
        this.refreshDefaultTitle();
        this.applyDefaultTitleIfEmpty();
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
        const base = this.consumePendingRawCredits() ?? this.feedbackCredits() ?? 0;
        const snapped = (delta > 0 ? Math.floor(base / this.CREDITS_STEP) : Math.ceil(base / this.CREDITS_STEP)) * this.CREDITS_STEP;
        this.onCreditsChange(snapped + delta);
    }

    /** See {@link pendingRawCredits}. */
    stashRawCredits(rawValue: string): void {
        this.pendingRawCredits = rawValue;
    }

    private consumePendingRawCredits(): number | undefined {
        const raw = this.pendingRawCredits;
        this.pendingRawCredits = undefined;
        if (raw === undefined || raw.trim() === '') {
            return undefined;
        }
        const parsed = Number(raw);
        return Number.isFinite(parsed) ? parsed : undefined;
    }

    private normalizedCredits(value: number | null | undefined): number | undefined {
        if (value === null || value === undefined || !Number.isFinite(value)) {
            return undefined;
        }
        const snapped = Math.round(value / this.CREDITS_STEP) * this.CREDITS_STEP;
        return Math.min(this.CREDITS_MAX, Math.max(this.CREDITS_MIN, snapped));
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

    private getReferencedFeedbackTitle(feedback: Feedback): string {
        if (feedback.text) {
            // An assessor may clear a suggestion's title, leaving only its prefix; show the same default title the
            // editor offered as placeholder instead of an empty heading.
            if (Feedback.isFeedbackSuggestion(feedback)) {
                return Feedback.stripSuggestionPrefix(feedback.text).trim() || this.defaultTitlePlaceholder();
            }
            if (Feedback.isNonGradedFeedbackSuggestion(feedback)) {
                return feedback.text.slice(NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER.length).trim() || this.defaultTitlePlaceholder();
            }
            if (Feedback.isStaticCodeAnalysisFeedback(feedback)) {
                return feedback.text.slice(STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER.length);
            }
            // Without a body of its own, feedback.text is a comment written before titles existed; buildFeedbackTextForReview
            // shows it as content, so it gets the default title instead of being repeated here.
            if (Feedback.isTextTitle(feedback)) {
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
