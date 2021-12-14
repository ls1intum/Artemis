import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Data, ParamMap, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { onError } from 'app/shared/util/global.utils';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { combineLatest, Subject } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { AbstractControl, FormControl, FormGroup } from '@angular/forms';
import { EventManager } from 'app/core/util/event-manager.service';
import { ParseLinks } from 'app/core/util/parse-links.service';
import { ASC, DESC, ITEMS_PER_PAGE, SORT } from 'app/shared/constants/pagination.constants';
import { faEye, faPlus, faSort, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';

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
    ) {
        this.search
            .pipe(
                tap(() => (this.loadingSearchResult = true)),
                debounceTime(1000),
                switchMap(() =>
                    this.userService.query({
                        page: this.page - 1,
                        pageSize: this.itemsPerPage,
                        searchTerm: this.searchTermString,
                        sortingOrder: this.ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING,
                        sortedColumn: this.predicate,
                    }),
                ),
            )
            .subscribe(
                (res: HttpResponse<User[]>) => {
                    this.loadingSearchResult = false;
                    this.onSuccess(res.body || [], res.headers);
                },
                (res: HttpErrorResponse) => {
                    this.loadingSearchResult = false;
                    onError(this.alertService, res);
                },
            );
    }

    /**
     * Retrieves the current user and calls the {@link loadAll} and {@link registerChangeInUsers} methods on init
     */
    ngOnInit(): void {
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
        combineLatest(this.activatedRoute.data, this.activatedRoute.queryParamMap, (data: Data, params: ParamMap) => {
            const page = params.get('page');
            this.page = page != undefined ? +page : 1;
            const sort = (params.get(SORT) ?? data['defaultSort']).split(',');
            this.predicate = sort[0];
            this.ascending = sort[1] === ASC;
            this.loadAll();
        }).subscribe();
    }

    /**
     * Deletes a user
     * @param login of the user that should be deleted
     */
    deleteUser(login: string) {
        this.userService.delete(login).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'userListModification',
                    content: 'Deleted a user',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
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
