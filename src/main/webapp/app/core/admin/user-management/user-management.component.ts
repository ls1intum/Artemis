import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, inject } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, Subscription, combineLatest } from 'rxjs';
import { onError } from 'app/shared/util/global.utils';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { switchMap, tap } from 'rxjs/operators';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { EventManager } from 'app/shared/service/event-manager.service';
import { ASC, DESC, ITEMS_PER_PAGE, SORT } from 'app/shared/constants/pagination.constants';
import { faEye, faFilter, faPlus, faSort, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { LocalStorageService } from 'ngx-webstorage';
import { NgbHighlight, NgbModal, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AdminUserService } from 'app/core/user/shared/admin-user.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UsersImportButtonComponent } from 'app/shared/user-import/button/users-import-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteUsersButtonComponent } from './delete-users-button/delete-users-button.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { NgClass } from '@angular/common';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PROFILE_LDAP, addPublicFilePrefix } from 'app/app.constants';

export class UserFilter {
    authorityFilter: Set<AuthorityFilter> = new Set();
    originFilter: Set<OriginFilter> = new Set();
    statusFilter: Set<StatusFilter> = new Set();
    registrationNumberFilter: Set<RegistrationNumberFilter> = new Set();
    noAuthority = false;

    /**
     * Adds the http param options
     * @param options request options
     */
    adjustOptions(options: HttpParams) {
        if (this.noAuthority) {
            options = options.append('authorities', 'NO_AUTHORITY');
        } else {
            options = options.append('authorities', [...this.authorityFilter].join(','));
        }
        options = options.append('origins', [...this.originFilter].join(','));
        options = options.append('registrationNumbers', [...this.registrationNumberFilter].join(','));
        options = options.append('status', [...this.statusFilter].join(','));
        return options;
    }

    /**
     * Returns the number of applied filters.
     */
    get numberOfAppliedFilters() {
        return this.authorityFilter.size + this.originFilter.size + this.registrationNumberFilter.size + this.statusFilter.size + (this.noAuthority ? 1 : 0);
    }
}

export enum AuthorityFilter {
    ADMIN = 'ADMIN',
    INSTRUCTOR = 'INSTRUCTOR',
    EDITOR = 'EDITOR',
    TA = 'TA',
    USER = 'USER',
}

export enum OriginFilter {
    INTERNAL = 'INTERNAL',
    EXTERNAL = 'EXTERNAL',
}

export enum RegistrationNumberFilter {
    WITH_REG_NO = 'WITH_REG_NO',
    WITHOUT_REG_NO = 'WITHOUT_REG_NO',
}

export enum StatusFilter {
    ACTIVATED = 'ACTIVATED',
    DEACTIVATED = 'DEACTIVATED',
}

export enum UserStorageKey {
    AUTHORITY = 'artemis.userManagement.authority',
    NO_AUTHORITY = 'artemis.userManagement.noAuthority',
    ORIGIN = 'artemis.userManagement.origin',
    STATUS = 'artemis.userManagement.status',
    REGISTRATION_NUMBER = 'artemis.userManagement.registrationNumber',
}

type Filter = typeof AuthorityFilter | typeof OriginFilter | typeof StatusFilter | typeof RegistrationNumberFilter;

@Component({
    selector: 'jhi-user-management',
    templateUrl: './user-management.component.html',
    styleUrls: ['./user-management.component.scss'],
    imports: [
        TranslateDirective,
        UsersImportButtonComponent,
        RouterLink,
        FaIconComponent,
        FormsModule,
        ReactiveFormsModule,
        DeleteUsersButtonComponent,
        DeleteButtonDirective,
        NgClass,
        SortDirective,
        SortByDirective,
        ProfilePictureComponent,
        NgbHighlight,
        ItemCountComponent,
        NgbPagination,
        HelpIconComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
    ],
})
export class UserManagementComponent implements OnInit, OnDestroy {
    private adminUserService = inject(AdminUserService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private eventManager = inject(EventManager);
    private localStorage = inject(LocalStorageService);
    private modalService = inject(NgbModal);
    private profileService = inject(ProfileService);

    @ViewChild('filterModal') filterModal: TemplateRef<any>;

    search = new Subject<void>();
    loadingSearchResult = false;
    currentAccount?: User;
    users: User[];
    selectedUsers: User[] = [];
    userListSubscription?: Subscription;
    totalItems = 0;
    itemsPerPage = ITEMS_PER_PAGE;
    page!: number;
    predicate!: string;
    ascending!: boolean;
    searchTermString = '';
    searchInvalid = false;
    isLdapProfileActive: boolean;

    // filters
    filters: UserFilter = new UserFilter();
    faFilter = faFilter;
    authorityKey = UserStorageKey.AUTHORITY;
    statusKey = UserStorageKey.STATUS;
    originKey = UserStorageKey.ORIGIN;
    registrationKey = UserStorageKey.REGISTRATION_NUMBER;

    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();
    userSearchForm: FormGroup;

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;

    readonly medium = ButtonSize.MEDIUM;
    readonly ButtonType = ButtonType;

    /**
     * Retrieves the current user and calls the {@link loadAll} and {@link registerChangeInUsers} methods on init
     */
    ngOnInit(): void {
        this.initFilters();
        this.search
            .pipe(
                tap(() => (this.loadingSearchResult = true)),
                switchMap(() =>
                    this.adminUserService.query(
                        {
                            page: this.page - 1,
                            pageSize: this.itemsPerPage,
                            searchTerm: this.searchTermString,
                            sortingOrder: this.ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING,
                            sortedColumn: this.predicate,
                            filters: this.filters,
                        },
                        this.filters,
                    ),
                ),
            )
            .subscribe({
                next: (res: HttpResponse<User[]>) => {
                    this.loadingSearchResult = false;
                    this.onSuccess(res.body || [], res.headers);
                },
                error: (res: HttpErrorResponse) => {
                    this.loadingSearchResult = false;
                    onError(this.alertService, res);
                },
            });

        this.userSearchForm = new FormGroup({
            searchControl: new FormControl('', { updateOn: 'change' }),
        });
        this.accountService.identity().then((user) => {
            this.currentAccount = user!;
            this.userListSubscription = this.eventManager.subscribe('userListModification', () => this.loadAll());
            this.handleNavigation();
        });
        this.isLdapProfileActive = this.profileService.isProfileActive(PROFILE_LDAP);
    }

    /**
     * clean up the subscriptions
     */
    ngOnDestroy(): void {
        if (this.userListSubscription) {
            this.eventManager.destroy(this.userListSubscription);
        }
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Inits the available filter and maps the functions.
     */
    initFilters() {
        this.filters.authorityFilter = this.initFilter<AuthorityFilter>(UserStorageKey.AUTHORITY, AuthorityFilter);
        this.filters.originFilter = this.initFilter<OriginFilter>(UserStorageKey.ORIGIN, OriginFilter);
        this.filters.registrationNumberFilter = this.initFilter<RegistrationNumberFilter>(UserStorageKey.REGISTRATION_NUMBER, RegistrationNumberFilter);
        this.filters.statusFilter = this.initFilter<StatusFilter>(UserStorageKey.STATUS, StatusFilter);
        this.filters.noAuthority = !!this.localStorage.retrieve(UserStorageKey.NO_AUTHORITY);
    }

    /**
     * Inits a specific filter.
     * @param key of the filter in the local storage
     * @param type of filter
     */
    initFilter<E>(key: UserStorageKey, type: Filter): Set<E> {
        const temp = this.localStorage.retrieve(key);
        const tempInStorage = temp
            ? temp
                  .split(',')
                  .map((filter: keyof Filter) => type[filter])
                  .filter(Boolean)
            : new Set();
        return new Set(tempInStorage);
    }

    /**
     * Method to add or remove a filter and store the selected filters in the local store if required.
     */
    toggleFilter<E>(filter: Set<E>, value: E, key?: UserStorageKey) {
        if (filter.has(value)) {
            filter.delete(value);
        } else {
            filter.add(value);
        }
        if (key) {
            this.localStorage.store(key, Array.from(filter).join(','));
        }
    }

    /**
     * Method to add or remove an authority filter and store the selected authority filters in the local store if required.
     */
    toggleAuthorityFilter(filter: Set<AuthorityFilter>, value: AuthorityFilter) {
        this.filters.noAuthority = false;
        this.updateNoAuthority(false);
        this.toggleFilter<AuthorityFilter>(filter, value, this.authorityKey);
    }

    /**
     * Method to add or remove an origin filter and store the selected origin filters in the local store if required.
     */
    toggleOriginFilter(value?: OriginFilter) {
        const filter = this.filters.originFilter;
        this.deselectFilter<OriginFilter>(filter, this.originKey);
        if (value) {
            this.toggleFilter<OriginFilter>(filter, value, this.originKey);
        }
    }

    /**
     * Method to add or remove a status filter and store the selected status filters in the local store if required.
     */
    toggleStatusFilter(value?: StatusFilter) {
        const filter = this.filters.statusFilter;
        this.deselectFilter<StatusFilter>(filter, this.statusKey);
        if (value) {
            this.toggleFilter<StatusFilter>(filter, value, this.statusKey);
        }
    }

    /**
     * Method to add or remove a registration number filter and store the selected filters in the local store if required.
     * @param registrationNumber corresponds to the registration number filter that is added or removed when the user clicks on the checkbox.
     * When the filter is added, the value is set to the filter. Thus, when the value is present, the filter is toggled.
     */
    toggleRegistrationNumberFilter(registrationNumber?: RegistrationNumberFilter) {
        const filter = this.filters.registrationNumberFilter;
        this.deselectFilter<RegistrationNumberFilter>(filter, this.registrationKey);
        if (registrationNumber) {
            this.toggleFilter<RegistrationNumberFilter>(filter, registrationNumber, this.registrationKey);
        }
    }

    /**
     * Deselect filter.
     */
    deselectFilter<E>(filter: Set<E>, key: UserStorageKey) {
        if (filter.size) {
            this.toggleFilter<E>(filter, Array.from(filter).pop()!, key);
        }
    }

    /**
     * Get all filter options for authorities.
     */
    get authorityFilters() {
        return Object.values<AuthorityFilter>(AuthorityFilter);
    }

    /**
     * Get all filter options for origin.
     */
    get originFilters() {
        return Object.values<OriginFilter>(OriginFilter);
    }

    /**
     * Get all filter options for status.
     */
    get statusFilters() {
        return Object.values<StatusFilter>(StatusFilter);
    }

    get registrationNumberFilters() {
        return Object.values<RegistrationNumberFilter>(RegistrationNumberFilter);
    }

    /**
     * Update the no authority selection and the local storage.
     * @param value new value
     */
    updateNoAuthority(value: boolean) {
        this.localStorage.store(UserStorageKey.NO_AUTHORITY, value);
        this.filters.noAuthority = value;
    }

    /**
     * Deselect all roles
     */
    deselectAllRoles() {
        this.filters.authorityFilter.clear();
        this.localStorage.clear(UserStorageKey.AUTHORITY);
        this.updateNoAuthority(false);
    }

    /**
     * Select empty roles
     */
    selectEmptyRoles() {
        this.filters.authorityFilter.clear();
        this.updateNoAuthority(true);
    }

    /**
     * Select all roles
     */
    selectAllRoles() {
        this.filters.authorityFilter = new Set(this.authorityFilters);
        this.updateNoAuthority(false);
    }

    /**
     * Opens the modal.
     */
    open(content: any) {
        this.modalService.open(content);
    }

    /**
     * Apply the filter and close the modal.
     */
    applyFilter() {
        this.loadAll();
        this.modalService.dismissAll();
    }

    /**
     * Update the user's activation status
     * @param user whose activation status should be changed
     * @param isActivated true if user should be activated, otherwise false
     */
    setActive(user: User, isActivated: boolean) {
        user.activated = isActivated;
        const action = isActivated ? this.adminUserService.activate : this.adminUserService.deactivate;
        action.call(this.adminUserService, user.id!).subscribe(() => {
            this.loadAll();
        });
    }

    /**
     * Selects/Unselects all (filtered) users.
     */
    toggleAllUserSelection() {
        const usersWithoutCurrentUser = this.usersWithoutCurrentUser;
        if (this.selectedUsers.length === usersWithoutCurrentUser.length) {
            // Clear all users
            this.selectedUsers = [];
        } else {
            // Add all users
            this.selectedUsers = [...usersWithoutCurrentUser];
        }
    }

    /**
     * Gets the users without the current user.
     */
    get usersWithoutCurrentUser() {
        return this.users.filter((user) => this.currentAccount && this.currentAccount.login !== user.login);
    }

    /**
     * Selects/Unselects a user.
     */
    toggleUser(user: User) {
        const index = this.selectedUsers.indexOf(user);
        if (index > -1) {
            this.selectedUsers.splice(index, 1);
        } else {
            // Add all users
            this.selectedUsers.push(user);
        }
    }

    /**
     * Delete all selected users.
     */
    deleteAllSelectedUsers() {
        const logins = this.selectedUsers.map((user) => user.login!);
        this.adminUserService.deleteUsers(logins).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'userListModification',
                    content: 'Deleted users',
                });
                this.selectedUsers = [];
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Actions after the deletion of not enrolled users is completed.
     */
    deleteNotEnrolledUsersComplete() {
        this.eventManager.broadcast({
            name: 'userListModification',
            content: 'Deleted users',
        });
    }

    /**
     * Retrieve the list of users from the user service for a single page in the user management based on the page, size and sort configuration
     */
    loadAll() {
        this.searchTerm = this.searchControl.value;
        if (this.searchTerm.length >= 3 || this.searchTerm.length === 0) {
            this.searchInvalid = false;
            this.search.next();
        } else {
            this.searchInvalid = true;
        }
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
     * Transitions to another page and/or sorting order
     */
    transition(): void {
        this.router.navigate(['/admin/user-management'], {
            relativeTo: this.activatedRoute.parent,
            queryParams: {
                page: this.page,
                sort: `${this.predicate},${this.ascending ? ASC : DESC}`,
            },
        });
    }

    private handleNavigation(): void {
        combineLatest({
            data: this.activatedRoute.data,
            params: this.activatedRoute.queryParamMap,
        }).subscribe(({ data, params }) => {
            const page = params.get('page');
            this.page = page != undefined ? +page : 1;
            const sort = (params.get(SORT) ?? data['defaultSort']).split(',');
            this.predicate = sort[0];
            this.ascending = sort[1] === ASC;
            this.loadAll();
        });
    }

    /**
     * Deletes a user
     * @param login of the user that should be deleted
     */
    deleteUser(login: string) {
        this.adminUserService.deleteUser(login).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'userListModification',
                    content: 'Deleted a user',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    ldapSync(userId: number) {
        this.adminUserService.syncLdap(userId).subscribe(() => {
            this.loadAll();
        });
    }

    private onSuccess(users: User[], headers: HttpHeaders) {
        this.totalItems = Number(headers.get('X-Total-Count'));
        this.users = users;
    }

    set searchTerm(searchTerm: string) {
        this.searchTermString = searchTerm;
    }

    get searchTerm(): string {
        return this.searchTermString;
    }

    get searchControl() {
        return this.userSearchForm.get('searchControl')!;
    }

    onKeydown(event: KeyboardEvent) {
        if (event.key === 'Enter') {
            event.preventDefault(); // Prevent the default form submission behavior
            this.loadAll(); // Trigger the search logic
        }
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
