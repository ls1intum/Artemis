import { Injectable, Signal, computed, signal } from '@angular/core';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';

/**
 * Contract fulfilled by the feedback list of an assessment editor.
 */
export interface GradingInstructionSelectionHost {
    /** Ids of the grading instructions that anywhere in the assessment have at least one feedback linked to them. */
    readonly appliedInstructionIds: Signal<ReadonlySet<number>>;

    /** Ids of the applied instructions whose feedback the host owns and can therefore remove again. */
    readonly removableInstructionIds: Signal<ReadonlySet<number>>;

    /** Adds one feedback linked to the given instruction. */
    applyInstruction(instruction: GradingInstruction): void;

    /** Removes every feedback linked to the given instruction. */
    unapplyInstruction(instruction: GradingInstruction): void;
}

const NO_APPLIED_INSTRUCTIONS: ReadonlySet<number> = new Set<number>();

/**
 * Mediates between the structured grading instruction list and the editable feedback list of the currently open
 * assessment. Exactly one host is registered at a time.
 */
@Injectable({ providedIn: 'root' })
export class GradingInstructionSelectionService {
    private readonly host = signal<GradingInstructionSelectionHost | undefined>(undefined);

    /** True while an editable feedback list is mounted. */
    readonly isSelectable = computed(() => this.host() !== undefined);

    /** Ids of the instructions currently applied anywhere in the open assessment. */
    readonly appliedInstructionIds = computed(() => this.host()?.appliedInstructionIds() ?? NO_APPLIED_INSTRUCTIONS);

    /** Ids of the applied instructions the registered feedback list can remove again. */
    readonly removableInstructionIds = computed(() => this.host()?.removableInstructionIds() ?? NO_APPLIED_INSTRUCTIONS);

    register(host: GradingInstructionSelectionHost): void {
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
}
