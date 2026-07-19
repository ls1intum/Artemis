import { ChangeDetectionStrategy, Component, OnInit, TrackByFunction, computed, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { ButtonModule } from 'primeng/button';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiTableComponent } from 'app/shared-ui/tum-ui/table/tum-ui-table.component';
import { CellTemplateRef, ColumnDef, TumUiTableQueryEvent } from 'app/shared-ui/tum-ui/table/tum-ui-table.types';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { VcsAccessTokenOverviewService } from 'app/account/user/settings/vcs-access-token-overview/vcs-access-token-overview.service';
import { VcsAccessTokenOverview, VcsAccessTokenType } from 'app/account/user/settings/vcs-access-token-overview/vcs-access-token-overview.model';

/**
 * User-settings page listing the VCS access tokens the current user owns (participation tokens plus repository-scoped staff tokens) and letting them revoke individual tokens.
 * The token secret is never shown here — only display metadata. Revoking a token simply lets the next clone-dialog visit re-mint a fresh one. The list is small, so it is loaded in
 * full and the tum-ui table paginates, sorts, and filters it client-side.
 */
@Component({
    selector: 'jhi-vcs-access-token-overview',
    templateUrl: './vcs-access-token-overview.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumUiTableComponent, TranslateDirective, ArtemisTranslatePipe, FaIconComponent, ButtonModule, DeleteButtonDirective],
})
export class VcsAccessTokenOverviewComponent implements OnInit {
    private readonly service = inject(VcsAccessTokenOverviewService);
    private readonly alertService = inject(AlertService);

    private readonly allTokens = signal<VcsAccessTokenOverview[]>([]);
    protected readonly rows = signal<VcsAccessTokenOverview[]>([]);
    protected readonly totalCount = signal(0);
    protected readonly isLoading = signal(false);

    protected readonly repositoryColumnTemplate = viewChild<CellTemplateRef<VcsAccessTokenOverview>>('repositoryColumn');

    protected readonly columns = computed<ColumnDef<VcsAccessTokenOverview>[]>(() => [
        { field: 'courseTitle', headerKey: 'artemisApp.userSettings.vcsAccessTokensOverview.table.course', sort: true },
        { field: 'exerciseTitle', headerKey: 'artemisApp.userSettings.vcsAccessTokensOverview.table.exercise', sort: true },
        { headerKey: 'artemisApp.userSettings.vcsAccessTokensOverview.table.repository', templateRef: this.repositoryColumnTemplate() },
    ]);

    // Row identity (type + id, since ids are only unique within a token table) so the table reuses row DOM across reloads.
    protected readonly trackByToken: TrackByFunction<VcsAccessTokenOverview> = (_, token) => `${token.tokenType}-${token.id}`;

    private readonly dialogErrorSource = new Subject<string>();
    protected readonly dialogError$ = this.dialogErrorSource.asObservable();

    protected readonly faTrash = faTrash;
    protected readonly VcsAccessTokenType = VcsAccessTokenType;
    protected readonly RepositoryType = RepositoryType;

    private lastQuery: TumUiTableQueryEvent = { page: 0, pageSize: 20 };

    ngOnInit(): void {
        this.loadTokens();
    }

    /**
     * Loads the current user's VCS access tokens from the server and applies the current query (page, sort, search) to them.
     */
    private loadTokens(): void {
        this.isLoading.set(true);
        this.service.getTokens().subscribe({
            next: (tokens) => {
                this.allTokens.set(tokens);
                this.applyQuery(this.lastQuery);
                this.isLoading.set(false);
            },
            error: (error: HttpErrorResponse) => {
                this.allTokens.set([]);
                this.applyQuery(this.lastQuery);
                this.isLoading.set(false);
                onError(this.alertService, error);
            },
        });
    }

    /**
     * The tum-ui table emits this whenever its page, sort, or search term changes (and once after first render). Because the full token list is held in memory, the query is
     * applied client-side.
     */
    onDataRequest(event: TumUiTableQueryEvent): void {
        this.lastQuery = event;
        this.applyQuery(event);
    }

    /**
     * Filters (by search term), sorts, and paginates the full in-memory token list according to the given table query, updating the displayed rows and the total count.
     *
     * @param event the current table query (page, page size, optional sort and search term)
     */
    private applyQuery(event: TumUiTableQueryEvent): void {
        let filtered = this.allTokens();
        const term = event.searchTerm?.toLowerCase();
        if (term) {
            filtered = filtered.filter(
                (token) =>
                    (token.courseTitle ?? '').toLowerCase().includes(term) ||
                    (token.exerciseTitle ?? '').toLowerCase().includes(term) ||
                    (token.studentLogin ?? '').toLowerCase().includes(term),
            );
        }
        const sorted = [...filtered];
        const sortField = event.sort?.field;
        if (sortField === 'courseTitle' || sortField === 'exerciseTitle') {
            const direction = event.sort!.direction === 'asc' ? 1 : -1;
            sorted.sort((a, b) => (a[sortField] ?? '').localeCompare(b[sortField] ?? '') * direction);
        }
        this.totalCount.set(sorted.length);
        const from = event.page * event.pageSize;
        this.rows.set(sorted.slice(from, from + event.pageSize));
    }

    /**
     * Revokes the given token, removes it from the list on success, and reports the outcome. The next clone-dialog visit re-mints a fresh token for that repository.
     *
     * @param token the token to revoke
     */
    revokeToken(token: VcsAccessTokenOverview): void {
        this.service.revokeToken(token.tokenType, token.id).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.allTokens.set(this.allTokens().filter((candidate) => !(candidate.id === token.id && candidate.tokenType === token.tokenType)));
                this.applyQuery(this.lastQuery);
                this.alertService.success('artemisApp.userSettings.vcsAccessTokensOverview.revoke.success');
            },
            error: (error: HttpErrorResponse) => {
                this.dialogErrorSource.next(error.message);
            },
        });
    }
}
