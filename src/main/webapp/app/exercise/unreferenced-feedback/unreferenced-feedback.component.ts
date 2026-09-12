import { Component, computed, effect, inject, input, model } from '@angular/core';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { UnreferencedFeedbackDetailComponent } from 'app/assessment/manage/unreferenced-feedback-detail/unreferenced-feedback-detail.component';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingInstructionSelectionHost, GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { TumUiButtonDirective, TumUiMessageComponent, TumUiTagComponent, TumUiTagSeverity } from '@tumaet/ui-angular';
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
    readonly resultId = input<number>(undefined!);

    /**
     * Criteria of the assessed exercise; used to group the feedback cards by the criterion they belong to.
     */
    readonly gradingCriteria = input<GradingCriterion[]>([]);
    /**
     * Max points of the exercise including bonus points. Used to cap the displayed final score the same way the
     * assessment save path does.
     */
    readonly maxPoints = input<number>();
    /**
     * Complete assessment feedback (referenced + unreferenced + automatic, where applicable). When provided, group
     * and final totals follow the structured-grading usageCount rules and include every score-contributing item.
     */
    readonly allFeedbacks = input<Feedback[]>([]);
    /**
     * Whether the automatic test points are capped before the manual points are added, as programming exercises
     * grade them. Passed on to {@link StructuredGradingCriterionService.computeAssessmentScore}.
     */
    readonly capAutomaticTestSubtotal = input(false);

    /**
     * In order to make it possible to mark unreferenced feedback based on the correction status, we assign reference ids to the unreferenced feedback
     */
    readonly addReferenceIdForExampleSubmission = input(false);

    readonly feedbacks = model<Feedback[]>([]);

    /** Feedback used for scoring: full assessment when the parent supplies it, otherwise this list only. */
    private readonly scoringFeedbacks = computed(() => {
        const allFeedbacks = this.allFeedbacks();
        return allFeedbacks.length > 0 ? allFeedbacks : this.feedbacks();
    });

    /**
     * The score of the whole assessment, computed exactly as saving it would. Also carries how many points each
     * single feedback contributes, which is what the per-criterion tags show.
     */
    private readonly assessmentScore = computed(() => {
        const maxPoints = this.maxPoints();
        // The exercise may not be loaded yet; capping against 0 points would wrongly show a score of 0 until it is.
        const cap = maxPoints !== undefined && maxPoints > 0 ? maxPoints : Number.POSITIVE_INFINITY;
        return this.structuredGradingCriterionService.computeAssessmentScore(this.scoringFeedbacks(), cap, this.capAutomaticTestSubtotal());
    });

    /**
     * How often each grading instruction is applied anywhere in the assessment, including on referenced elements
     * such as a line of code or a diagram element.
     */
    readonly appliedInstructionCounts = computed<ReadonlyMap<number, number>>(() => instructionCountsOf(this.scoringFeedbacks()));

    /**
     * Ids of the grading instructions applied anywhere in the assessment. Derived from
     * {@link appliedInstructionCounts} so the checkbox and the usage-aware drag gate share one source of truth.
     */
    readonly appliedInstructionIds = computed<ReadonlySet<number>>(() => new Set(this.appliedInstructionCounts().keys()));

    /**
     * Ids of the applied instructions this list can take back again, i.e. those that produced feedback it owns.
     * An instruction applied only to a referenced element has to be removed on that element instead.
     */
    readonly removableInstructionIds = computed<ReadonlySet<number>>(() => new Set(instructionCountsOf(this.feedbacks()).keys()));

    /**
     * The feedback cards split into one block per grading criterion (criteria in alphabetical order), with every
     * feedback that belongs to no criterion collected in a trailing block.
     */
    readonly feedbackGroups = computed<FeedbackGroup[]>(() => {
        const feedbacks = this.feedbacks();
        const contributingCredits = this.assessmentScore().contributions;
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
            groups.push(toGroup(criterion.title ?? '', false, groupFeedbacks, contributingCredits));
        }

        const ungrouped = feedbacks.filter((feedback) => !alreadyGrouped.has(feedback));
        if (ungrouped.length > 0) {
            groups.push(toGroup('artemisApp.assessment.detail.otherFeedback', true, ungrouped, contributingCredits));
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

    /**
     * Awarded / deducted / final points for the assessment, using the same structured-grading usage and
     * positive/max-point capping as the assessment save path.
     */
    readonly pointsSummary = computed(() => {
        const { awarded, deducted, total } = this.assessmentScore();
        return { awarded, deducted, total };
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
        const indexToUpdate = feedback.id != undefined ? unreferencedFeedback.findIndex((existing) => existing.id === feedback.id) : unreferencedFeedback.indexOf(feedback);
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

    createAssessmentOnDrop(event: Event) {
        this.addUnreferencedFeedback();
        const newFeedback: Feedback | undefined = this.unreferencedFeedback.last();
        if (newFeedback) {
            this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(newFeedback, event);
            this.updateFeedback(newFeedback);
        }
    }
}

/** How often each grading instruction appears among the given feedback. */
function instructionCountsOf(feedbacks: Feedback[]): ReadonlyMap<number, number> {
    const counts = new Map<number, number>();
    for (const feedback of feedbacks) {
        const instructionId = feedback.gradingInstruction?.id;
        if (instructionId !== undefined) {
            counts.set(instructionId, (counts.get(instructionId) ?? 0) + 1);
        }
    }
    return counts;
}

function toGroup(title: string, translateTitle: boolean, feedbacks: Feedback[], contributingCredits: Map<Feedback, number>): FeedbackGroup {
    const points = feedbacks.reduce((sum, feedback) => sum + (contributingCredits.get(feedback) ?? 0), 0);
    let pointsSeverity: TumUiTagSeverity = 'secondary';
    if (points > 0) {
        pointsSeverity = 'success';
    } else if (points < 0) {
        pointsSeverity = 'danger';
    }
    return { title, translateTitle, feedbacks, points, pointsSeverity };
}
