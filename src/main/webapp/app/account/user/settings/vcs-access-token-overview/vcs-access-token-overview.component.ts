import { ChangeDetectionStrategy, Component, OnInit, TrackByFunction, computed, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TumUiTableComponent } from 'app/shared-ui/tum-ui/table/tum-ui-table.component';
import { CellTemplateRef, ColumnDef, TumUiTableQueryEvent } from 'app/shared-ui/tum-ui/table/tum-ui-table.types';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { ActionType, DeleteDialogData } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { RouterLink } from '@angular/router';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { VcsAccessTokenOverviewService } from 'app/account/user/settings/vcs-access-token-overview/vcs-access-token-overview.service';
import { VcsAccessTokenOverview, VcsAccessTokenType } from 'app/account/user/settings/vcs-access-token-overview/vcs-access-token-overview.model';

const TYPE_LABEL_KEY_PREFIX = 'artemisApp.userSettings.vcsAccessTokensOverview.type.';

/**
 * User-settings page listing the VCS access tokens the current user owns (participation tokens plus repository-scoped staff tokens) and letting them revoke individual tokens.
 * The token secret is never shown here — only display metadata (course, exercise, a short repository type and the repository URI). Revoking a token simply lets the next
 * clone-dialog visit re-mint a fresh one. The list is small, so it is loaded in full and the tum-ui table paginates, sorts, and filters it client-side.
 */
@Component({
    selector: 'jhi-vcs-access-token-overview',
    templateUrl: './vcs-access-token-overview.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TumUiTableComponent, TumUiButtonComponent, TranslateDirective, ArtemisTranslatePipe, RouterLink],
})
export class VcsAccessTokenOverviewComponent implements OnInit {
    private readonly service = inject(VcsAccessTokenOverviewService);
    private readonly alertService = inject(AlertService);
    private readonly deleteDialogService = inject(DeleteDialogService);
    private readonly translateService = inject(TranslateService);

    private readonly allTokens = signal<VcsAccessTokenOverview[]>([]);
    protected readonly rows = signal<VcsAccessTokenOverview[]>([]);
    protected readonly totalCount = signal(0);
    protected readonly isLoading = signal(false);

    protected readonly courseColumnTemplate = viewChild<CellTemplateRef<VcsAccessTokenOverview>>('courseColumn');
    protected readonly exerciseColumnTemplate = viewChild<CellTemplateRef<VcsAccessTokenOverview>>('exerciseColumn');
    protected readonly typeColumnTemplate = viewChild<CellTemplateRef<VcsAccessTokenOverview>>('typeColumn');
    protected readonly repositoryColumnTemplate = viewChild<CellTemplateRef<VcsAccessTokenOverview>>('repositoryColumn');

    // On small screens the least essential columns drop out (course below md, the long repository URI below xl) so the table stays usable without horizontal scrolling; the exercise
    // and type — enough to identify and revoke a token — are always shown. The repository column gets a width floor so the URI claims space (rendering in ~2 lines) instead of being
    // starved by the other columns.
    protected readonly columns = computed<ColumnDef<VcsAccessTokenOverview>[]>(() => [
        { field: 'courseTitle', headerKey: 'artemisApp.userSettings.vcsAccessTokensOverview.table.course', sort: true, hideBelow: 'md', templateRef: this.courseColumnTemplate() },
        { field: 'exerciseTitle', headerKey: 'artemisApp.userSettings.vcsAccessTokensOverview.table.exercise', sort: true, templateRef: this.exerciseColumnTemplate() },
        { headerKey: 'artemisApp.userSettings.vcsAccessTokensOverview.table.type', templateRef: this.typeColumnTemplate() },
        { headerKey: 'artemisApp.userSettings.vcsAccessTokensOverview.table.repository', hideBelow: 'xl', width: '24rem', templateRef: this.repositoryColumnTemplate() },
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
            filtered = filtered.filter((token) => this.searchHaystack(token).includes(term));
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
     * The lowercased, searchable text of a token: its course, exercise, student login, repository URI and (translated) short repository-type label. Used for the client-side
     * global search so the user can filter by any of the columns, including the repository type and URI.
     *
     * @param token the token to build the search text for
     */
    private searchHaystack(token: VcsAccessTokenOverview): string {
        return [token.courseTitle, token.exerciseTitle, token.studentLogin, token.repositoryUri, this.translateService.instant(this.tokenTypeLabelKey(token))]
            .filter(Boolean)
            .join(' ')
            .toLowerCase();
    }

    /**
     * The i18n key of the short label describing a token's repository type (e.g. "Template", "Assignment") or, for a participation token, "Participation".
     *
     * @param token the token to label
     */
    protected tokenTypeLabelKey(token: VcsAccessTokenOverview): string {
        if (token.tokenType === VcsAccessTokenType.PARTICIPATION) {
            return `${TYPE_LABEL_KEY_PREFIX}participation`;
        }
        switch (token.repositoryType) {
            case RepositoryType.TEMPLATE:
                return `${TYPE_LABEL_KEY_PREFIX}template`;
            case RepositoryType.SOLUTION:
                return `${TYPE_LABEL_KEY_PREFIX}solution`;
            case RepositoryType.TESTS:
                return `${TYPE_LABEL_KEY_PREFIX}tests`;
            case RepositoryType.AUXILIARY:
                return `${TYPE_LABEL_KEY_PREFIX}auxiliary`;
            case RepositoryType.USER:
                return `${TYPE_LABEL_KEY_PREFIX}assignment`;
            default:
                return `${TYPE_LABEL_KEY_PREFIX}repository`;
        }
    }

    /**
     * The router link to the token's course, or {@code undefined} if the course is unknown. Participation tokens belong to the current user (who may be a student without
     * course-management access), so they link to the student-facing course page; repository-scoped staff tokens link to course management.
     *
     * @param token the token whose course to link to
     */
    protected courseLink(token: VcsAccessTokenOverview): (string | number)[] | undefined {
        if (!token.courseId) {
            return undefined;
        }
        return token.tokenType === VcsAccessTokenType.PARTICIPATION ? ['/courses', token.courseId] : ['/course-management', token.courseId];
    }

    /**
     * The router link to the token's exercise, or {@code undefined} if it cannot be built. The target depends on the token kind so it never points a user at a page they cannot open:
     * participation tokens (owned by the current user, possibly a student) use the student-facing routes, while repository-scoped staff tokens use the course-management routes.
     * Exam exercises are routed through their exam (and, for staff, exercise group).
     *
     * @param token the token whose exercise to link to
     */
    protected exerciseLink(token: VcsAccessTokenOverview): (string | number)[] | undefined {
        if (!token.courseId || !token.exerciseId) {
            return undefined;
        }
        if (token.tokenType === VcsAccessTokenType.PARTICIPATION) {
            // Student-facing routes: exam exercises are reached through the exam, regular exercises through the exercise page.
            return token.examId ? ['/courses', token.courseId, 'exams', token.examId] : ['/courses', token.courseId, 'exercises', token.exerciseId];
        }
        // Staff repository token: course-management routes (staff have access there).
        return token.examId && token.exerciseGroupId
            ? ['/course-management', token.courseId, 'exams', token.examId, 'exercise-groups', token.exerciseGroupId, 'programming-exercises', token.exerciseId]
            : ['/course-management', token.courseId, 'programming-exercises', token.exerciseId];
    }

    /**
     * Opens the confirmation dialog for revoking the given token. Confirming triggers {@link revokeToken}; the shared delete dialog surfaces any server error via {@link dialogError$}.
     *
     * @param token the token the user wants to revoke
     */
    protected openRevokeDialog(token: VcsAccessTokenOverview): void {
        const deleteDialogData: DeleteDialogData = {
            requireConfirmationOnlyForAdditionalChecks: false,
            deleteQuestion: 'artemisApp.userSettings.vcsAccessTokensOverview.revoke.question',
            translateValues: {},
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            delete: () => this.revokeToken(token),
            dialogError: this.dialogError$,
        };
        this.deleteDialogService.openDeleteDialog(deleteDialogData, true);
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
