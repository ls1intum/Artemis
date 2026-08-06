import { Injectable, Signal, computed, signal } from '@angular/core';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';

/**
 * Contract fulfilled by the feedback list of an assessment editor.
 */
export interface GradingInstructionSelectionHost {
    /** Ids of the grading instructions that currently have at least one feedback linked to them. */
    readonly appliedInstructionIds: Signal<ReadonlySet<number>>;

    /** Number of feedback entries currently linked to each grading instruction id. */
    readonly appliedInstructionCounts: Signal<ReadonlyMap<number, number>>;

    /** Adds one feedback linked to the given instruction. */
    applyInstruction(instruction: GradingInstruction): void;

    /** Removes every feedback linked to the given instruction. */
    unapplyInstruction(instruction: GradingInstruction): void;
}

const NO_APPLIED_INSTRUCTIONS: ReadonlySet<number> = new Set<number>();
const NO_APPLIED_INSTRUCTION_COUNTS: ReadonlyMap<number, number> = new Map<number, number>();

/**
 * Mediates between the structured grading instruction list and the editable feedback list of the currently open
 * assessment. Exactly one host is registered at a time.
 */
@Injectable({ providedIn: 'root' })
export class GradingInstructionSelectionService {
    private readonly host = signal<GradingInstructionSelectionHost | undefined>(undefined);

    /**
     * Feedback that uses up instruction usages without being part of the host list, e.g. the inline feedback placed
     * directly in the code. It only adds to the usage counts: the checkbox may add to and remove from the host list
     * alone, so its state keeps following the host.
     */
    private readonly usageSources = signal<readonly Signal<readonly Feedback[]>[]>([]);

    /** True while an editable feedback list is mounted. */
    readonly isSelectable = computed(() => this.host() !== undefined);

    /** Ids of the instructions currently applied in the registered feedback list. */
    readonly appliedInstructionIds = computed(() => this.host()?.appliedInstructionIds() ?? NO_APPLIED_INSTRUCTIONS);

    /** Number of times each instruction is used, across the registered feedback list and all usage sources. */
    readonly appliedInstructionCounts = computed<ReadonlyMap<number, number>>(() => {
        const sources = this.usageSources();
        if (sources.length === 0) {
            return this.host()?.appliedInstructionCounts() ?? NO_APPLIED_INSTRUCTION_COUNTS;
        }
        const counts = new Map(this.host()?.appliedInstructionCounts() ?? []);
        for (const source of sources) {
            for (const feedback of source()) {
                const instructionId = feedback.gradingInstruction?.id;
                if (instructionId !== undefined) {
                    counts.set(instructionId, (counts.get(instructionId) ?? 0) + 1);
                }
            }
        }
        return counts;
    });

    register(host: GradingInstructionSelectionHost): void {
        this.host.set(host);
    }
    unregister(host: GradingInstructionSelectionHost): void {
        if (this.host() === host) {
            this.host.set(undefined);
        }
    }

    registerUsageSource(source: Signal<readonly Feedback[]>): void {
        this.usageSources.update((sources) => [...sources, source]);
    }
    unregisterUsageSource(source: Signal<readonly Feedback[]>): void {
        this.usageSources.update((sources) => sources.filter((registered) => registered !== source));
    }

    isApplied(instruction: GradingInstruction): boolean {
        return instruction.id !== undefined && this.appliedInstructionIds().has(instruction.id);
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
