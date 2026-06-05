import { Injectable, signal } from '@angular/core';

/**
 * Dev-only mock store for which exercises a student has handed in (submitted) per course-level exercise
 * group. Shared between the group detail page (which records a submission) and the sidebar (which warns
 * when a group still has no hand-in). Signal-based so consumers — e.g. the sidebar's computed data —
 * react when a selection is submitted. Nothing is persisted; the store resets on reload.
 */
@Injectable({ providedIn: 'root' })
export class GroupHandInSelectionService {
    private readonly selections = signal<ReadonlyMap<number, number[]>>(new Map());

    /** Exercise ids the student has submitted for hand-in for the given group (empty if none yet). */
    getSubmittedSelection(groupId: number): number[] {
        return this.selections().get(groupId) ?? [];
    }

    /** Whether the student has handed in at least one exercise for the given group. */
    hasSubmittedSelection(groupId: number): boolean {
        return (this.selections().get(groupId)?.length ?? 0) > 0;
    }

    /** Records the submitted hand-in selection for a group (replacing any previous one). */
    submitSelection(groupId: number, exerciseIds: number[]): void {
        const next = new Map(this.selections());
        next.set(groupId, exerciseIds.slice());
        this.selections.set(next);
    }
}
