import { ChangeDetectionStrategy, Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Organization } from 'app/core/shared/entities/organization.model';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { User } from 'app/core/user/user.model';
import { Observable, Subject, Subscription, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { iconsAsHTML } from 'app/shared/util/icons.utils';
import { UserService } from 'app/core/user/shared/user.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { faUserSlash } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';

const cssClasses = {
    alreadyMember: 'already-member',
    newlyAddedMember: 'newly-added-member',
};

/**
 * Admin component for viewing and managing organization details.
 * Allows adding/removing users from organizations.
 */
@Component({
    selector: 'jhi-organization-management-detail',
    templateUrl: './organization-management-detail.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, RouterLink, DataTableComponent, NgxDatatableModule, FaIconComponent, DeleteButtonDirective],
})
export class OrganizationManagementDetailComponent implements OnInit {
    private readonly organizationService = inject(OrganizationManagementService);
    private readonly userService = inject(UserService);
    private readonly route = inject(ActivatedRoute);

    /** Reference to the data table component */
    readonly dataTable = viewChild(DataTableComponent);

    /** The organization being viewed */
    readonly organization = signal<Organization>(new Organization());

    readonly ActionType = ActionType;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    paramSub: Subscription;

    /** Number of filtered users */
    readonly filteredUsersSize = signal(0);

    /** Loading and state flags */
    readonly isLoading = signal(false);
    readonly isSearching = signal(false);
    readonly searchFailed = signal(false);
    readonly searchNoResults = signal(false);
    readonly isTransitioning = signal(false);
    readonly rowClass = signal('');

    protected readonly faUserSlash = faUserSlash;

    /**
     * Retrieve the organization from the organization management activated route data subscription
     * and get the organization based on its id
     */
    ngOnInit() {
        this.route.data.subscribe(({ organization }) => {
            this.organization.set(new Organization());
            if (organization) {
                const organizationId = organization.body ? organization.body.id : organization.id;
                this.organizationService.getOrganizationByIdWithUsersAndCourses(organizationId).subscribe((organizationWithUserAndCourses) => {
                    this.organization.set(organizationWithUserAndCourses);
                });
            }
        });
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param _index of a user in the collection
     * @param item current user
     */
    trackIdentity(_index: number, item: User) {
        return item.id ?? -1;
    }

    /**
     * Receives the search text and filter results from DataTableComponent, modifies them and returns the result which will be used by ngbTypeahead.
     *
     * 1. Perform server-side search using the search text
     * 2. Return results from server query that contain all users (instead of only the client-side users who are members already)
     *
     * @param stream$ stream of searches of the format {text, entities} where entities are the results
     * @return stream of users for the autocomplete
     */
    searchAllUsers = (stream$: Observable<{ text: string; entities: User[] }>): Observable<User[]> => {
        return stream$.pipe(
            switchMap(({ text: loginOrName }) => {
                this.searchFailed.set(false);
                this.searchNoResults.set(false);
                if (loginOrName.length < 3) {
                    return of([]);
                }
                this.isSearching.set(true);
                return this.userService
                    .search(loginOrName)
                    .pipe(map((usersResponse) => usersResponse.body!))
                    .pipe(
                        tap((users) => {
                            if (users.length === 0) {
                                this.searchNoResults.set(true);
                            }
                        }),
                        catchError(() => {
                            this.searchFailed.set(true);
                            return of([]);
                        }),
                    );
            }),
            tap(() => {
                this.isSearching.set(false);
            }),
            tap((users) => {
                setTimeout(() => {
                    const table = this.dataTable();
                    if (!table) return;
                    const org = this.organization();
                    for (let i = 0; i < table.typeaheadButtons.length; i++) {
                        const orgUsers = org.users ?? [];
                        const button = table.typeaheadButtons[i];
                        const isAlreadyInOrganization = orgUsers.map((user) => user.id).includes(users[i].id);
                        const hasIcon = button.querySelector('fa-icon');
                        if (!hasIcon) {
                            button.insertAdjacentHTML('beforeend', iconsAsHTML[isAlreadyInOrganization ? 'users' : 'users-plus']);
                        }
                        if (isAlreadyInOrganization) {
                            table.typeaheadButtons[i].classList.add(cssClasses.alreadyMember);
                        }
                    }
                });
            }),
        );
    };

    /**
     * Remove user from organization
     *
     * @param user User that should be removed from the currently viewed organization
     */
    removeFromOrganization(user: User) {
        const org = this.organization();
        if (user.login) {
            this.organizationService.removeUserFromOrganization(org.id!, user.login).subscribe({
                next: () => {
                    this.organization.update((o) => ({
                        ...o,
                        users: o.users!.filter((u) => u.login !== user.login),
                    }));
                    this.dialogErrorSource.next('');
                },
                error: (error: HttpErrorResponse) => {
                    this.dialogErrorSource.next(error.message);
                },
            });
        }
    }

    /**
     * Load all users of the viewed organization
     */
    loadAll() {
        this.isLoading.set(true);
        this.route.data.subscribe(({ organization }) => {
            this.organization.set(new Organization());
            if (organization) {
                const organizationId = organization.body ? organization.body.id : organization.id;
                this.organizationService.getOrganizationByIdWithUsersAndCourses(organizationId).subscribe((organizationWithUserAndCourses) => {
                    this.organization.set(organizationWithUserAndCourses);
                    this.isLoading.set(false);
                });
            }
        });
    }

    /**
     * Receives the user that was selected in the autocomplete and the callback from DataTableComponent.
     * The callback inserts the search term of the selected entity into the search field and updates the displayed users.
     *
     * @param user The selected user from the autocomplete suggestions
     * @param callback Function that can be called with the selected user to trigger the DataTableComponent default behavior
     */
    onAutocompleteSelect = (user: User, callback: (user: User) => void): void => {
        const org = this.organization();
        // If the user is not part of this organization yet, perform the server call to add them
        if (!org.users!.map((u) => u.id).includes(user.id) && user.login) {
            this.isTransitioning.set(true);
            this.organizationService.addUserToOrganization(org.id!, user.login).subscribe({
                next: () => {
                    this.isTransitioning.set(false);

                    // Add newly added user to the list of all users in the organization
                    this.organization.update((o) => ({
                        ...o,
                        users: [...(o.users ?? []), user],
                    }));

                    // Hand back over to the data table for updating
                    callback(user);

                    // Flash green background color to signal to the user that this record was added
                    this.flashRowClass(cssClasses.newlyAddedMember);
                },
                error: () => {
                    this.isTransitioning.set(false);
                },
            });
        } else {
            // Hand back over to the data table
            callback(user);
        }
    };

    /**
     * Converts a user object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param user User
     */
    searchTextFromUser = (user: User): string => {
        return user.login || '';
    };

    /**
     * Computes the row class that is being added to all rows of the datatable
     */
    dataTableRowClass = () => {
        return this.rowClass();
    };

    /**
     * Can be used to highlight rows temporarily by flashing a certain css class
     *
     * @param className Name of the class to be applied to all rows
     */
    flashRowClass = (className: string) => {
        this.rowClass.set(className);
        setTimeout(() => this.rowClass.set(''));
    };

    /**
     * Update the number of filtered users
     *
     * @param filteredUsersSize Total number of users after filters have been applied
     */
    handleUsersSizeChange = (filteredUsersSize: number) => {
        this.filteredUsersSize.set(filteredUsersSize);
    };

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param user
     */
    searchResultFormatter = (user: User) => {
        const { name, login } = user;
        return `${name} (${login})`;
    };
}
