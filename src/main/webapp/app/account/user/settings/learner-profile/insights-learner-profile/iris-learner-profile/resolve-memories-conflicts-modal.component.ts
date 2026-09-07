import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumUiButtonComponent, TumUiButtonDirective, TumUiButtonGroupComponent, TumUiCardComponent } from '@tumaet/ui-angular';
import { MemirisMemoryWithRelationsDTO } from 'app/iris/shared/entities/memiris.model';
import { IrisMemoriesHttpService } from 'app/iris/overview/services/iris-memories-http.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { firstValueFrom } from 'rxjs';

@Component({
    selector: 'jhi-resolve-memories-conflicts-modal',
    standalone: true,
    imports: [CommonModule, TranslateDirective, TumUiButtonComponent, TumUiButtonDirective, TumUiButtonGroupComponent, TumUiCardComponent],
    templateUrl: './resolve-memories-conflicts-modal.component.html',
})
export class ResolveMemoriesConflictsModalComponent {
    private readonly irisMemoriesHttpService = inject(IrisMemoriesHttpService);
    private readonly alertService = inject(AlertService);

    readonly conflictGroups = input<string[][]>([]);
    readonly details = input<Record<string, MemirisMemoryWithRelationsDTO | undefined>>({});

    /** Emits the ids that were deleted once every conflict is resolved, so the host can apply them. */
    readonly resolved = output<string[]>();
    /** Emits when the user dismisses the dialog without finishing. */
    readonly closed = output<void>();

    // Local state
    groups = signal<string[][]>([]);
    currentIndex = signal<number>(0);
    busy = signal<boolean>(false);
    private deletedIds: string[] = [];

    currentGroup = computed<string[]>(() => {
        const idx = this.currentIndex();
        const g = this.groups();
        return g.length > 0 && idx >= 0 && idx < g.length ? g[idx] : [];
    });

    constructor() {
        // The host may hand over a fresh set of conflicts while the dialog stays mounted, so the working copy
        // follows the input rather than being taken once on init.
        effect(() => {
            this.groups.set(this.conflictGroups().map((group) => [...group]));
            this.currentIndex.set(0);
        });
    }

    /** Closes the modal without applying changes. */
    close(): void {
        this.closed.emit();
    }

    /**
     * Keeps a selected memory in the current conflict group and deletes the others.
     * On completion, advances to the next group or closes the modal.
     * @param _groupIndex The current group index (unused; relies on signal state).
     * @param keepId The memory id to retain.
     */
    async keep(_groupIndex: number, keepId: string): Promise<void> {
        const group = this.currentGroup();
        if (!group?.length) return;
        const toDelete = group.filter((id) => id !== keepId);
        this.busy.set(true);
        try {
            for (const id of toDelete) {
                try {
                    await firstValueFrom(this.irisMemoriesHttpService.deleteUserMemory(id));
                    this.deletedIds.push(id);
                } catch (error) {
                    this.alertService.error('artemisApp.iris.memories.error.deleteFailed');
                }
            }
            // Remove resolved group
            const g = [...this.groups()];
            g.splice(this.currentIndex(), 1);
            this.groups.set(g);
            // Move to next available group or finish
            if (g.length === 0) {
                this.resolved.emit(this.deletedIds);
            } else if (this.currentIndex() >= g.length) {
                this.currentIndex.set(g.length - 1);
            }
        } finally {
            this.busy.set(false);
        }
    }

    /** Moves to the previous conflict group if available. */
    prev(): void {
        const idx = this.currentIndex();
        if (idx > 0) this.currentIndex.set(idx - 1);
    }

    /** Moves to the next conflict group if available. */
    next(): void {
        const idx = this.currentIndex();
        if (idx < this.groups().length - 1) this.currentIndex.set(idx + 1);
    }
}
