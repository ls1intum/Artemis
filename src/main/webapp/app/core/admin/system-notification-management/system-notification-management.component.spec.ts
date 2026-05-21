/**
 * Vitest tests for SystemNotificationManagementComponent.
 * Tests the main list view for managing system notifications with
 * pagination and navigation to detail/edit views.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import '@angular/localize/init';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';

import { SystemNotificationManagementComponent } from 'app/core/admin/system-notification-management/system-notification-management.component';
import { SystemNotification } from 'app/core/shared/entities/system-notification.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { AlertService } from 'app/shared/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';
import { AdminSystemNotificationService } from 'app/core/notification/system-notification/admin-system-notification.service';

describe('SystemNotificationManagementComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<SystemNotificationManagementComponent>;
    let component: SystemNotificationManagementComponent;
    let router: MockRouter;
    let mockSystemNotificationService: any;
    let mockAdminSystemNotificationService: any;
    let eventManager: EventManager;
    let alertService: AlertService;
    let routeDataSubject: BehaviorSubject<any>;

    beforeEach(async () => {
        router = new MockRouter();
        router.setUrl('');
        routeDataSubject = new BehaviorSubject({ pagingParams: { page: 1, ascending: true, predicate: 'id' } });

        const mockRoute = {
            data: routeDataSubject.asObservable(),
            children: [],
        } as unknown as ActivatedRoute;

        mockSystemNotificationService = {
            query: vi.fn().mockReturnValue(of(new HttpResponse({ body: [], headers: new HttpHeaders() }))),
        };

        mockAdminSystemNotificationService = {
            delete: vi.fn().mockReturnValue(of(void 0)),
        };

        await TestBed.configureTestingModule({
            imports: [SystemNotificationManagementComponent],
            providers: [
                { provide: ActivatedRoute, useValue: mockRoute },
                { provide: Router, useValue: router },
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AlertService, useValue: { error: vi.fn() } },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SystemNotificationService, useValue: mockSystemNotificationService },
                { provide: AdminSystemNotificationService, useValue: mockAdminSystemNotificationService },
                EventManager,
            ],
        })
            .overrideTemplate(SystemNotificationManagementComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(SystemNotificationManagementComponent);
        component = fixture.componentInstance;
        eventManager = TestBed.inject(EventManager);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should have notifications signal initialized as empty array', () => {
        expect(component.notifications()).toEqual([]);
    });

    it('should initialize with paging params from route data', () => {
        expect(component.page()).toBe(1);
        expect(component.reverse()).toBe(true);
        expect(component.predicate()).toBe('id');
    });

    it('should update paging params when route data changes', () => {
        routeDataSubject.next({ pagingParams: { page: 3, ascending: false, predicate: 'title' } });

        expect(component.page()).toBe(3);
        expect(component.reverse()).toBe(false);
        expect(component.predicate()).toBe('title');
    });

    it('should handle route data without paging params', () => {
        routeDataSubject.next({});
        // Should not throw and keep existing values
        expect(component.page()).toBe(1);
    });

    it('should load notifications on init', async () => {
        const notifications = [
            { id: 1, title: 'Test 1', notificationDate: dayjs() } as SystemNotification,
            { id: 2, title: 'Test 2', notificationDate: dayjs() } as SystemNotification,
        ];
        const headers = new HttpHeaders().set('X-Total-Count', '2').set('link', '<url>; rel="next"');
        mockSystemNotificationService.query.mockReturnValue(of(new HttpResponse({ body: notifications, headers })));

        component.ngOnInit();
        await fixture.whenStable();

        expect(mockSystemNotificationService.query).toHaveBeenCalled();
        expect(component.notifications()).toEqual(notifications);
        expect(component.totalItems()).toBe(2);
    });

    it('should handle error when loading notifications', async () => {
        // Use status 400 because onError doesn't call alertService for 500 errors
        const errorResponse = new HttpErrorResponse({ status: 400, statusText: 'Bad Request' });
        mockSystemNotificationService.query.mockReturnValue(throwError(() => errorResponse));

        component.ngOnInit();
        await fixture.whenStable();

        expect(alertService.error).toHaveBeenCalledWith('error.http.400');
    });

    it('should clean up subscriptions on destroy', () => {
        component.ngOnInit();
        expect(() => component.ngOnDestroy()).not.toThrow();
        expect(() => fixture.destroy()).not.toThrow();
    });

    describe('transition', () => {
        it('should navigate to new page with current sorting', () => {
            component.page.set(2);
            component.predicate.set('title');
            component.reverse.set(true);

            component.transition();

            expect(router.navigate).toHaveBeenCalledWith(['/admin/system-notification-management'], {
                queryParams: {
                    page: 2,
                    sort: 'title,asc',
                },
            });
        });

        it('should call loadAll after navigating', () => {
            component.transition();

            expect(mockSystemNotificationService.query).toHaveBeenCalled();
        });
    });

    describe('loadPage', () => {
        it('should load page when page changes', () => {
            component.loadPage(2);

            expect(component.page()).toBe(2);
            expect(router.navigate).toHaveBeenCalled();
        });

        it('should not load page when page is the same', () => {
            component.loadPage(1);
            router.navigate.mockClear();

            component.loadPage(1);

            expect(router.navigate).not.toHaveBeenCalled();
        });
    });

    describe('getNotificationState', () => {
        it('should return SCHEDULED for future notifications', () => {
            const notification = new SystemNotification();
            notification.notificationDate = dayjs().add(1, 'day');
            notification.expireDate = dayjs().add(2, 'day');

            expect(component.getNotificationState(notification)).toBe('SCHEDULED');
        });

        it('should return ACTIVE for current notifications', () => {
            const notification = new SystemNotification();
            notification.notificationDate = dayjs().subtract(1, 'day');
            notification.expireDate = dayjs().add(1, 'day');

            expect(component.getNotificationState(notification)).toBe('ACTIVE');
        });

        it('should return ACTIVE when expireDate is undefined', () => {
            const notification = new SystemNotification();
            notification.notificationDate = dayjs().subtract(1, 'day');
            notification.expireDate = undefined;

            expect(component.getNotificationState(notification)).toBe('ACTIVE');
        });

        it('should return EXPIRED for past notifications', () => {
            const notification = new SystemNotification();
            notification.notificationDate = dayjs().subtract(2, 'day');
            notification.expireDate = dayjs().subtract(1, 'day');

            expect(component.getNotificationState(notification)).toBe('EXPIRED');
        });
    });

    describe('deleteNotification', () => {
        it('should delete notification and broadcast event on success', () => {
            const broadcastSpy = vi.spyOn(eventManager, 'broadcast');

            component.deleteNotification(1);

            expect(mockAdminSystemNotificationService.delete).toHaveBeenCalledWith(1);
            expect(broadcastSpy).toHaveBeenCalledWith({
                name: 'notificationListModification',
                content: 'Deleted a system notification',
            });
        });

        it('should emit error to dialogError$ on failure', () => {
            const errorResponse = new HttpErrorResponse({ status: 500, statusText: 'Server Error', error: { message: 'Delete failed' } });
            mockAdminSystemNotificationService.delete.mockReturnValue(throwError(() => errorResponse));

            let emittedError: string | undefined;
            component.dialogError$.subscribe((error) => (emittedError = error));

            component.deleteNotification(1);

            expect(emittedError).toBeDefined();
        });
    });

    describe('sort', () => {
        it('should return sort array with predicate and direction', () => {
            component.predicate.set('title');
            component.reverse.set(false);

            const result = component.sort();

            expect(result).toEqual(['title,desc', 'id']);
        });

        it('should return ascending order when reverse is true', () => {
            component.predicate.set('notificationDate');
            component.reverse.set(true);

            const result = component.sort();

            expect(result).toEqual(['notificationDate,asc', 'id']);
        });

        it('should not add id twice when predicate is id', () => {
            component.predicate.set('id');
            component.reverse.set(true);

            const result = component.sort();

            expect(result).toEqual(['id,asc']);
        });
    });

    describe('trackIdentity', () => {
        it('should return notification id', () => {
            const notification = { id: 5 } as SystemNotification;

            expect(component.trackIdentity(0, notification)).toBe(5);
        });

        it('should return -1 when id is undefined', () => {
            const notification = {} as SystemNotification;

            expect(component.trackIdentity(0, notification)).toBe(-1);
        });
    });

    describe('registerChangeInNotifications', () => {
        it('should reload notifications when event is broadcast', async () => {
            // Wait for ngOnInit to complete and subscriptions to be set up
            component.ngOnInit();
            await fixture.whenStable();
            mockSystemNotificationService.query.mockClear();

            // Broadcast the event
            eventManager.broadcast({ name: 'notificationListModification', content: 'test' });
            await fixture.whenStable();

            expect(mockSystemNotificationService.query).toHaveBeenCalled();
        });
    });

    describe('onSuccess', () => {
        it('should parse pagination headers correctly', async () => {
            const notifications = [{ id: 1, notificationDate: dayjs() } as SystemNotification];
            const headers = new HttpHeaders().set('X-Total-Count', '50').set('link', '<http://example.com?page=2>; rel="next"');
            mockSystemNotificationService.query.mockReturnValue(of(new HttpResponse({ body: notifications, headers })));

            component.loadAll();
            await fixture.whenStable();

            expect(component.totalItems()).toBe(50);
            expect(component.notifications()).toEqual(notifications);
        });

        it('should handle missing X-Total-Count header', async () => {
            const notifications: SystemNotification[] = [];
            const headers = new HttpHeaders();
            mockSystemNotificationService.query.mockReturnValue(of(new HttpResponse({ body: notifications, headers })));

            component.loadAll();
            await fixture.whenStable();

            expect(component.totalItems()).toBe(0);
        });
    });
});
