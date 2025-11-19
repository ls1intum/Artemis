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

    currentGroup = computed<string[]>(() => {
        const idx = this.currentIndex();
        const g = this.groups();
        return g.length > 0 && idx >= 0 && idx < g.length ? g[idx] : [];
    });

    ngOnInit(): void {
        // Copy input groups to local state to manage progression within the modal
        const inputGroups = this.conflictGroups ?? [];
        this.groups.set(inputGroups.map((arr) => [...arr]));
        this.currentIndex.set(0);
    }

    close(): void {
        this.activeModal.dismiss();
    }

    async keep(_groupIndex: number, keepId: string): Promise<void> {
        const group = this.currentGroup();
        if (!group?.length) return;
        const toDelete = group.filter((id) => id !== keepId);
        this.busy.set(true);
        try {
            for (const id of toDelete) {
                try {
                    await firstValueFrom(this.irisMemoriesHttpService.deleteUserMemory(id));
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
                this.activeModal.close('resolved');
            } else if (this.currentIndex() >= g.length) {
                this.currentIndex.set(g.length - 1);
            }
        } finally {
            this.busy.set(false);
        }
    }

    prev(): void {
        const idx = this.currentIndex();
        if (idx > 0) this.currentIndex.set(idx - 1);
    }

    next(): void {
        const idx = this.currentIndex();
        if (idx < this.groups().length - 1) this.currentIndex.set(idx + 1);
    }
}
