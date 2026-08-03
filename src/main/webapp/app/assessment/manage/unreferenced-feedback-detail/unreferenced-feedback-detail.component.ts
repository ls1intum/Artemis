import { Component, OnInit, computed, inject, input, model, output } from '@angular/core';
import { faCheck, faExclamation, faExclamationTriangle, faMinus, faPlus, faTrash, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
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
import { TumUiCardComponent } from 'app/shared-ui/tum-ui/card/tum-ui-card.component';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiButtonDirective } from 'app/shared-ui/tum-ui/button/tum-ui-button.directive';
import { TumUiInputDirective } from 'app/shared-ui/tum-ui/input/tum-ui-input.directive';
import { TumUiTagComponent, TumUiTagSeverity } from 'app/shared-ui/tum-ui/tag/tum-ui-tag.component';
import { TumUiInputGroupComponent } from 'app/shared-ui/tum-ui/input-group/tum-ui-input-group.component';
import { TumUiInputGroupAddonComponent } from 'app/shared-ui/tum-ui/input-group/tum-ui-input-group-addon.component';
import { pointsLabel, pointsSeverity } from 'app/exercise/structured-grading-criterion/grading-points-display.util';

/** Awarded / deducted / neutral — drives the card's left accent stripe. */
export type FeedbackTone = 'positive' | 'negative' | 'neutral';

/** Points are graded in half steps throughout Artemis, so the stepper and the native input use the same one. */
const CREDITS_STEP = 0.5;

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

    // Icons
    faTrashAlt = faTrashAlt;
    faExclamation = faExclamation;
    faExclamationTriangle = faExclamationTriangle;
    faCheck = faCheck;
    faTrash = faTrash;
    faMinus = faMinus;
    faPlus = faPlus;

    // Expose to template
    protected readonly Feedback = Feedback;
    readonly ButtonSize = ButtonSize;
    readonly CREDITS_STEP = CREDITS_STEP;

    /** Whether this feedback awards, deducts or changes nothing — the card's left accent stripe follows it. */
    readonly tone = computed<FeedbackTone>(() => {
        const credits = this.feedback().credits ?? 0;
        if (credits > 0) {
            return 'positive';
        }
        return credits < 0 ? 'negative' : 'neutral';
    });

    /** Severity of the read-only point pill (green awarded / red deducted / neutral). */
    readonly pointsSeverity = computed<TumUiTagSeverity>(() => pointsSeverity(this.feedback().credits));

    /** Signed, compact label of the read-only point pill, e.g. `+10`, `-5`. */
    readonly pointsLabel = computed(() => pointsLabel(this.feedback().credits));

    /**
     * Points of a feedback linked to a grading instruction are owned by that instruction and cannot be edited, so
     * such a feedback (and any read-only card) shows the points as a static pill instead of the editable stepper.
     */
    readonly pointsDisabled = computed(() => !!this.feedback().gradingInstruction || this.readOnly());

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
        if (feedback.type === FeedbackType.AUTOMATIC) {
            feedback.type = FeedbackType.AUTOMATIC_ADAPTED;
        }
        Feedback.updateFeedbackTypeOnChange(feedback);
        this.feedback.set(feedback);
        this.onFeedbackChange.emit(feedback);
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
        const base = feedback.credits ?? 0;
        // Snap a hand-typed value onto the half-point grid in the direction of travel first, so stepping up from
        // 1.3 lands on 1.5 (the next grid point) rather than skipping to 2.
        const snapped = (delta > 0 ? Math.floor(base / CREDITS_STEP) : Math.ceil(base / CREDITS_STEP)) * CREDITS_STEP;
        feedback.credits = snapped + delta;
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
}
