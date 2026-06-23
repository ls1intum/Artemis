import { Component, ViewEncapsulation, computed, effect, input, model, output, signal, viewChild } from '@angular/core';
import { Observable, Subject, of } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { User } from 'app/account/user/user.model';
import { Course, CourseGroup } from 'app/course/shared/entities/course.model';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { catchError, map } from 'rxjs/operators';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { faDownload, faUserPlus, faUserSlash, faUsers } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared-ui/export/export-constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { addPublicFilePrefix } from 'app/app.constants';
import { UsersImportButtonComponent } from 'app/shared-ui/user-import/button/users-import-button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ExportUserInformationRow, exportUserInformationAsCsv } from 'app/shared-ui/user-import/util/write-users-to-csv';
import { AutoComplete, AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

const cssClasses = {
    alreadyMember: 'already-member',
    newlyAddedMember: 'newly-added-member',
};

/**
 * Duration (ms) the `newly-added-member` flash class stays applied so the CSS flash animation can play
 * through before being cleared (animation-delay 150ms + animation-duration 1.5s, see course-group.component.scss).
 */
const FLASH_ANIMATION_DURATION_MS = 1650;

@Component({
    selector: 'jhi-course-group',
    templateUrl: './course-group.component.html',
    styleUrls: ['./course-group.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        UsersImportButtonComponent,
        FaIconComponent,
        TranslateDirective,
        TableViewComponent,
        RouterLink,
        ProfilePictureComponent,
        DeleteButtonDirective,
        AutoComplete,
        ArtemisTranslatePipe,
    ],
})
export class CourseGroupComponent {
    constructor() {
        // Keep the parent's filteredUsersSize in sync with the *filtered* member count so the header
        // "X out of Y" counter tracks the autocomplete search. Reads filteredGroupUsers() so it re-emits
        // whenever either allGroupUsers or the search query changes; with no active query the filtered
        // list equals the full list, so the parent hides the counter instead of showing a stale "0".
        effect(() => {
            this.handleUsersSizeChange()(this.filteredGroupUsers().length);
        });
    }

    readonly allGroupUsers = model<User[]>([]);
    readonly isLoadingAllGroupUsers = input(false);
    readonly isAdmin = input(false);
    readonly course = input.required<Course>();
    readonly tutorialGroup = input<TutorialGroup | undefined>(undefined);
    readonly courseGroup = input.required<CourseGroup>();
    readonly exportFileName = input.required<string>();

    readonly userSearch = input<(loginOrName: string) => Observable<HttpResponse<User[]>>>(() => of(new HttpResponse<User[]>({ body: [] })));
    readonly addUserToGroup = input<(login: string) => Observable<HttpResponse<void>>>(() => of(new HttpResponse<void>()));
    readonly removeUserFromGroup = input<(login: string) => Observable<HttpResponse<void>>>(() => of(new HttpResponse<void>()));
    readonly handleUsersSizeChange = input<(filteredUsersSize: number) => void>(() => {});

    readonly importFinish = output<void>();

    // Cell templates for custom column rendering
    readonly idCellTemplate = viewChild<CellTemplateRef<User>>('idCellTemplate');
    readonly profilePictureCellTemplate = viewChild<CellTemplateRef<User>>('profilePictureCellTemplate');

    readonly tableOptions: TableViewOptions = {
        lazy: false,
        showSearch: false,
        striped: true,
    };

    readonly columns = computed<ColumnDef<User>[]>(() => [
        { field: 'id', headerKey: 'global.field.id', sort: true, width: '5rem', templateRef: this.idCellTemplate() },
        { field: 'imageUrl', headerKey: 'artemisApp.course.courseGroup.profilePicture', width: '7rem', templateRef: this.profilePictureCellTemplate() },
        { field: 'login', headerKey: 'artemisApp.course.courseGroup.login', sort: true, width: '12rem' },
        { field: 'visibleRegistrationNumber', headerKey: 'artemisApp.course.courseGroup.registrationNumber', sort: true, width: '12rem' },
        { field: 'name', headerKey: 'artemisApp.course.courseGroup.name', sort: true, width: '15rem' },
        { field: 'email', headerKey: 'artemisApp.course.courseGroup.email', sort: true },
    ]);

    protected readonly ActionType = ActionType;

    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    readonly isSearching = signal(false);
    readonly searchFailed = signal(false);
    readonly isTransitioning = signal(false);
    private readonly rowClass = signal('');

    /** Current text in the combined search/add autocomplete; drives client-side table filtering. */
    private readonly filterQuery = signal('');

    /** True when the user has typed 1–2 characters — not enough to trigger a server search. */
    protected readonly searchQueryTooShort = computed(() => {
        const q = this.filterQuery();
        return q.length > 0 && q.length < 3;
    });

    /** Suggestions shown in the autocomplete dropdown (fetched from the server). */
    readonly userSuggestions = signal<User[]>([]);

    /**
     * The subset of allGroupUsers that match the current filterQuery.
     * Passed as [vals] to jhi-table-view so filtering and adding users share one input.
     */
    readonly filteredGroupUsers = computed<User[]>(() => {
        const query = this.filterQuery().toLowerCase().trim();
        if (!query) {
            return this.allGroupUsers();
        }
        return this.allGroupUsers().filter(
            (u) =>
                u.login?.toLowerCase().includes(query) ||
                u.name?.toLowerCase().includes(query) ||
                u.email?.toLowerCase().includes(query) ||
                u.visibleRegistrationNumber?.toLowerCase().includes(query),
        );
    });

    protected readonly faDownload = faDownload;
    protected readonly faUserSlash = faUserSlash;
    protected readonly faUsers = faUsers;
    protected readonly faUserPlus = faUserPlus;

    private latestSearchRequestId = 0;

    /**
     * Triggered by p-autocomplete on each keystroke (after minLength chars are typed).
     * Updates the table filter query and fetches matching users from the server for the dropdown.
     */
    onUserSearchComplete(event: AutoCompleteCompleteEvent): void {
        const query = event.query.trim();
        this.filterQuery.set(query);
        this.searchFailed.set(false);

        if (query.length < 3) {
            this.isSearching.set(false);
            this.userSuggestions.set([]);
            return;
        }

        this.isSearching.set(true);
        const requestId = ++this.latestSearchRequestId;
        this.userSearch()(query)
            .pipe(
                map((response) => response.body ?? []),
                catchError(() => {
                    this.searchFailed.set(true);
                    return of([]);
                }),
            )
            .subscribe((users) => {
                if (requestId !== this.latestSearchRequestId) return;
                this.isSearching.set(false);
                this.userSuggestions.set(users);
            });
    }

    /**
     * Triggered on every key-up in the autocomplete input.
     * Updates filterQuery for short queries (< minLength) where completeMethod does not fire,
     * so the table is filtered in real time even before the server-search threshold is reached.
     */
    onSearchKeyUp(event: KeyboardEvent): void {
        this.filterQuery.set((event.target as HTMLInputElement).value);
    }

    /**
     * Triggered when the autocomplete input is cleared (by the X button or programmatically).
     * Resets the table filter and clears the dropdown suggestions.
     */
    onSearchClear(): void {
        this.filterQuery.set('');
        this.userSuggestions.set([]);
    }

    /**
     * Triggered when the user picks a suggestion from the p-autocomplete dropdown.
     * Keeps the current search text (PrimeNG shows the selected user's login in the input)
     * by syncing filterQuery to their login so the table stays filtered and the newly added
     * member is immediately visible. Then adds the user to the group if not already a member.
     */
    onUserSelect(user: User): void {
        // Sync filterQuery to the login that PrimeNG will show in the input after selection.
        // This keeps the search bar non-empty and the table filtered, so the new member is visible.
        this.filterQuery.set(user.login ?? '');
        // Close the dropdown by clearing suggestions; PrimeNG already closes it on select,
        // but resetting avoids stale suggestions appearing on the next open.
        this.userSuggestions.set([]);

        if (!this.isAlreadyMember(user) && user.login) {
            this.isTransitioning.set(true);
            this.addUserToGroup()(user.login).subscribe({
                next: () => {
                    this.isTransitioning.set(false);
                    this.allGroupUsers.update((users) => [...users, user]);
                    this.flashRowClass(cssClasses.newlyAddedMember);
                },
                error: () => {
                    this.isTransitioning.set(false);
                },
            });
        }
    }

    /**
     * Returns true when the given user is already a member of this course group.
     * Used in the p-autocomplete item template to show the appropriate icon.
     */
    isAlreadyMember(user: User): boolean {
        return this.allGroupUsers()
            .map((u) => u.id)
            .includes(user.id);
    }

    /**
     * Remove user from course group.
     *
     * @param user User that should be removed from the currently viewed course group
     */
    removeFromGroup(user: User): void {
        if (user.login) {
            this.removeUserFromGroup()(user.login).subscribe({
                next: () => {
                    this.allGroupUsers.update((users) => users.filter((u) => u.login !== user.login));
                    this.dialogErrorSource.next('');
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
        }
    }

    /**
     * Returns the CSS class applied to all table rows.
     * Used to flash all rows with `newly-added-member` briefly after a user is added.
     */
    dataTableRowClass = (): string => {
        return this.rowClass();
    };

    /**
     * Temporarily applies a CSS class to all rows (e.g. green flash on add), then clears it.
     */
    flashRowClass = (className: string): void => {
        this.rowClass.set(className);
        // Keep the class applied long enough for the CSS flash animation to play before clearing it.
        setTimeout(() => this.rowClass.set(''), FLASH_ANIMATION_DURATION_MS);
    };

    /**
     * Exports the current group member list as a CSV file.
     */
    exportUserInformation = (): void => {
        const users = this.allGroupUsers();
        if (users.length > 0) {
            const rows: ExportUserInformationRow[] = users.map((user: User): ExportUserInformationRow => {
                return {
                    [NAME_KEY]: user.name?.trim() ?? '',
                    [USERNAME_KEY]: user.login?.trim() ?? '',
                    [EMAIL_KEY]: user.email?.trim() ?? '',
                    [REGISTRATION_NUMBER_KEY]: user.visibleRegistrationNumber?.trim() ?? '',
                };
            });
            const keys = [NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY];
            const fileName = this.exportFileName();
            exportUserInformationAsCsv(rows, keys, fileName);
        }
    };

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
