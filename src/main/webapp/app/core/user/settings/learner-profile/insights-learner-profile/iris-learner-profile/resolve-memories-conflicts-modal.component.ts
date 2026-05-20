import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MemirisMemoryWithRelationsDTO } from 'app/iris/shared/entities/memiris.model';
import { IrisMemoriesHttpService } from 'app/iris/overview/services/iris-memories-http.service';
import { AlertService } from 'app/shared/service/alert.service';
import { firstValueFrom } from 'rxjs';

@Component({
    selector: 'jhi-resolve-memories-conflicts-modal',
    standalone: true,
    imports: [CommonModule, TranslateDirective],
    templateUrl: './resolve-memories-conflicts-modal.component.html',
})
export class ResolveMemoriesConflictsModalComponent implements OnInit {
    private readonly activeModal = inject(NgbActiveModal);
    private readonly irisMemoriesHttpService = inject(IrisMemoriesHttpService);
    private readonly alertService = inject(AlertService);

    // Regular properties that can be set from parent
    conflictGroups: string[][] = [];
    details: Record<string, MemirisMemoryWithRelationsDTO | undefined> = {};

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

    /**
     * Initializes local modal state from the provided conflict groups.
     */
    ngOnInit(): void {
        const inputGroups = this.conflictGroups ?? [];
        this.groups.set(inputGroups.map((arr) => [...arr]));
        this.currentIndex.set(0);
    }

    /** Closes the modal without applying changes. */
    close(): void {
        this.activeModal.dismiss();
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
                this.activeModal.close(this.deletedIds);
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
