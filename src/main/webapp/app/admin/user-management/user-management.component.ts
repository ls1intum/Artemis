import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { combineLatest, Subject, Subscription } from 'rxjs';
import { onError } from 'app/shared/util/global.utils';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
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

export class UserFilter {
    authorityFilter: Set<AuthorityFilter> = new Set();
    originFilter: Set<OriginFilter> = new Set();
    statusFilter: Set<StatusFilter> = new Set();
    courseFilter: Set<number> = new Set();
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

export enum StatusFilter {
    ACTIVATED = 'ACTIVATED',
    DEACTIVATED = 'DEACTIVATED',
}

enum UserStorageKey {
    AUTHORITY = 'artemis.userManagement.authority',
    ORIGIN = 'artemis.userManagement.origin',
    STATUS = 'artemis.userManagement.status',
}

@Component({
    selector: 'jhi-user-management',
    templateUrl: './user-management.component.html',
})
export class UserManagementComponent implements OnInit, OnDestroy {
    search = new Subject<void>();
    loadingSearchResult = false;
    currentAccount?: User;
    users: User[];
    userListSubscription?: Subscription;
    totalItems = 0;
    itemsPerPage = ITEMS_PER_PAGE;
    page!: number;
    predicate!: string;
    ascending!: boolean;
    searchTermString = '';

    // filters
    filters: UserFilter = new UserFilter();
    faFilter = faFilter;
    courses: Course[] = [];
    authorityKey = UserStorageKey.AUTHORITY;
    statusKey = UserStorageKey.STATUS;
    originKey = UserStorageKey.ORIGIN;

    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();
    userSearchForm: FormGroup;

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;

    constructor(
        private userService: UserService,
        private alertService: AlertService,
        private accountService: AccountService,
        private parseLinks: ParseLinks,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private eventManager: EventManager,
        private localStorage: LocalStorageService,
        private curseManagementService: CourseManagementService,
    ) {}

    /**
     * Retrieves the current user and calls the {@link loadAll} and {@link registerChangeInUsers} methods on init
     */
    ngOnInit(): void {
        // Load all courses and create id to title map
        this.curseManagementService.getAll().subscribe((courses) => {
            if (courses.body) {
                this.courses = courses.body;
            }
            this.initFilters();
        });

        this.search
            .pipe(
                tap(() => (this.loadingSearchResult = true)),
                debounceTime(1000),
                switchMap(() =>
                    this.userService.query(
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
     * Inits the available filter and maps the functions
     */
    initFilters() {
        this.filters.authorityFilter = this.initFilter(UserStorageKey.AUTHORITY, AuthorityFilter) as Set<AuthorityFilter>;
        this.filters.originFilter = this.initFilter(UserStorageKey.ORIGIN, OriginFilter) as Set<OriginFilter>;
        this.filters.statusFilter = this.initFilter(UserStorageKey.STATUS, StatusFilter) as Set<StatusFilter>;

        this.courses.forEach((course) => this.filters.courseFilter.add(course.id!));
    }

    /**
     * Inits a specific filter
     * @param key of the filter in the local storage
     * @param type of filter
     */
    initFilter(key: UserStorageKey, type: any) {
        const temp = this.localStorage.retrieve(key);
        const tempInStorage =
            temp !== null
                ? temp
                      .split(',')
                      .map((filter: string) => type[filter])
                      .filter(Boolean)
                : this.getFilter(type);
        return new Set(tempInStorage);
    }

    /**
     * Method to add or remove a filter and store the selected filters in the local store if required.
     */
    toggleFilter(filter: Set<any>, value: any, key?: UserStorageKey) {
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
     * Generic method to return all possible filter values per category.
     */
    getFilter(type: any) {
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

    /**
     * Get all filter options for course.
     */
    get courseFilters() {
        return this.courses;
    }

    /**
     * Update the user's activation status
     * @param user whose activation status should be changed
     * @param isActivated true if user should be activated, otherwise false
     */
    setActive(user: User, isActivated: boolean) {
        user.activated = isActivated;
        this.userService.update(user).subscribe(() => {
            this.loadAll();
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
        this.userService.delete(login).subscribe({
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
