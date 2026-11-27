import { Service, Signal, computed, signal } from '@angular/core';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

/**
 * A grading instruction as it is dragged onto an assessment. It also names its criterion, which an instruction does not
 * carry itself, because Apollon's assessment box shows that title on the instruction it links.
 */
export type GradingInstructionWithCriterionTitle = GradingInstruction & { criterionTitle?: string };

/** The instruction without the criterion title it carries for display, so that the title is never stored with a feedback. */
export function withoutCriterionTitle({ criterionTitle: _criterionTitle, ...instruction }: GradingInstructionWithCriterionTitle): GradingInstruction {
    return instruction;
}

/**
 * Contract fulfilled by the feedback list of an assessment editor.
 */
export interface GradingInstructionSelectionHost {
    /** Ids of the grading instructions that anywhere in the assessment have at least one feedback linked to them. */
    readonly appliedInstructionIds: Signal<ReadonlySet<number>>;

    /**
     * How often each grading instruction is currently applied anywhere in the assessment.
     * Used to keep drag-and-drop enabled until a finite {@link GradingInstruction.usageCount} is reached.
     */
    readonly appliedInstructionCounts: Signal<ReadonlyMap<number, number>>;

    /** Ids of the applied instructions whose feedback the host owns and can therefore remove again. */
    readonly removableInstructionIds: Signal<ReadonlySet<number>>;

    /** Adds one feedback linked to the given instruction. */
    applyInstruction(instruction: GradingInstruction): void;

    /** Removes one feedback linked to the given instruction (owned by this host). */
    unapplyOneInstruction(instruction: GradingInstruction): void;

    /** Removes every feedback linked to the given instruction. */
    unapplyInstruction(instruction: GradingInstruction): void;
}

const NO_APPLIED_INSTRUCTIONS: ReadonlySet<number> = new Set<number>();
const NO_APPLIED_COUNTS: ReadonlyMap<number, number> = new Map<number, number>();

/**
 * Mediates between the structured grading instruction list and the editable feedback list of the currently open
 * assessment. Exactly one host is registered at a time.
 */
@Service()
export class GradingInstructionSelectionService {
    private readonly host = signal<GradingInstructionSelectionHost | undefined>(undefined);
    private readonly reservedInstructions = signal<ReadonlyMap<object, GradingInstruction>>(new Map());

    /** Instruction selected for application to the next feedback target. */
    private readonly armedInstruction = signal<GradingInstruction | undefined>(undefined);

    /** True while an armed instruction can still be applied to the next feedback target within its usage limit. */
    readonly hasArmedInstruction = computed(() => {
        const instruction = this.armedInstruction();
        return instruction !== undefined && this.canApplyInstruction(instruction);
    });
    private readonly criteria = signal<readonly GradingCriterion[]>([]);

    /** True while an editable feedback list is mounted. */
    readonly isSelectable = computed(() => this.host() !== undefined);

    /** Ids of the instructions currently applied anywhere in the open assessment. */
    readonly appliedInstructionIds = computed(() => {
        const applied = new Set(this.host()?.appliedInstructionIds() ?? NO_APPLIED_INSTRUCTIONS);
        if (this.host()) {
            for (const instruction of this.reservedInstructions().values()) {
                if (instruction.id !== undefined) {
                    applied.add(instruction.id);
                }
            }
        }
        return applied;
    });

    /** How often each instruction is currently applied anywhere in the open assessment. */
    readonly appliedInstructionCounts = computed(() => {
        const host = this.host();
        if (!host) {
            return NO_APPLIED_COUNTS;
        }
        const counts = new Map(host.appliedInstructionCounts());
        for (const instruction of this.reservedInstructions().values()) {
            if (instruction.id !== undefined) {
                counts.set(instruction.id, (counts.get(instruction.id) ?? 0) + 1);
            }
        }
        return counts;
    });

    /** Ids of the applied instructions the registered feedback list can remove again. */
    readonly removableInstructionIds = computed(() => this.host()?.removableInstructionIds() ?? NO_APPLIED_INSTRUCTIONS);

    register(host: GradingInstructionSelectionHost): void {
        this.clearArmedInstruction();
        this.reservedInstructions.set(new Map());
        this.host.set(host);
    }
    unregister(host: GradingInstructionSelectionHost): void {
        if (this.host() === host) {
            this.host.set(undefined);
            this.reservedInstructions.set(new Map());
        }
    }

    /** Counts a draft instruction link until its card is saved or canceled. */
    reserveInstruction(owner: object, instruction?: GradingInstruction): void {
        if (this.reservedInstructions().get(owner)?.id === instruction?.id) {
            return;
        }
        const reservations = new Map(this.reservedInstructions());
        if (instruction?.id !== undefined) {
            reservations.set(owner, instruction);
        } else {
            reservations.delete(owner);
        }
        this.reservedInstructions.set(reservations);
    }

    /** Called by the grading instructions panel with the criteria of the open assessment, so that an instruction can be named by its criterion. */
    setCriteria(criteria: readonly GradingCriterion[]): void {
        this.criteria.set(criteria);
    }

    /** The title of the criterion the instruction belongs to, if the grading instructions panel lists it. */
    criterionTitleOf(instruction: GradingInstruction): string | undefined {
        if (instruction.id === undefined) {
            return undefined;
        }
        const criterion = this.criteria().find((candidate) => candidate.structuredGradingInstructions?.some((listed) => listed.id === instruction.id));
        return criterion?.title?.trim() || undefined;
    }

    /** The instruction together with the title of its criterion, the way Apollon's assessment box shows a linked instruction. */
    withCriterionTitle(instruction: GradingInstruction): GradingInstructionWithCriterionTitle {
        return cloneWith(instruction, { criterionTitle: this.criterionTitleOf(instruction) });
    }

    isApplied(instruction: GradingInstruction): boolean {
        return instruction.id !== undefined && this.appliedInstructionIds().has(instruction.id);
    }

    /** How often the instruction is currently applied anywhere in the assessment. */
    applicationCount(instruction: GradingInstruction): number {
        if (instruction.id === undefined) {
            return 0;
        }
        return this.appliedInstructionCounts().get(instruction.id) ?? 0;
    }

    /** Whether another application fits the instruction's finite usage limit. */
    canApplyInstruction(instruction?: GradingInstruction): boolean {
        const usageLimit = instruction?.usageCount ?? 0;
        return !instruction || usageLimit <= 0 || this.applicationCount(instruction) < usageLimit;
    }

    /**
     * Whether the registered feedback list can take the instruction back. False while it is applied only to a
     * referenced element, which owns its feedback itself.
     */
    isRemovable(instruction: GradingInstruction): boolean {
        return instruction.id !== undefined && this.removableInstructionIds().has(instruction.id);
    }

    /**
     * Applies or removes the instruction in the registered feedback list.
     * @param instruction the instruction whose checkbox was toggled
     * @param applied the new checkbox state
     */
    setApplied(instruction: GradingInstruction, applied: boolean): void {
        const host = this.host();
        if (!host) {
            return;
        }
        if (applied) {
            if (!this.canApplyInstruction(instruction)) {
                return;
            }
            host.applyInstruction(instruction);
        } else {
            host.unapplyInstruction(instruction);
        }
    }

    isArmed(instruction: GradingInstruction): boolean {
        const armed = this.armedInstruction();
        return armed !== undefined && (armed === instruction || (armed.id !== undefined && armed.id === instruction.id));
    }

    toggleArmedInstruction(instruction: GradingInstruction): void {
        this.armedInstruction.set(this.isArmed(instruction) ? undefined : instruction);
    }

    /** Takes and clears the armed instruction. The caller checks the usage limit when it links the instruction. */
    consumeArmedInstruction(): GradingInstruction | undefined {
        const instruction = this.armedInstruction();
        this.armedInstruction.set(undefined);
        return instruction;
    }

    /** Drops an unconsumed armed instruction (assessment teardown / new host registration). */
    clearArmedInstruction(): void {
        this.armedInstruction.set(undefined);
    }

    /** Removes one application of the instruction owned by the registered feedback list. */
    removeOneApplication(instruction: GradingInstruction): void {
        this.host()?.unapplyOneInstruction(instruction);
    }
}
