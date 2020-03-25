import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiEventManager, JhiParseLinks } from 'ng-jhipster';
import { Subscription } from 'rxjs/Subscription';
import { onError } from 'app/shared/util/global.utils';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { Subject } from 'rxjs';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { AlertService } from 'app/core/alert/alert.service';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { FormControl, AbstractControl, FormGroup } from '@angular/forms';

@Component({
    selector: 'jhi-user-management',
    templateUrl: './user-management.component.html',
})
export class UserManagementComponent implements OnInit, OnDestroy {
    search = new Subject<string>();
    loadingSearchResult = false;
    currentAccount: User;
    users: User[];
    error: string | null;
    success: string | null;
    routeData: Subscription;
    links: any;
    totalItems: number;
    itemsPerPage: number;
    page: number;
    predicate: string;
    previousPage: number;
    reverse: boolean;
    searchTermString: string;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    userSearchForm: FormGroup;
    constructor(
        private userService: UserService,
        private alertService: AlertService,
        private accountService: AccountService,
        private parseLinks: JhiParseLinks,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private eventManager: JhiEventManager,
    ) {
        this.itemsPerPage = ITEMS_PER_PAGE;
        this.searchTermString = '';
        this.routeData = this.activatedRoute.data.subscribe((data) => {
            this.page = data['pagingParams'].page;
            this.previousPage = data['pagingParams'].page;
            this.reverse = data['pagingParams'].ascending;
            this.predicate = data['pagingParams'].predicate;
        });
        this.search
            .pipe(
                tap(() => (this.loadingSearchResult = true)),
                debounceTime(1000),
                switchMap(() =>
                    this.userService.query({
                        page: this.page - 1,
                        pageSize: this.itemsPerPage,
                        searchTerm: this.searchTermString,
                        sortingOrder: this.reverse ? SortingOrder.ASCENDING : SortingOrder.DESCENDING,
                        sortedColumn: this.predicate,
                    }),
                ),
            )
            .subscribe(
                (res: HttpResponse<User[]>) => {
                    this.loadingSearchResult = false;
                    this.onSuccess(res.body!, res.headers);
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
    ngOnInit() {
        this.userSearchForm = new FormGroup({
            searchControl: new FormControl('', { validators: [this.validateUserSearch], updateOn: 'blur' }),
        });
        this.accountService.identity().then((user) => {
            this.currentAccount = user!;
            this.loadAll();
            this.registerChangeInUsers();
        });
    }

    /**
     * Unsubscribe from routeData
     */
    ngOnDestroy() {
        this.routeData.unsubscribe();
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Subscribe to event 'userListModification' and call {@link loadAll} on event broadcast
     */
    registerChangeInUsers() {
        this.eventManager.subscribe('userListModification', () => this.loadAll());
    }

    /**
     * Update the user's activation status
     * @param user whose activation status should be changed
     * @param isActivated true if user should be activated, otherwise false
     */
    setActive(user: User, isActivated: boolean) {
        user.activated = isActivated;

        this.userService.update(user).subscribe((response) => {
            if (response.status === 200) {
                this.error = null;
                this.success = 'OK';
                this.loadAll();
            } else {
                this.success = null;
                this.error = 'ERROR';
            }
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
        return item.id;
    }

    /**
     * Loads specified page, if it is not the same as previous one
     * @param page number of the page that will be loaded
     */
    loadPage(page: number) {
        if (page !== this.previousPage) {
            this.previousPage = page;
            this.transition();
        }
    }

    /**
     * Transitions to another page and/or sorting order
     */
    transition() {
        this.router.navigate(['/admin/user-management'], {
            queryParams: {
                page: this.page,
                sortingOrder: this.reverse ? SortingOrder.ASCENDING : SortingOrder.DESCENDING,
                sortedColumn: this.predicate,
            },
        });
        this.loadAll();
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

    private onSuccess(data: User[], headers: HttpHeaders) {
        this.links = this.parseLinks.parse(headers.get('link')!);
        this.totalItems = +headers.get('X-Total-Count')!;
        this.users = data;
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
