import { Injectable, Signal, computed, signal } from '@angular/core';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';

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
@Injectable({ providedIn: 'root' })
export class GradingInstructionSelectionService {
    private readonly host = signal<GradingInstructionSelectionHost | undefined>(undefined);

    /**
     * Instruction armed by keyboard (Enter/Space) for the next feedback target without a checkbox host. Consumed by
     * the next target that accepts it (drop or keyboard), the drag-and-drop stand-in without a checkbox host.
     */
    private readonly armedInstruction = signal<GradingInstruction | undefined>(undefined);

    /** True while an instruction is armed for the next feedback target. */
    readonly hasArmedInstruction = computed(() => this.armedInstruction() !== undefined);

    /** True while an editable feedback list is mounted. */
    readonly isSelectable = computed(() => this.host() !== undefined);

    /** Ids of the instructions currently applied anywhere in the open assessment. */
    readonly appliedInstructionIds = computed(() => this.host()?.appliedInstructionIds() ?? NO_APPLIED_INSTRUCTIONS);

    /** How often each instruction is currently applied anywhere in the open assessment. */
    readonly appliedInstructionCounts = computed(() => this.host()?.appliedInstructionCounts() ?? NO_APPLIED_COUNTS);

    /** Ids of the applied instructions the registered feedback list can remove again. */
    readonly removableInstructionIds = computed(() => this.host()?.removableInstructionIds() ?? NO_APPLIED_INSTRUCTIONS);

    register(host: GradingInstructionSelectionHost): void {
        this.clearArmedInstruction();
        this.host.set(host);
    }
    unregister(host: GradingInstructionSelectionHost): void {
        if (this.host() === host) {
            this.host.set(undefined);
        }
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
            host.applyInstruction(instruction);
        } else {
            host.unapplyInstruction(instruction);
        }
    }

    /**
     * Arms an instruction for the next feedback drop or keyboard apply.
     */
    armInstruction(instruction: GradingInstruction): void {
        this.armedInstruction.set(instruction);
    }

    /** Takes and clears the keyboard-armed instruction when its live usage limit still allows an application. */
    consumeArmedInstruction(): GradingInstruction | undefined {
        const instruction = this.armedInstruction();
        this.armedInstruction.set(undefined);
        if (!instruction) {
            return undefined;
        }
        const usageLimit = instruction.usageCount ?? 0;
        return usageLimit > 0 && this.applicationCount(instruction) >= usageLimit ? undefined : instruction;
    }

    /** Drops an unconsumed armed instruction (assessment teardown / new host registration). */
    clearArmedInstruction(): void {
        this.armedInstruction.set(undefined);
    }

    /** Adds one more application of the instruction in the registered feedback list. */
    addApplication(instruction: GradingInstruction): void {
        this.host()?.applyInstruction(instruction);
    }

    /** Removes one application of the instruction owned by the registered feedback list. */
    removeOneApplication(instruction: GradingInstruction): void {
        this.host()?.unapplyOneInstruction(instruction);
    }
}
