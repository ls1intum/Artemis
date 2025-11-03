import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { IrisMemoriesHttpService } from 'app/iris/overview/services/iris-memories-http.service';
import { MemirisMemory, MemirisMemoryWithRelationsDTO } from 'app/iris/shared/entities/memiris.model';
import { firstValueFrom } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronRight } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-memiris-memories-list',
    imports: [CommonModule, TranslateDirective, FaIconComponent],
    templateUrl: './memiris-memories-list.component.html',
})
export class MemirisMemoriesListComponent implements OnInit {
    private readonly irisMemoriesHttpService = inject(IrisMemoriesHttpService);
    private readonly alertService = inject(AlertService);

    loading = false;
    deleting: Record<string, boolean> = {};
    loadingDetails: Record<string, boolean> = {};
    memories: MemirisMemory[] = [];
    open: Record<string, boolean> = {};
    details: Record<string, MemirisMemoryWithRelationsDTO | undefined> = {};

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
}
