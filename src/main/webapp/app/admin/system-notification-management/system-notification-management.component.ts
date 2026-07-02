import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { onError } from 'app/foundation/util/global.utils';
import { SystemNotification } from 'app/admin/system-notification-management/system-notification.model';
import { ITEMS_PER_PAGE } from 'app/foundation/constants/pagination.constants';
import { AlertService } from 'app/foundation/service/alert.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { faEye, faPencil, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ItemCountComponent } from 'app/foundation/pagination/item-count.component';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { TableModule } from 'primeng/table';
import { SortEvent } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';
import { AdminSystemNotificationService } from 'app/core/notification/system-notification/admin-system-notification.service';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';

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
    imports: [
        TranslateDirective,
        RouterLink,
        FaIconComponent,
        DeleteButtonDirective,
        ItemCountComponent,
        PaginatorModule,
        TableModule,
        ButtonModule,
        TagModule,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemNotificationManagementComponent implements OnInit, OnDestroy {
    private readonly systemNotificationService = inject(SystemNotificationService);
    private readonly adminSystemNotificationService = inject(AdminSystemNotificationService);
    private readonly alertService = inject(AlertService);
    private readonly accountService = inject(AccountService);
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

    /** List of system notifications */
    readonly notifications = signal<SystemNotification[]>([]);

    /** Total number of items for pagination */
    readonly totalItems = signal(0);

    /** Current page number (1-indexed) */
    readonly page = signal(1);

    /** Items per page for pagination */
    readonly itemsPerPage = ITEMS_PER_PAGE;

    /** Sort predicate (field name) */
    readonly predicate = signal('notificationDate');

    /** Previous page number for change detection */
    private readonly previousPage = signal(1);

    /** Sort order (true = ascending) */
    readonly reverse = signal(false);

    /** Subject for dialog error messages */
    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    /** Icons for the template */
    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    protected readonly faEye = faEye;
    protected readonly faPencil = faPencil;

    constructor() {
        // Subscribe to route data for paging parameters
        this.routeDataSubscription = this.activatedRoute.data.subscribe((data) => {
            const pagingParams = data['pagingParams'];
            if (pagingParams) {
                this.page.set(pagingParams.page);
                this.previousPage.set(pagingParams.page);
                this.reverse.set(pagingParams.ascending);
                this.predicate.set(pagingParams.predicate);
            }
        });
    }

    /**
     * Initializes current account and loads system notifications.
     */
    ngOnInit(): void {
        this.accountService.identity().then(() => {
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
        if (page !== this.previousPage()) {
            this.previousPage.set(page);
            this.page.set(page);
            this.transition();
        }
    }

    /** Handles a PrimeNG paginator page change by converting the 0-indexed event page to the 1-indexed page and loading it. */
    onPageChange(event: PaginatorState): void {
        this.loadPage((event.page ?? 0) + 1);
    }

    /**
     * Handles a PrimeNG table sort event by mapping the sort field/order onto the predicate/reverse state and navigating.
     * Server-side sorting is triggered via the resulting route transition.
     */
    onTableSort(event: SortEvent): void {
        if (!event.field) {
            return;
        }
        this.predicate.set(event.field);
        this.reverse.set((event.order ?? 1) === 1);
        this.transition();
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
        this.totalItems.set(Number(headers.get('X-Total-Count') ?? 0));
        this.notifications.set(data);
    }
}
