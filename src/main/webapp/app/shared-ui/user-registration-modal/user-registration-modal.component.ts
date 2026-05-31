import { Component, DestroyRef, TemplateRef, ViewEncapsulation, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { SharedModule } from 'primeng/api';
import { Dialog } from 'primeng/dialog';
import { ButtonDirective } from 'primeng/button';
import { Message } from 'primeng/message';
import { TableLazyLoadEvent } from 'primeng/table';
import { buildDbQueryFromLazyEvent } from 'app/shared-ui/table-view/request-builder';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { addPublicFilePrefix } from 'app/app.constants';
import { CellRendererParams, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { HighlightMatchPipe } from 'app/foundation/pipes/highlight-match.pipe';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { UserForRegistration, UserSearchResult } from 'app/shared-ui/user-registration-modal/user-for-registration.model';

@Component({
    selector: 'jhi-user-registration-modal',
    imports: [
        SharedModule,
        Dialog,
        ButtonDirective,
        Message,
        TranslateDirective,
        ArtemisTranslatePipe,
        TableViewComponent,
        SearchFilterComponent,
        HighlightMatchPipe,
        ProfilePictureComponent,
    ],
    templateUrl: './user-registration-modal.component.html',
    styleUrl: './user-registration-modal.component.scss',
    encapsulation: ViewEncapsulation.None,
})
export class UserRegistrationModalComponent {
    private static readonly SEARCH_DEBOUNCE_MS = 300;

    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    private readonly alertService = inject(AlertService);

    private debounceTimer: ReturnType<typeof setTimeout> | undefined;

    constructor() {
        inject(DestroyRef).onDestroy(() => clearTimeout(this.debounceTimer));
    }

    readonly titleKey = input.required<string>();
    readonly searchFn = input.required<(searchTerm: string, page: number, size: number) => Observable<UserSearchResult>>();
    readonly registerFn = input.required<(users: UserForRegistration[]) => Observable<void>>();

    readonly registered = output<void>();

    readonly tableViewRef = viewChild(TableViewComponent);
    readonly searchFilterRef = viewChild(SearchFilterComponent);

    readonly profilePicTemplate = viewChild<TemplateRef<{ $implicit: CellRendererParams<UserForRegistration> }>>('profilePicTemplate');
    readonly nameLoginTemplate = viewChild<TemplateRef<{ $implicit: CellRendererParams<UserForRegistration> }>>('nameLoginTemplate');
    readonly matriculationTemplate = viewChild<TemplateRef<{ $implicit: CellRendererParams<UserForRegistration> }>>('matriculationTemplate');
    readonly emailTemplate = viewChild<TemplateRef<{ $implicit: CellRendererParams<UserForRegistration> }>>('emailTemplate');

    readonly isOpen = signal(false);
    readonly isLoading = signal(false);
    readonly searchTerm = signal('');
    readonly hasSearched = signal(false);
    readonly searchResults = signal<UserForRegistration[]>([]);
    readonly totalSearchResults = signal(0);
    readonly selectedUsers = signal<UserForRegistration[]>([]);
    readonly isViewingSelected = signal(false);
    private requestId = 0;

    readonly selectedCount = computed(() => this.selectedUsers().length);
    readonly searchPlaceholderKey = computed(() => (this.isViewingSelected() ? 'userRegistrationModal.viewingSelectedPlaceholder' : 'userRegistrationModal.searchPlaceholder'));
    readonly isRegisterDisabled = computed(() => this.selectedCount() === 0 || this.isLoading());
    readonly showTable = computed(() => this.isViewingSelected() || (this.hasSearched() && (this.isLoading() || this.searchResults().length > 0)));
    readonly noResults = computed(() => this.hasSearched() && !this.isLoading() && this.searchResults().length === 0 && !this.isViewingSelected());

    readonly tableValues = computed<UserForRegistration[]>(() => (this.isViewingSelected() ? this.selectedUsers() : this.searchResults()));
    readonly tableTotalRows = computed(() => (this.isViewingSelected() ? this.selectedCount() : this.totalSearchResults()));

    readonly selectedInCurrentResults = computed<UserForRegistration[]>(() => {
        if (this.isViewingSelected()) {
            return this.selectedUsers();
        }
        const selectedIds = new Set(this.selectedUsers().map((u) => u.id));
        return this.searchResults().filter((u) => selectedIds.has(u.id));
    });

    readonly tableOptions = computed<TableViewOptions>(() => ({
        lazy: !this.isViewingSelected(),
        paginated: !this.isViewingSelected(),
        showSearch: false,
        selectionMode: 'multiple',
        scrollable: true,
        scrollHeight: 'flex',
        pageSize: 10,
        hidePageSizeOptions: true,
        tableStyle: { 'min-width': '40rem' },
    }));

    readonly columns = computed<ColumnDef<UserForRegistration>[]>(() => [
        { field: 'profilePictureUrl', width: '3.5rem', templateRef: this.profilePicTemplate() },
        { field: 'name', headerKey: 'userRegistrationModal.columns.nameLogin', width: '12rem', templateRef: this.nameLoginTemplate() },
        { field: 'registrationNumber', headerKey: 'userRegistrationModal.columns.matriculationNumber', width: '10rem', templateRef: this.matriculationTemplate() },
        { field: 'email', headerKey: 'userRegistrationModal.columns.email', width: '12rem', templateRef: this.emailTemplate() },
    ]);

    readonly isRowDisabledFn = (row: UserForRegistration): boolean => row.isRegistered;

    open(): void {
        this.isOpen.set(true);
    }

    close(): void {
        this.resetState();
        this.isOpen.set(false);
    }

    onSearch(searchTerm: string): void {
        const trimmed = searchTerm.trim();
        this.searchTerm.set(trimmed);
        clearTimeout(this.debounceTimer);

        if (!trimmed) {
            this.hasSearched.set(false);
            this.searchResults.set([]);
            this.totalSearchResults.set(0);
            return;
        }

        this.debounceTimer = setTimeout(() => {
            this.isLoading.set(true);
            this.hasSearched.set(true);
            this.tableViewRef()?.reload();
        }, UserRegistrationModalComponent.SEARCH_DEBOUNCE_MS);
    }

    onTableLazyLoad(event: TableLazyLoadEvent): void {
        const searchTerm = this.searchTerm();
        if (!searchTerm) return;

        const { page, pageSize } = buildDbQueryFromLazyEvent(event, { pageSize: 10 });
        const currentRequestId = ++this.requestId;

        this.isLoading.set(true);
        this.searchFn()(searchTerm, page, pageSize).subscribe({
            next: (result) => {
                if (currentRequestId !== this.requestId) return;
                this.searchResults.set(result.content);
                this.totalSearchResults.set(result.totalElements);
                this.isLoading.set(false);
            },
            error: () => {
                if (currentRequestId !== this.requestId) return;
                this.alertService.error('userRegistrationModal.searchError');
                this.searchResults.set([]);
                this.totalSearchResults.set(0);
                this.isLoading.set(false);
            },
        });
    }

    handleSelectionChange(selection: UserForRegistration | UserForRegistration[] | undefined): void {
        if (!Array.isArray(selection)) return;

        const updatedSelection = selection.filter((u) => !u.isRegistered);
        if (this.isViewingSelected()) {
            // In view mode the table data IS selectedUsers(), so the emitted selection
            // is already the full updated list — just apply it directly.
            this.selectedUsers.set(updatedSelection);
            if (updatedSelection.length === 0) {
                this.isViewingSelected.set(false);
            }
        } else {
            // In search mode: keep users selected in previous searches that are not in
            // the current result page, then merge with this page's selection.
            const currentResultIds = new Set(this.searchResults().map((u) => u.id));
            const keptFromPreviousSearches = this.selectedUsers().filter((u) => !currentResultIds.has(u.id));
            this.selectedUsers.set([...keptFromPreviousSearches, ...updatedSelection]);
        }
    }

    toggleViewSelected(): void {
        if (!this.isViewingSelected()) {
            this.searchFilterRef()?.resetSearchValue(); // clears input + emits onSearch('') → resets searchTerm/hasSearched/searchResults
        }
        this.isViewingSelected.set(!this.isViewingSelected());
    }

    clearSelection(): void {
        this.selectedUsers.set([]);
        this.tableViewRef()?.clearSelection();
        if (this.isViewingSelected()) {
            this.isViewingSelected.set(false);
        }
    }

    register(): void {
        const users = this.selectedUsers();
        if (!users.length) return;

        this.isLoading.set(true);
        this.registerFn()(users).subscribe({
            next: () => {
                this.registered.emit();
                this.close();
            },
            error: () => {
                this.alertService.error('userRegistrationModal.registerError');
                this.isLoading.set(false);
            },
        });
    }

    private resetState(): void {
        this.requestId++;
        this.searchTerm.set('');
        this.hasSearched.set(false);
        this.searchResults.set([]);
        this.totalSearchResults.set(0);
        this.selectedUsers.set([]);
        this.isViewingSelected.set(false);
        this.searchFilterRef()?.resetSearchValue();
    }
}
