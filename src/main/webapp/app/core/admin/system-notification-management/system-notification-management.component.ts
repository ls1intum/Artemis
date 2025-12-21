import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { onError } from 'app/shared/util/global.utils';
import { SystemNotification } from 'app/core/shared/entities/system-notification.model';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { ParseLinks } from 'app/core/admin/system-notification-management/parse-links.service';
import { faEye, faPlus, faSort, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';
import { AdminSystemNotificationService } from 'app/core/notification/system-notification/admin-system-notification.service';

/**
 * Enum representing the state of a system notification.
 */
enum NotificationState {
    SCHEDULED = 'SCHEDULED',
    ACTIVE = 'ACTIVE',
    EXPIRED = 'EXPIRED',
}

/**
 * Component for managing system notifications.
 * Displays a paginated list of notifications with sorting and deletion capabilities.
 */
@Component({
    selector: 'jhi-system-notification-management',
    templateUrl: './system-notification-management.component.html',
    imports: [TranslateDirective, RouterLink, FaIconComponent, SortDirective, SortByDirective, DeleteButtonDirective, ItemCountComponent, NgbPagination, ArtemisDatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemNotificationManagementComponent implements OnInit, OnDestroy {
    private readonly systemNotificationService = inject(SystemNotificationService);
    private readonly adminSystemNotificationService = inject(AdminSystemNotificationService);
    private readonly alertService = inject(AlertService);
    private readonly accountService = inject(AccountService);
    private readonly parseLinks = inject(ParseLinks);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly eventManager = inject(EventManager);

    /** Subscriptions that need cleanup on destroy */
    private routeDataSubscription?: Subscription;
    private eventManagerSubscription?: Subscription;

    /** Notification state constants for template access */
    protected readonly SCHEDULED = NotificationState.SCHEDULED;
    protected readonly ACTIVE = NotificationState.ACTIVE;
    protected readonly EXPIRED = NotificationState.EXPIRED;

    /** Current logged-in user */
    readonly currentAccount = signal<User | undefined>(undefined);

    /** List of system notifications */
    readonly notifications = signal<SystemNotification[]>([]);

    /** Pagination links parsed from response headers */
    readonly links = signal<Record<string, number>>({});

    /** Total number of items for pagination */
    readonly totalItems = signal(0);

    /** Current page number (1-indexed) */
    readonly page = signal(1);

    /** Items per page for pagination */
    readonly itemsPerPage = ITEMS_PER_PAGE;

    /** Sort predicate (field name) */
    readonly predicate = signal('notificationDate');

    /** Previous page number for change detection */
    private previousPage = 1;

    /** Sort order (true = ascending) */
    readonly reverse = signal(false);

    /** Subject for dialog error messages */
    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    /** Icons for the template */
    protected readonly faSort = faSort;
    protected readonly faPlus = faPlus;
    protected readonly faTimes = faTimes;
    protected readonly faEye = faEye;
    protected readonly faWrench = faWrench;

    constructor() {
        // Subscribe to route data for paging parameters
        this.routeDataSubscription = this.activatedRoute.data.subscribe((data) => {
            const pagingParams = data['pagingParams'];
            if (pagingParams) {
                this.page.set(pagingParams.page);
                this.previousPage = pagingParams.page;
                this.reverse.set(pagingParams.ascending);
                this.predicate.set(pagingParams.predicate);
            }
        });
    }

    /**
     * Initializes current account and loads system notifications.
     */
    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.currentAccount.set(user);
            this.loadAll();
            this.registerChangeInNotifications();
        });
    }

    /**
     * Subscribes to notification list modifications to reload data.
     * Subscription is cleaned up in ngOnDestroy.
     */
    private registerChangeInNotifications(): void {
        this.eventManagerSubscription = this.eventManager.subscribe('notificationListModification', () => this.loadAll());
    }

    /**
     * Cleans up subscriptions when the component is destroyed.
     */
    ngOnDestroy(): void {
        this.routeDataSubscription?.unsubscribe();
        this.eventManagerSubscription?.unsubscribe();
    }

    /**
     * Deletes a notification by ID.
     * @param notificationId - The ID of the notification to delete
     */
    deleteNotification(notificationId: number): void {
        this.adminSystemNotificationService.delete(notificationId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'notificationListModification',
                    content: 'Deleted a system notification',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Loads all system notifications for the current page.
     */
    loadAll(): void {
        this.systemNotificationService
            .query({
                page: this.page() - 1,
                size: this.itemsPerPage,
                sort: this.sort(),
            })
            .subscribe({
                next: (res: HttpResponse<SystemNotification[]>) => this.onSuccess(res.body!, res.headers),
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    /**
     * Track function for ngFor to optimize rendering.
     * @param index - Index in the collection
     * @param item - The notification item
     * @returns The notification ID or -1 if not available
     */
    trackIdentity(index: number, item: SystemNotification): number {
        return item.id ?? -1;
    }

    /**
     * Determines the current state of a notification (scheduled, active, or expired).
     * @param systemNotification - The notification to check
     * @returns The notification state
     */
    getNotificationState(systemNotification: SystemNotification): NotificationState {
        const now = dayjs();
        if (systemNotification.notificationDate!.isAfter(now)) {
            return NotificationState.SCHEDULED;
        } else if (systemNotification.expireDate?.isAfter(now) ?? true) {
            return NotificationState.ACTIVE;
        } else {
            return NotificationState.EXPIRED;
        }
    }

    /**
     * Creates sort parameters for the query.
     * @returns Array of sort strings
     */
    sort(): string[] {
        const result: string[] = [];
        const pred = this.predicate();
        if (pred) {
            result.push(`${pred},${this.reverse() ? 'asc' : 'desc'}`);
        }
        if (pred !== 'id') {
            result.push('id');
        }
        return result;
    }

    /**
     * Loads a specific page if different from the current page.
     * @param page - The page number to load
     */
    loadPage(page: number): void {
        if (page !== this.previousPage) {
            this.previousPage = page;
            this.page.set(page);
            this.transition();
        }
    }

    /**
     * Navigates to the updated route with current pagination and sorting.
     */
    transition(): void {
        this.router.navigate(['/admin/system-notification-management'], {
            queryParams: {
                page: this.page(),
                sort: `${this.predicate()},${this.reverse() ? 'asc' : 'desc'}`,
            },
        });
        this.loadAll();
    }

    /**
     * Handles successful notification load response.
     * @param data - The notification data
     * @param headers - The response headers containing pagination info
     */
    private onSuccess(data: SystemNotification[], headers: HttpHeaders): void {
        const linkHeader = headers.get('link');
        if (linkHeader) {
            this.links.set(this.parseLinks.parse(linkHeader));
        }
        this.totalItems.set(Number(headers.get('X-Total-Count') ?? 0));
        this.notifications.set(data);
    }
}
