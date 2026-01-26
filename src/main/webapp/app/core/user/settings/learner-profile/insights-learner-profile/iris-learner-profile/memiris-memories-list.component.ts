import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { IrisMemoriesHttpService } from 'app/iris/overview/services/iris-memories-http.service';
import { MemirisMemory, MemirisMemoryDataDTO, MemirisMemoryWithRelationsDTO } from 'app/iris/shared/entities/memiris.model';
import { firstValueFrom } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbModalModule, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ResolveMemoriesConflictsModalComponent } from './resolve-memories-conflicts-modal.component';
import { MemirisMemoryDetailsComponent } from './memiris-memory-details.component';

@Component({
    selector: 'jhi-memiris-memories-list',
    imports: [CommonModule, TranslateDirective, FaIconComponent, NgbModalModule, MemirisMemoryDetailsComponent],
    templateUrl: './memiris-memories-list.component.html',
})
export class MemirisMemoriesListComponent implements OnInit {
    private readonly irisMemoriesHttpService = inject(IrisMemoriesHttpService);
    private readonly alertService = inject(AlertService);
    private readonly modalService = inject(NgbModal);

    // Signals for component state
    loading = signal<boolean>(false);
    deleting = signal<Record<string, boolean>>({});
    memoryData = signal<MemirisMemoryDataDTO | undefined>(undefined);
    open = signal<Record<string, boolean>>({});

    // Derived signals
    memories = computed<MemirisMemory[]>(() => this.memoryData()?.memories ?? []);
    conflictGroups = computed<string[][]>(() => {
        const md = this.memoryData();
        const connections = md?.connections ?? [];
        const validIds = new Set((md?.memories ?? []).map((m) => m.id));
        const groups: string[][] = [];
        const seen = new Set<string>();
        for (const c of connections) {
            const type = c.connectionType?.toUpperCase();
            const isConflict = type === 'CONFLICT' || type === 'CONTRADICTS';
            if (!isConflict || !c.id) continue;
            const ids = (c.memories ?? []).filter((id) => validIds.has(id));
            if (ids.length >= 2 && !seen.has(c.id)) {
                seen.add(c.id);
                groups.push(Array.from(new Set(ids)).sort());
            }
        }
        return groups;
    });
    hasConflicts = computed<boolean>(() => this.conflictGroups().length > 0);
    details = computed<Record<string, MemirisMemoryWithRelationsDTO | undefined>>(() => {
        const result: Record<string, MemirisMemoryWithRelationsDTO | undefined> = {};
        const openMap = this.open();
        for (const id of Object.keys(openMap)) {
            if (openMap[id]) {
                result[id] = this.buildDetails(id);
            }
        }
        return result;
    });

    // Icons
    protected readonly faChevronRight = faChevronRight;
    protected readonly faChevronDown = faChevronDown;

    /**
     * Loads aggregated memory data when the component initializes.
     */
    async ngOnInit(): Promise<void> {
        await this.loadMemories();
    }

    /**
     * Loads aggregated memory data for the user and computes conflict groups.
     */
    async loadMemories() {
        this.loading.set(true);
        try {
            const data = await firstValueFrom(this.irisMemoriesHttpService.getUserMemoryData());
            this.memoryData.set(data);
        } catch (error) {
            this.alertService.error('artemisApp.iris.memories.error.loadFailed');
        } finally {
            this.loading.set(false);
        }
    }

    /**
     * Deletes a memory and applies cached updates without reloading the list.
     * @param memory The memory to delete.
     */
    async deleteMemory(memory: MemirisMemory) {
        if (!memory?.id || this.deleting()[memory.id]) return;
        this.deleting.update((m) => ({ ...m, [memory.id]: true }));
        try {
            await firstValueFrom(this.irisMemoriesHttpService.deleteUserMemory(memory.id));
            this.applyDeletions([memory.id]);
        } catch (error) {
            this.alertService.error('artemisApp.iris.memories.error.deleteFailed');
        } finally {
            this.deleting.update((m) => ({ ...m, [memory.id]: false }));
        }
    }

    /**
     * Toggles expanded state of a memory and lazily computes its details.
     * @param memory The memory to toggle.
     */
    async toggleOpen(memory: MemirisMemory) {
        if (!memory?.id) return;
        const id = memory.id;
        const currentlyOpen = this.open()[id];
        this.open.update((o) => ({ ...o, [id]: !currentlyOpen }));
    }

    /**
     * Computes conflict groups from aggregated connections and precomputes details for involved memories.
     */
    // conflicts are derived via computed conflictGroups

    /**
     * Builds a detailed memory view from aggregated memory data.
     * @param id The memory id to resolve.
     * @returns A constructed details DTO or undefined if the id is unknown.
     */
    private buildDetails(id: string): MemirisMemoryWithRelationsDTO | undefined {
        const md = this.memoryData();
        if (!md) return undefined;
        const mem = md.memories.find((m) => m.id === id);
        if (!mem) return undefined;
        const validMemoryIds = new Set(md.memories.map((m) => m.id));
        const learnings = (md.learnings ?? []).filter((l) => (mem.learnings ?? []).includes(l.id) || (l.memories ?? []).includes(id));
        const connections = (md.connections ?? [])
            .filter((c) => (mem.connections ?? []).includes(c.id) || (c.memories ?? []).includes(id))
            .map((c) => ({ ...c, memories: (c.memories ?? []).filter((mid) => validMemoryIds.has(mid)) }));
        return {
            id: mem.id,
            title: mem.title,
            content: mem.content,
            sleptOn: mem.slept_on,
            deleted: mem.deleted,
            learnings,
            connections,
        };
    }

    /**
     * Opens the conflict resolution modal. Applies deletions silently on close.
     */
    openResolveConflictsModal() {
        const modalRef: NgbModalRef = this.modalService.open(ResolveMemoriesConflictsModalComponent, { size: 'lg', backdrop: 'static' });
        const groups = this.conflictGroups();
        const detailsMap: Record<string, MemirisMemoryWithRelationsDTO | undefined> = {};
        for (const g of groups) {
            for (const gid of g) {
                if (!detailsMap[gid]) detailsMap[gid] = this.buildDetails(gid);
            }
        }
        modalRef.componentInstance.conflictGroups = groups;
        modalRef.componentInstance.details = detailsMap;
        modalRef.result
            .then((deletedIds: string[] | undefined) => {
                if (Array.isArray(deletedIds) && deletedIds.length > 0) {
                    this.applyDeletions(deletedIds);
                }
            })
            .catch(() => {
                // Dismissed: keep current state without reloading
            });
    }

    /**
     * Applies deletions to the cached memory data and updates the UI without reloading.
     * @param deletedIds The list of memory ids to remove.
     */
    private applyDeletions(deletedIds: string[]) {
        if (!deletedIds?.length) return;
        const toDelete = new Set(deletedIds);
        const md = this.memoryData();
        if (md) {
            const updated: MemirisMemoryDataDTO = {
                memories: md.memories.filter((m) => !toDelete.has(m.id)),
                learnings: (md.learnings ?? []).map((l) => ({ ...l, memories: (l.memories ?? []).filter((id) => !toDelete.has(id)) })),
                connections: (md.connections ?? []).map((c) => ({ ...c, memories: (c.memories ?? []).filter((id) => !toDelete.has(id)) })),
            };
            this.memoryData.set(updated);
        }
        this.open.update((o) => {
            const copy = { ...o };
            for (const id of deletedIds) delete copy[id];
            return copy;
        });
        this.deleting.update((d) => {
            const copy = { ...d };
            for (const id of deletedIds) delete copy[id];
            return copy;
        });
    }
}
