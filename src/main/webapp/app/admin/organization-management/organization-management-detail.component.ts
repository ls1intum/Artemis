import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Organization } from 'app/entities/organization.model';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { AlertService } from 'app/core/util/alert.service';
import { User } from 'app/core/user/user.model';
import { Observable, Subject, Subscription, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { iconsAsHTML } from 'app/utils/icons.utils';
import { UserService } from 'app/core/user/user.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { faUserSlash } from '@fortawesome/free-solid-svg-icons';

const cssClasses = {
    alreadyMember: 'already-member',
    newlyAddedMember: 'newly-added-member',
};

@Component({
    selector: 'jhi-organization-management-detail',
    templateUrl: './organization-management-detail.component.html',
})
export class OrganizationManagementDetailComponent implements OnInit {
    @ViewChild(DataTableComponent) dataTable: DataTableComponent;
    organization: Organization;

    readonly ActionType = ActionType;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    filteredUsersSize = 0;
    paramSub: Subscription;

    isLoading = false;
    isSearching = false;
    searchFailed = false;
    searchNoResults = false;
    isTransitioning = false;
    rowClass: string | undefined = undefined;

    // Icons
    faUserSlash = faUserSlash;

    constructor(private organizationService: OrganizationManagementService, private userService: UserService, private alertService: AlertService, private route: ActivatedRoute) {}

    /**
     * Retrieve the organization from the organization management activated route data {@link OrganizationMgmtResolve} subscription
     * and get the organization based on its id
     */
    ngOnInit() {
        this.route.data.subscribe(({ organization }) => {
            this.organization = new Organization();
            if (organization) {
                const organizationId = organization.body ? organization.body.id : organization.id;
                this.organizationService.getOrganizationByIdWithUsersAndCourses(organizationId).subscribe((organizationWithUserAndCourses) => {
                    this.organization = organizationWithUserAndCourses;
                });
            }
        });
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index of a user in the collection
     * @param item current user
     */
    trackIdentity(index: number, item: User) {
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
                this.searchFailed = false;
                this.searchNoResults = false;
                if (loginOrName.length < 3) {
                    return of([]);
                }
                this.isSearching = true;
                return this.userService
                    .search(loginOrName)
                    .pipe(map((usersResponse) => usersResponse.body!))
                    .pipe(
                        tap((users) => {
                            if (users.length === 0) {
                                this.searchNoResults = true;
                            }
                        }),
                        catchError(() => {
                            this.searchFailed = true;
                            return of([]);
                        }),
                    );
            }),
            tap(() => {
                this.isSearching = false;
            }),
            tap((users) => {
                setTimeout(() => {
                    for (let i = 0; i < this.dataTable.typeaheadButtons.length; i++) {
                        if (this.organization.users === undefined) {
                            this.organization.users = [];
                        }
                        const isAlreadyInOrganization = this.organization.users!.map((user) => user.id).includes(users[i].id);
                        this.dataTable.typeaheadButtons[i].insertAdjacentHTML('beforeend', iconsAsHTML[isAlreadyInOrganization ? 'users' : 'users-plus']);
                        if (isAlreadyInOrganization) {
                            this.dataTable.typeaheadButtons[i].classList.add(cssClasses.alreadyMember);
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
        if (user.login) {
            this.organizationService.removeUserFromOrganization(this.organization.id!, user.login).subscribe({
                next: () => {
                    this.organization.users = this.organization.users!.filter((u) => u.login !== user.login);
                    this.dialogErrorSource.next('');
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
        }
    }

    /**
     * Load all users of the viewed organization
     */
    loadAll() {
        this.isLoading = true;
        this.route.data.subscribe(({ organization }) => {
            this.organization = new Organization();
            if (organization) {
                const organizationId = organization.body ? organization.body.id : organization.id;
                this.organizationService.getOrganizationByIdWithUsersAndCourses(organizationId).subscribe((organizationWithUserAndCourses) => {
                    this.organization = organizationWithUserAndCourses;
                    this.isLoading = false;
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
        // If the user is not part of this organization yet, perform the server call to add them
        if (!this.organization.users!.map((u) => u.id).includes(user.id) && user.login) {
            this.isTransitioning = true;
            this.organizationService.addUserToOrganization(this.organization.id!, user.login).subscribe({
                next: () => {
                    this.isTransitioning = false;

                    // Add newly added user to the list of all users in the organization
                    this.organization.users!.push(user);

                    // Hand back over to the data table for updating
                    callback(user);

                    // Flash green background color to signal to the user that this record was added
                    this.flashRowClass(cssClasses.newlyAddedMember);
                },
                error: () => {
                    this.isTransitioning = false;
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
        return this.rowClass;
    };

    /**
     * Can be used to highlight rows temporarily by flashing a certain css class
     *
     * @param className Name of the class to be applied to all rows
     */
    flashRowClass = (className: string) => {
        this.rowClass = className;
        setTimeout(() => (this.rowClass = undefined));
    };

    /**
     * Update the number of filtered users
     *
     * @param filteredUsersSize Total number of users after filters have been applied
     */
    handleUsersSizeChange = (filteredUsersSize: number) => {
        this.filteredUsersSize = filteredUsersSize;
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
