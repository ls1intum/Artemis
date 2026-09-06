import { Component, OnInit, computed, inject, input, model, output, signal } from '@angular/core';
import { faCheck, faExclamation, faExclamationTriangle, faMinus, faPaste, faPlus, faTrash, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { Subject } from 'rxjs';
import { FeedbackService } from 'app/exercise/feedback/services/feedback.service';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { FaIconComponent, FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { AssessmentCorrectionRoundBadgeComponent } from './assessment-correction-round-badge/assessment-correction-round-badge.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FeedbackContentPipe } from 'app/foundation/pipes/feedback-content.pipe';
import { QuotePipe } from 'app/foundation/pipes/quote.pipe';
import { FeedbackSuggestionBadgeComponent } from 'app/exercise/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';
import {
    TumUiButtonComponent,
    TumUiButtonDirective,
    TumUiCardComponent,
    TumUiInputDirective,
    TumUiInputGroupAddonComponent,
    TumUiInputGroupComponent,
    TumUiTagComponent,
    TumUiTagSeverity,
} from '@tumaet/ui-angular';
import { CREDITS_STEP, normalizedCredits, pointsLabel, pointsSeverity, steppedCredits } from 'app/exercise/structured-grading-criterion/grading-points-display.util';

/** Awarded / deducted / neutral — drives the card's left accent stripe. */
export type FeedbackTone = 'positive' | 'negative' | 'neutral';

@Component({
    selector: 'jhi-unreferenced-feedback-detail',
    templateUrl: './unreferenced-feedback-detail.component.html',
    styleUrls: ['./unreferenced-feedback-detail.component.scss'],
    imports: [
        FeedbackSuggestionBadgeComponent,
        DeleteButtonDirective,
        FaIconComponent,
        TranslateDirective,
        FormsModule,
        FaLayersComponent,
        AssessmentCorrectionRoundBadgeComponent,
        ArtemisTranslatePipe,
        FeedbackContentPipe,
        QuotePipe,
        TumUiCardComponent,
        TumUiButtonComponent,
        TumUiButtonDirective,
        TumUiInputDirective,
        TumUiTagComponent,
        TumUiInputGroupComponent,
        TumUiInputGroupAddonComponent,
    ],
})
export class UnreferencedFeedbackDetailComponent implements OnInit {
    structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    private readonly selectionService = inject(GradingInstructionSelectionService);

    // Parent matches feedback by reference (`indexOf`); mutate in place on edit.
    public readonly feedback = model.required<Feedback>();
    readonly resultId = input.required<number>();
    readonly isSuggestion = input<boolean>();
    public readonly readOnly = input.required<boolean>();
    readonly highlightDifferences = input<boolean>(false);
    readonly useDefaultFeedbackSuggestionBadgeText = input.required<boolean>();

    public readonly onFeedbackChange = output<Feedback>();
    public readonly onFeedbackDelete = output<Feedback>();
    readonly onAcceptSuggestion = output<Feedback>();
    readonly onDiscardSuggestion = output<Feedback>();
    private feedbackService = inject(FeedbackService);

    /** Shows the apply-armed-instruction control while an instruction is armed. */
    protected readonly isKeyboardDropTarget = computed(() => !this.readOnly() && !this.isSuggestion() && this.selectionService.hasArmedInstruction());

    // Icons
    faTrashAlt = faTrashAlt;
    faExclamation = faExclamation;
    faExclamationTriangle = faExclamationTriangle;
    faCheck = faCheck;
    faTrash = faTrash;
    faMinus = faMinus;
    faPaste = faPaste;
    faPlus = faPlus;

    // Expose to template
    protected readonly Feedback = Feedback;
    readonly ButtonSize = ButtonSize;
    readonly CREDITS_STEP = CREDITS_STEP;

    /** Stable per-card id so repeated unreferenced cards do not share the same control ids. */
    private static nextInstanceId = 0;
    private readonly instanceId = UnreferencedFeedbackDetailComponent.nextInstanceId++;
    protected readonly pointsInputId = `feedback-points-${this.instanceId}`;
    protected readonly headerInputId = `feedback-header-${this.instanceId}`;
    protected readonly textareaId = `feedback-textarea-${this.instanceId}`;

    /**
     * Parent matches feedback by reference, so edits mutate in place and {@link feedback} may not notify.
     * Bumping this re-runs credit-dependent template bindings.
     */
    private readonly creditsEpoch = signal(0);

    /** Card accent stripe: follows credits; reads {@link creditsEpoch} so in-place edits still refresh. */
    protected cardTone(): FeedbackTone {
        this.creditsEpoch();
        return this.toneForCredits(this.feedback().credits);
    }

    protected toneForCredits(credits: number | undefined): FeedbackTone {
        const value = credits ?? 0;
        if (value > 0) {
            return 'positive';
        }
        return value < 0 ? 'negative' : 'neutral';
    }

    /** Severity of the read-only point pill (green awarded / red deducted / neutral). */
    protected pointsSeverityFor(credits: number | undefined): TumUiTagSeverity {
        return pointsSeverity(credits);
    }

    /** Signed, compact label of the read-only point pill, e.g. `+10`, `-5`. */
    protected pointsLabelFor(credits: number | undefined): string {
        return pointsLabel(credits);
    }

    /**
     * Points of a feedback linked to a grading instruction are owned by that instruction and cannot be edited, so
     * such a feedback (and any read-only card) shows the points as a static pill instead of the editable stepper.
     * Plain method: instruction drops mutate {@link feedback} in place without a new signal identity.
     */
    protected pointsDisabled(): boolean {
        return !!this.feedback().gradingInstruction || this.readOnly();
    }

    /** Optional header for manual feedback; AI suggestion title from {@link Feedback.text}. Hidden when linked to an instruction. */
    protected showHeaderSection(): boolean {
        const feedback = this.feedback();
        if (feedback.gradingInstruction) {
            return false;
        }
        if (this.headerReadOnly()) {
            return !!Feedback.getDisplayTitle(feedback);
        }
        return true;
    }

    readonly headerReadOnly = computed(() => this.readOnly() || this.isSuggestion());

    protected displayTitle(): string | undefined {
        return Feedback.getDisplayTitle(this.feedback());
    }
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    ngOnInit() {
        void this.loadLongFeedback();
    }

    /**
     * Call this method to load long feedback if needed
     */
    public async loadLongFeedback() {
        const feedback = this.feedback();
        if (feedback.id && feedback.hasLongFeedbackText) {
            feedback.detailText = await this.feedbackService.getLongFeedbackText(feedback.id);
            this.feedback.set(feedback);
            this.onFeedbackChange.emit(feedback);
        }
    }

    /**
     * Emits assessment changes to parent component
     */
    public emitChanges(): void {
        const feedback = this.feedback();
        feedback.credits = normalizedCredits(feedback.credits);
        if (feedback.type === FeedbackType.AUTOMATIC) {
            feedback.type = FeedbackType.AUTOMATIC_ADAPTED;
        }
        Feedback.updateFeedbackTypeOnChange(feedback);
        this.feedback.set(feedback);
        this.creditsEpoch.update((epoch) => epoch + 1);
        this.onFeedbackChange.emit(feedback);
    }

    updateCredits(credits: number | null | undefined): void {
        this.feedback().credits = normalizedCredits(credits);
        this.emitChanges();
    }

    /**
     * Increments or decrements the points by one half-point step, mirroring what typing into the field does.
     * @param delta the signed step to apply
     */
    stepCredits(delta: number): void {
        if (this.pointsDisabled()) {
            return;
        }
        const feedback = this.feedback();
        feedback.credits = steppedCredits(feedback.credits, delta);
        this.emitChanges();
    }

    /**
     * Emits the deletion of a feedback
     */
    public delete() {
        this.onFeedbackDelete.emit(this.feedback());
        this.dialogErrorSource.next('');
    }

    updateFeedbackOnDrop(event: Event) {
        event.stopPropagation();
        const feedback = this.feedback();
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(feedback, event);
        this.feedback.set(feedback);
        this.onFeedbackChange.emit(feedback);
    }

    /** Applies a previously armed instruction to this feedback (keyboard stand-in for drop). */
    applyArmedInstruction(): void {
        if (!this.isKeyboardDropTarget()) {
            return;
        }
        if (!this.structuredGradingCriterionService.applyArmedInstructionToFeedback(this.feedback())) {
            return;
        }
        const feedback = this.feedback();
        this.feedback.set(feedback);
        this.onFeedbackChange.emit(feedback);
    }

    updateHeaderTitle(title: string): void {
        const feedback = this.feedback();
        const prefix = feedback.text ? Feedback.getFeedbackSuggestionPrefix(feedback.text) : undefined;
        if (prefix) {
            feedback.text = prefix + title;
        } else {
            feedback.text = title || undefined;
        }
        this.emitChanges();
    }
}
