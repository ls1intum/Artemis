import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { IrisMemoriesHttpService } from 'app/iris/overview/services/iris-memories-http.service';
import { MemirisMemory, MemirisMemoryWithRelationsDTO } from 'app/iris/shared/entities/memiris.model';
import { firstValueFrom } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbModalModule, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ResolveMemoriesConflictsModalComponent } from './resolve-memories-conflicts-modal.component';

@Component({
    selector: 'jhi-memiris-memories-list',
    imports: [CommonModule, TranslateDirective, FaIconComponent, NgbModalModule],
    templateUrl: './memiris-memories-list.component.html',
})
export class MemirisMemoriesListComponent implements OnInit {
    private readonly irisMemoriesHttpService = inject(IrisMemoriesHttpService);
    private readonly alertService = inject(AlertService);
    private readonly modalService = inject(NgbModal);

    loading = false;
    deleting: Record<string, boolean> = {};
    loadingDetails: Record<string, boolean> = {};
    memories: MemirisMemory[] = [];
    open: Record<string, boolean> = {};
    details: Record<string, MemirisMemoryWithRelationsDTO | undefined> = {};

    // Conflict handling
    hasConflicts = false;
    conflictGroups: string[][] = [];

    // Icons
    protected readonly faChevronRight = faChevronRight;
    protected readonly faChevronDown = faChevronDown;

    async ngOnInit(): Promise<void> {
        await this.loadMemories();
    }

    async loadMemories() {
        this.loading = true;
        try {
            this.memories = (await firstValueFrom(this.irisMemoriesHttpService.listUserMemories())) ?? [];
            await this.checkConflicts();
        } catch (error) {
            this.alertService.error('artemisApp.iris.memories.error.loadFailed');
        } finally {
            this.loading = false;
        }
    }

    async deleteMemory(memory: MemirisMemory) {
        if (!memory?.id || this.deleting[memory.id]) return;
        this.deleting[memory.id] = true;
        try {
            await firstValueFrom(this.irisMemoriesHttpService.deleteUserMemory(memory.id));
            await this.loadMemories();
        } catch (error) {
            this.alertService.error('artemisApp.iris.memories.error.deleteFailed');
        } finally {
            this.deleting[memory.id] = false;
        }
    }

    async toggleOpen(memory: MemirisMemory) {
        if (!memory?.id) return;
        const id = memory.id;
        const currentlyOpen = this.open[id];
        this.open[id] = !currentlyOpen;

        if (this.open[id] && !this.details[id] && !this.loadingDetails[id]) {
            this.loadingDetails[id] = true;
            try {
                this.details[id] = await firstValueFrom(this.irisMemoriesHttpService.getUserMemory(id));
            } catch (error) {
                this.alertService.error('artemisApp.iris.memories.error.detailsLoadFailed');
            } finally {
                this.loadingDetails[id] = false;
            }
        }
    }

    private async checkConflicts() {
        this.hasConflicts = false;
        this.conflictGroups = [];

        const groups: string[][] = [];
        const seenConflicts = new Set<string>(); // Track which conflict connections we've already processed

        // Fetch details only for memories with any connections to minimize calls
        for (const mem of this.memories.filter((m) => (m.connections?.length ?? 0) > 0)) {
            const id = mem.id;
            try {
                // Cache details if not present
                if (!this.details[id]) {
                    this.details[id] = await firstValueFrom(this.irisMemoriesHttpService.getUserMemory(id));
                }
                const connections = this.details[id]?.connections ?? [];
                for (const c of connections) {
                    const connectionType = c.connectionType?.toUpperCase();
                    const isConflict = connectionType === 'CONFLICT' || connectionType === 'CONTRADICTS';
                    if (isConflict && c.id && (c.memories?.length ?? 0) > 0) {
                        // Use the connection ID to ensure we only process each conflict once
                        // Each connection has a unique ID on the backend
                        if (!seenConflicts.has(c.id)) {
                            seenConflicts.add(c.id);
                            // c.memories already contains all memories involved in this conflict
                            // Remove duplicates using Set and sort for consistent ordering
                            const conflictMemories = Array.from(new Set(c.memories!)).sort();
                            groups.push(conflictMemories);
                        }
                    }
                }
            } catch (error) {
                // Do not break overall detection on individual errors
                this.alertService.error('artemisApp.iris.memories.error.detailsLoadFailed');
            }
        }
        this.conflictGroups = groups;
        this.hasConflicts = this.conflictGroups.length > 0;

        // Preload details for all ids in conflict groups to show in modal
        for (const group of this.conflictGroups) {
            for (const gid of group) {
                try {
                    if (!this.details[gid]) {
                        this.details[gid] = await firstValueFrom(this.irisMemoriesHttpService.getUserMemory(gid));
                    }
                } catch (error) {
                    this.alertService.error('artemisApp.iris.memories.error.detailsLoadFailed');
                }
            }
        }
    }

    openResolveConflictsModal() {
        const modalRef: NgbModalRef = this.modalService.open(ResolveMemoriesConflictsModalComponent, { size: 'lg', backdrop: 'static' });
        // Set properties directly
        modalRef.componentInstance.conflictGroups = this.conflictGroups;
        modalRef.componentInstance.details = this.details;
        // After modal closes, refresh the memories and re-detect conflicts
        modalRef.result.then(() => this.loadMemories()).catch(() => this.loadMemories());
    }
}
