import { Component, computed, effect, inject, input, model, output } from '@angular/core';
import { FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_IDENTIFIER, Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { UnreferencedFeedbackDetailComponent } from 'app/assessment/manage/unreferenced-feedback-detail/unreferenced-feedback-detail.component';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingInstructionSelectionHost, GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { TumUiButtonDirective } from 'app/shared-ui/tum-ui/button/tum-ui-button.directive';
import { TumUiTagComponent, TumUiTagSeverity } from 'app/shared-ui/tum-ui/tag/tum-ui-tag.component';
import { TumUiMessageComponent } from 'app/shared-ui/tum-ui/message/tum-ui-message.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

//One rendered block of the feedback list: the feedback belonging to a single grading criterion.
export interface FeedbackGroup {
    title: string;
    translateTitle: boolean;
    feedbacks: Feedback[];
    points: number;
    pointsSeverity: TumUiTagSeverity;
}

@Component({
    selector: 'jhi-unreferenced-feedback',
    templateUrl: './unreferenced-feedback.component.html',
    styleUrls: ['./unreferenced-feedback.component.scss'],
    imports: [TranslateDirective, UnreferencedFeedbackDetailComponent, TumUiButtonDirective, TumUiTagComponent, TumUiMessageComponent, ArtemisTranslatePipe],
})
export class UnreferencedFeedbackComponent implements GradingInstructionSelectionHost {
    private structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    private readonly selectionService = inject(GradingInstructionSelectionService);

    FeedbackType = FeedbackType;

    assessmentsAreValid = false;

    readonly readOnly = input<boolean>(undefined!);
    readonly highlightDifferences = input<boolean>(undefined!);
    readonly useDefaultFeedbackSuggestionBadgeText = input(false);
    readonly resultId = input<number>(undefined!);

    /**
     * Criteria of the assessed exercise; used to group the feedback cards by the criterion they belong to.
     */
    readonly gradingCriteria = input<GradingCriterion[]>([]);
    readonly maxPoints = input<number>();

    /**
     * In order to make it possible to mark unreferenced feedback based on the correction status, we assign reference ids to the unreferenced feedback
     */
    readonly addReferenceIdForExampleSubmission = input(false);

    readonly feedbacks = model<Feedback[]>([]);
    readonly feedbackSuggestions = model<Feedback[]>([]);
    readonly onAcceptSuggestion = output<Feedback>();
    readonly onDiscardSuggestion = output<Feedback>();

    /** Ids of the grading instructions that are currently applied. */
    readonly appliedInstructionIds = computed<ReadonlySet<number>>(() => {
        const ids = new Set<number>();
        for (const feedback of this.feedbacks()) {
            const instructionId = feedback.gradingInstruction?.id;
            if (instructionId !== undefined) {
                ids.add(instructionId);
            }
        }
        return ids;
    });

    /**
     * The feedback cards split into one block per grading criterion (criteria in alphabetical order), with every
     * feedback that belongs to no criterion collected in a trailing block.
     */
    readonly feedbackGroups = computed<FeedbackGroup[]>(() => {
        const feedbacks = this.feedbacks();
        const groups: FeedbackGroup[] = [];
        const alreadyGrouped = new Set<Feedback>();

        const sortedCriteria = [...this.gradingCriteria()].sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
        for (const criterion of sortedCriteria) {
            const instructionIds = new Set((criterion.structuredGradingInstructions ?? []).map((instruction) => instruction.id));
            const groupFeedbacks = feedbacks.filter((feedback) => feedback.gradingInstruction?.id !== undefined && instructionIds.has(feedback.gradingInstruction.id));
            if (groupFeedbacks.length === 0) {
                continue;
            }
            groupFeedbacks.forEach((feedback) => alreadyGrouped.add(feedback));
            groups.push(toGroup(criterion.title, false, groupFeedbacks));
        }

        const ungrouped = feedbacks.filter((feedback) => !alreadyGrouped.has(feedback));
        if (ungrouped.length > 0) {
            groups.push(toGroup('artemisApp.assessment.detail.otherFeedback', true, ungrouped));
        }
        return groups;
    });

    /**
     * Group headers only add information once the feedback is actually split up.
     * A single block of uncategorized feedback is rendered without a header.
     */
    readonly showGroupHeaders = computed(() => {
        const groups = this.feedbackGroups();
        return groups.length > 1 || (groups.length === 1 && !groups[0].translateTitle);
    });

    /** Awarded, deducted and resulting points of the unreferenced feedback shown in this list. */
    readonly pointsSummary = computed(() => {
        const credits = this.feedbacks().map((feedback) => feedback.credits ?? 0);
        const awarded = credits.filter((credit) => credit > 0).reduce((sum, credit) => sum + credit, 0);
        const deducted = credits.filter((credit) => credit < 0).reduce((sum, credit) => sum + credit, 0);
        return { awarded, deducted, total: awarded + deducted };
    });

    constructor() {
        // The grading-instruction list lives in a different part of the assessment editor and reaches this list
        // through the selection service. Only an editable list may receive instructions.
        effect((onCleanup) => {
            if (this.readOnly()) {
                return;
            }
            this.selectionService.register(this);
            onCleanup(() => this.selectionService.unregister(this));
        });
    }

    get unreferencedFeedback(): Feedback[] {
        return this.feedbacks();
    }

    set unreferencedFeedback(feedbacks: Feedback[]) {
        this.feedbacks.set([...feedbacks]);
    }

    public deleteFeedback(feedbackToDelete: Feedback): void {
        const unreferencedFeedback = [...this.unreferencedFeedback];
        const indexToDelete = unreferencedFeedback.indexOf(feedbackToDelete);
        unreferencedFeedback.splice(indexToDelete, 1);
        this.unreferencedFeedback = unreferencedFeedback;
        this.validateFeedback();
    }

    /**
     * Validates the feedback:
     *   - There must be any form of feedback, either general feedback or feedback referencing a model element or both
     *   - Each reference feedback must have a score that is a valid number
     */
    validateFeedback() {
        if (!this.unreferencedFeedback || this.unreferencedFeedback.length === 0) {
            this.assessmentsAreValid = false;
            return;
        }
        for (const feedback of this.unreferencedFeedback) {
            if (feedback.credits == undefined || isNaN(feedback.credits)) {
                this.assessmentsAreValid = false;
                return;
            }
        }
        this.assessmentsAreValid = true;
    }

    /**
     * Update the feedback in the list of unreferenced feedback, changing or adding it.
     * @param feedback The feedback to update
     */
    updateFeedback(feedback: Feedback) {
        const unreferencedFeedback = [...this.unreferencedFeedback];
        const indexToUpdate = unreferencedFeedback.indexOf(feedback);
        if (indexToUpdate < 0) {
            unreferencedFeedback.push(feedback);
        } else {
            unreferencedFeedback[indexToUpdate] = feedback;
        }
        this.unreferencedFeedback = unreferencedFeedback;
        this.validateFeedback();
    }

    public addUnreferencedFeedback(): void {
        this.appendFeedback(this.createFeedback());
    }

    applyInstruction(instruction: GradingInstruction): void {
        const feedback = this.createFeedback();
        feedback.gradingInstruction = instruction;
        feedback.credits = instruction.credits;
        this.appendFeedback(feedback);
    }

    unapplyInstruction(instruction: GradingInstruction): void {
        const feedbacksToRemove = this.unreferencedFeedback.filter((feedback) => feedback.gradingInstruction?.id === instruction.id);
        feedbacksToRemove.forEach((feedback) => this.deleteFeedback(feedback));
    }

    private createFeedback(): Feedback {
        const feedback = new Feedback();
        feedback.type = FeedbackType.MANUAL_UNREFERENCED;

        // Assign the next id to the unreferenced feedback
        if (this.addReferenceIdForExampleSubmission()) {
            feedback.reference = this.generateNewUnreferencedFeedbackReference().toString();
        }
        return feedback;
    }

    private appendFeedback(feedback: Feedback): void {
        this.unreferencedFeedback = [...this.unreferencedFeedback, feedback];
        this.validateFeedback();
    }

    /**
     * Generate the new reference, by computing what is currently the maximum reference within all feedback and add 1
     */
    private generateNewUnreferencedFeedbackReference(): number {
        if (this.unreferencedFeedback.length === 0) {
            return 1;
        }

        const references = this.unreferencedFeedback.map((feedback) => {
            const id = +(feedback.reference ?? '0');
            if (isNaN(id)) {
                return 0;
            }
            return id;
        });
        return Math.max(...references.concat([0])) + 1;
    }

    /**
     * Accept a feedback suggestion: Make it "real" feedback and remove the suggestion card
     */
    acceptSuggestion(feedback: Feedback) {
        this.feedbackSuggestions.update((feedbackSuggestions) => feedbackSuggestions.filter((f) => f !== feedback)); // Remove the suggestion card
        // We need to change the feedback type to "manual" because non-manual feedback is never editable in the editor
        // and will be filtered out in all kinds of places
        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
        // Change the prefix "FeedbackSuggestion:" to "FeedbackSuggestion:accepted:"
        feedback.text = (feedback.text ?? FEEDBACK_SUGGESTION_IDENTIFIER).replace(FEEDBACK_SUGGESTION_IDENTIFIER, FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER);
        this.updateFeedback(feedback); // Make it "real" feedback
        this.onAcceptSuggestion.emit(feedback);
    }

    /**
     * Discard a feedback suggestion: Remove the suggestion card and emit the event
     */
    discardSuggestion(feedback: Feedback) {
        this.feedbackSuggestions.update((feedbackSuggestions) => feedbackSuggestions.filter((f) => f !== feedback)); // Remove the suggestion card
        this.onDiscardSuggestion.emit(feedback);
    }

    createAssessmentOnDrop(event: Event) {
        this.addUnreferencedFeedback();
        const newFeedback: Feedback | undefined = this.unreferencedFeedback.last();
        if (newFeedback) {
            this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(newFeedback, event);
            this.updateFeedback(newFeedback);
        }
    }
}

function toGroup(title: string, translateTitle: boolean, feedbacks: Feedback[]): FeedbackGroup {
    const points = feedbacks.reduce((sum, feedback) => sum + (feedback.credits ?? 0), 0);
    let pointsSeverity: TumUiTagSeverity = 'secondary';
    if (points > 0) {
        pointsSeverity = 'success';
    } else if (points < 0) {
        pointsSeverity = 'danger';
    }
    return { title, translateTitle, feedbacks, points, pointsSeverity };
}
