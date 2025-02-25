import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { NgbModule, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute } from '@angular/router';
import { VcsAccessLogDTO } from 'app/entities/vcs-access-log-entry.model';
import { AlertService } from 'app/core/util/alert.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { SortIconComponent } from 'app/shared/sort/sort-icon.component';
import { CommonModule } from '@angular/common';
import { VcsRepositoryAccessLogService } from 'app/localvc/vcs-repository-access-log-view/vcs-repository-access-log.service';

@Component({
    selector: 'jhi-vcs-repository-access-log-view',
    templateUrl: './vcs-repository-access-log-view.component.html',
    imports: [TranslateDirective, NgbModule, NgbPagination, SortIconComponent, CommonModule],
})
export class VcsRepositoryAccessLogViewComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly alertService = inject(AlertService);
    private readonly vcsAccessLogService = inject(VcsRepositoryAccessLogService);

    private readonly params = toSignal(this.route.params, { requireSync: true });

    readonly page = signal<number>(1);
    readonly pageSize = signal<number>(25);
    readonly searchTerm = signal<string>('');
    readonly sortingOrder = signal<SortingOrder>(SortingOrder.ASCENDING);
    readonly sortedColumn = signal<string>('id');
    readonly isLoading = signal<boolean>(false);

    readonly content = signal<SearchResult<VcsAccessLogDTO>>({ resultsOnPage: [], numberOfPages: 0 });
    readonly totalItems = signal<number>(0);
    readonly collectionsSize = computed(() => this.content().numberOfPages * this.pageSize());

    readonly TRANSLATION_BASE = 'artemisApp.repository.vcsAccessLog';

    private readonly repositoryId = computed(() => {
        const repositoryId = this.params().repositoryId;
        if (repositoryId) {
            return Number(repositoryId);
        }
        return undefined;
    });
    private readonly exerciseId = computed(() => {
        const exerciseId = this.params().exerciseId;
        if (exerciseId) {
            return Number(exerciseId);
        }
        return 0;
    });
    private readonly repositoryType = computed(() => this.params().repositoryType);

    constructor() {
        effect(() => {
            untracked(async () => {
                await this.loadData();
            });
        });
    }

    async loadData() {
        const state = {
            page: this.page(),
            pageSize: this.pageSize(),
            searchTerm: this.searchTerm() || '',
            sortingOrder: this.sortingOrder(),
            sortedColumn: this.sortedColumn(),
        };
        this.isLoading.set(true);
        try {
            const response = await this.vcsAccessLogService.search(state, {
                repositoryId: this.repositoryId() ?? 0,
                exerciseId: this.exerciseId() ?? 0,
                repositoryType: this.repositoryType(),
            });
            this.content.set(response);
        } catch (error) {
            this.alertService.error(this.TRANSLATION_BASE + '.error');
        } finally {
            this.isLoading.set(false);
        }
    }

    async setPage(newPage: number) {
        this.page.set(newPage);
        await this.loadData();
    }

    async setSortedColumn(column: string) {
        if (this.sortedColumn() === column) {
            this.sortingOrder.set(this.sortingOrder() === SortingOrder.ASCENDING ? SortingOrder.DESCENDING : SortingOrder.ASCENDING);
        } else {
            this.sortedColumn.set(column);
            this.sortingOrder.set(SortingOrder.ASCENDING);
        }
        await this.loadData();
    }

    getSortDirection(column: string): SortingOrder.ASCENDING | SortingOrder.DESCENDING | 'none' {
        if (this.sortedColumn() === column) {
            return this.sortingOrder() === SortingOrder.ASCENDING ? SortingOrder.ASCENDING : SortingOrder.DESCENDING;
        }
        return 'none';
    }
}
