import { Component, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, Subscription, combineLatest } from 'rxjs';
import { onError } from 'app/shared/util/global.utils';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { AbstractControl, FormControl, FormGroup } from '@angular/forms';
import { EventManager } from 'app/core/util/event-manager.service';
import { ParseLinks } from 'app/core/util/parse-links.service';
import { ASC, DESC, ITEMS_PER_PAGE, SORT } from 'app/shared/constants/pagination.constants';
import { faEye, faFilter, faPlus, faSort, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { LocalStorageService } from 'ngx-webstorage';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize } from 'app/shared/components/button.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { AdminUserService } from 'app/core/user/admin-user.service';
import { UserService } from 'app/core/user/user.service';

export class UserFilter {
    authorityFilter: Set<AuthorityFilter> = new Set();
    originFilter: Set<OriginFilter> = new Set();
    statusFilter: Set<StatusFilter> = new Set();
    courseFilter: Set<number> = new Set();
    registrationNumberFilter: Set<RegistrationNumberFilter> = new Set();
    noAuthority = false;
    noCourse = false;

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
        if (this.noCourse) {
            // -1 means that we filter for users without any course
            options = options.append('courseIds', -1);
        } else {
            options = options.append('courseIds', [...this.courseFilter].join(','));
        }
        return options;
    }

    /**
     * Returns the number of applied filters.
     */
    get numberOfAppliedFilters() {
        return (
            this.authorityFilter.size +
            this.originFilter.size +
            this.registrationNumberFilter.size +
            this.statusFilter.size +
            this.courseFilter.size +
            (this.noAuthority ? 1 : 0) +
            (this.noCourse ? 1 : 0)
        );
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
    NO_COURSE = 'artemis.userManagement.noCourse',
    REGISTRATION_NUMBER = 'artemis.userManagement.registrationNumber',
}

type Filter = typeof AuthorityFilter | typeof OriginFilter | typeof StatusFilter | typeof RegistrationNumberFilter;

@Component({
    selector: 'jhi-user-management',
    templateUrl: './user-management.component.html',
    styleUrls: ['./user-management.component.scss'],
})
export class UserManagementComponent implements OnInit, OnDestroy {
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
    isLdapProfileActive: boolean;

    // filters
    filters: UserFilter = new UserFilter();
    faFilter = faFilter;
    courses: Course[] = [];
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

    constructor(
        private adminUserService: AdminUserService,
        private userService: UserService,
        private alertService: AlertService,
        private accountService: AccountService,
        private parseLinks: ParseLinks,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private eventManager: EventManager,
        private localStorage: LocalStorageService,
        private courseManagementService: CourseManagementService,
        private modalService: NgbModal,
        private profileService: ProfileService,
    ) {}

    /**
     * Retrieves the current user and calls the {@link loadAll} and {@link registerChangeInUsers} methods on init
     */
    ngOnInit(): void {
        // Load all courses and create id to title map
        this.courseManagementService.getAll().subscribe((courses) => {
            if (courses.body) {
                this.courses = courses.body.sort((c1, c2) => (c1.title ?? '').localeCompare(c2.title ?? ''));
            }
            this.initFilters();
        });
        this.search
            .pipe(
                tap(() => (this.loadingSearchResult = true)),
                debounceTime(1000),
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
            searchControl: new FormControl('', { validators: [this.validateUserSearch], updateOn: 'blur' }),
        });
        this.accountService.identity().then((user) => {
            this.currentAccount = user!;
            this.userListSubscription = this.eventManager.subscribe('userListModification', () => this.loadAll());
            this.handleNavigation();
        });
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.isLdapProfileActive = profileInfo.activeProfiles && profileInfo.activeProfiles?.includes('ldap');
        });
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

        this.filters.noCourse = !!this.localStorage.retrieve(UserStorageKey.NO_COURSE);
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
                  .map((filter: string) => type[filter])
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
     * Method to add or remove a course filter.
     */
    toggleCourseFilter(filter: Set<number>, value: number) {
        this.filters.noCourse = false;
        this.updateNoCourse(false);
        this.toggleFilter<number>(filter, value);
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
     * Generic method to return all possible filter values per category.
     */
    getFilter(type: Filter) {
        return Object.keys(type).map((value) => type[value]);
    }

    /**
     * Get all filter options for authorities.
     */
    get authorityFilters() {
        return this.getFilter(AuthorityFilter);
    }

    /**
     * Get all filter options for origin.
     */
    get originFilters() {
        return this.getFilter(OriginFilter);
    }

    /**
     * Get all filter options for status.
     */
    get statusFilters() {
        return this.getFilter(StatusFilter);
    }

    get registrationNumberFilters() {
        return this.getFilter(RegistrationNumberFilter);
    }

    /**
     * Get all filter options for course.
     */
    get courseFilters() {
        return this.courses;
    }

    /**
     * Update the no course selection and the local storage.
     * @param value new value
     */
    updateNoCourse(value: boolean) {
        this.localStorage.store(UserStorageKey.NO_COURSE, value);
        this.filters.noCourse = value;
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
     * Deselect all courses
     */
    deselectAllCourses() {
        this.filters.courseFilter.clear();
        this.updateNoCourse(false);
    }

    /**
     * Select all users without course
     */
    selectEmptyCourses() {
        this.filters.courseFilter.clear();
        this.updateNoCourse(true);
    }

    /**
     * Select all courses
     */
    selectAllCourses() {
        this.filters.courseFilter = new Set(this.courses.map((course) => course.id!));
        this.updateNoCourse(false);
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
        this.modalService.open(content).result.then();
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
        this.adminUserService.update(user).subscribe(() => {
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
     * Retrieve the list of users from the user service for a single page in the user management based on the page, size and sort configuration
     */
    loadAll() {
        if (this.searchTerm.length >= 3 || this.searchTerm.length === 0) {
            this.search.next();
        }
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
        this.userService.syncLdap(userId).subscribe(() => {
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

    validateUserSearch(control: AbstractControl) {
        if (control.value.length >= 1 && control.value.length <= 2) {
            return { searchControl: true };
        }
        return null;
    }

    get searchControl() {
        return this.userSearchForm.get('searchControl')!;
    }
}
