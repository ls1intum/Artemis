import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { Subject, of } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { SystemNotification, SystemNotificationType } from 'app/core/shared/entities/system-notification.model';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { Renderer2, RendererStyleFlags2 } from '@angular/core';
import { CLOSED_NOTIFICATION_IDS_STORAGE_KEY, SystemNotificationComponent, WEBSOCKET_CHANNEL } from 'app/core/notification/system-notification/system-notification.component';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';
import * as navbarUtil from 'app/shared/util/navbar.util';

describe('System Notification Component', () => {
    setupTestBed({ zoneless: true });

    let systemNotificationComponent: SystemNotificationComponent;
    let systemNotificationComponentFixture: ComponentFixture<SystemNotificationComponent>;
    let systemNotificationService: SystemNotificationService;
    let websocketService: WebsocketService;
    let localStorageService: LocalStorageService;

    const createActiveNotification = (type: SystemNotificationType, id: number) => {
        return {
            id,
            title: 'Maintenance',
            text: 'Artemis will be unavailable',
            type,
            notificationDate: dayjs().subtract(1, 'hour'),
            expireDate: dayjs().add(1, 'hour'),
        } as SystemNotification;
    };

    const createInactiveNotification = (type: SystemNotificationType, id: number) => {
        return {
            id,
            title: 'Maintenance',
            text: 'Artemis will be unavailable',
            type,
            notificationDate: dayjs().add(1, 'hour'),
            expireDate: dayjs().add(2, 'hour'),
        } as SystemNotification;
    };

    beforeEach(async () => {
        // Clear localStorage to prevent leaking state between tests
        localStorage.removeItem(CLOSED_NOTIFICATION_IDS_STORAGE_KEY);

        TestBed.configureTestingModule({
            imports: [SystemNotificationComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
            ],
        });
        await TestBed.compileComponents();
        systemNotificationComponentFixture = TestBed.createComponent(SystemNotificationComponent);
        systemNotificationComponent = systemNotificationComponentFixture.componentInstance;
        systemNotificationService = TestBed.inject(SystemNotificationService);
        websocketService = TestBed.inject(WebsocketService);
        localStorageService = TestBed.inject(LocalStorageService);
    });

    afterEach(() => {
        localStorage.removeItem(CLOSED_NOTIFICATION_IDS_STORAGE_KEY);
        vi.restoreAllMocks();
    });

    it('should get system notifications, display active ones on init, and schedule changes correctly', () => {
        vi.useFakeTimers();
        const notifications = [
            createActiveNotification(SystemNotificationType.WARNING, 1),
            createActiveNotification(SystemNotificationType.INFO, 2),
            createInactiveNotification(SystemNotificationType.WARNING, 3),
            createInactiveNotification(SystemNotificationType.INFO, 4),
        ];
        const getActiveNotificationSpy = vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));
        systemNotificationComponent.ngOnInit();
        vi.advanceTimersByTime(500);
        expect(getActiveNotificationSpy).toHaveBeenCalledOnce();
        expect(systemNotificationComponent.notifications).toEqual(notifications);
        expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[0], notifications[1]]);

        vi.advanceTimersByTime(60 * 60 * 1000); // one hour

        expect(systemNotificationComponent.notifications).toEqual(notifications);
        expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[2], notifications[3]]);

        vi.useRealTimers();
    });

    it('should connect to ws on init and update notifications to new array of notifications received through it', () => {
        vi.useFakeTimers();
        const originalNotifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createInactiveNotification(SystemNotificationType.INFO, 2)];
        const newNotifications = [createActiveNotification(SystemNotificationType.WARNING, 3), createInactiveNotification(SystemNotificationType.INFO, 4)];

        const subscribeSpy = vi.spyOn(websocketService, 'subscribe').mockReturnValue(of(newNotifications));
        const getActiveNotificationSpy = vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(originalNotifications));

        systemNotificationComponent.ngOnInit();
        expect(systemNotificationComponent.notifications).toEqual(originalNotifications);
        expect(systemNotificationComponent.notificationsToDisplay).toEqual([originalNotifications[0]]);

        vi.advanceTimersByTime(500);

        expect(systemNotificationComponent.notifications).toEqual(newNotifications);
        expect(systemNotificationComponent.notificationsToDisplay).toEqual([newNotifications[0]]);
        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(subscribeSpy).toHaveBeenCalledWith(WEBSOCKET_CHANNEL);
        expect(getActiveNotificationSpy).toHaveBeenCalledOnce();
        vi.useRealTimers();
    });

    describe('Persistence of dismissed notification IDs', () => {
        it('should save closed notification IDs to localStorage', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));
            const storeSpy = vi.spyOn(localStorageService, 'store');

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            systemNotificationComponent.close(notifications[0]);

            expect(storeSpy).toHaveBeenCalledWith(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1]);
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[1]]);
            vi.useRealTimers();
        });

        it('should load closed notification IDs from localStorage on init', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1]);
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            expect(systemNotificationComponent.closedIds).toEqual([1]);
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[1]]);
            vi.useRealTimers();
        });

        it('should not reset closed IDs on websocket update', () => {
            vi.useFakeTimers();
            const originalNotifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            const updatedNotifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];

            localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1]);
            vi.spyOn(websocketService, 'subscribe').mockReturnValue(of(updatedNotifications));
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(originalNotifications));

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            // After websocket update, notification 1 should still be closed
            expect(systemNotificationComponent.closedIds).toEqual([1]);
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([updatedNotifications[1]]);
            vi.useRealTimers();
        });

        it('should prune stale closed IDs when notifications are updated via websocket', () => {
            vi.useFakeTimers();
            const originalNotifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            // New notifications no longer include notification 1
            const updatedNotifications = [createActiveNotification(SystemNotificationType.INFO, 2), createActiveNotification(SystemNotificationType.WARNING, 3)];

            localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1]);
            vi.spyOn(websocketService, 'subscribe').mockReturnValue(of(updatedNotifications));
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(originalNotifications));

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            // Stale ID 1 should be pruned since it's no longer in the notification list
            expect(systemNotificationComponent.closedIds).toEqual([]);
            expect(localStorageService.retrieve(CLOSED_NOTIFICATION_IDS_STORAGE_KEY)).toEqual([]);
            vi.useRealTimers();
        });

        it('should default to empty array when localStorage has no stored IDs', () => {
            vi.useFakeTimers();
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of([]));

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            expect(systemNotificationComponent.closedIds).toEqual([]);
            vi.useRealTimers();
        });

        it('should accumulate multiple closed IDs in localStorage', () => {
            vi.useFakeTimers();
            const notifications = [
                createActiveNotification(SystemNotificationType.WARNING, 1),
                createActiveNotification(SystemNotificationType.INFO, 2),
                createActiveNotification(SystemNotificationType.WARNING, 3),
            ];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            systemNotificationComponent.close(notifications[0]);
            expect(localStorageService.retrieve(CLOSED_NOTIFICATION_IDS_STORAGE_KEY)).toEqual([1]);
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[1], notifications[2]]);

            systemNotificationComponent.close(notifications[2]);
            expect(localStorageService.retrieve(CLOSED_NOTIFICATION_IDS_STORAGE_KEY)).toEqual([1, 3]);
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[1]]);

            vi.useRealTimers();
        });

        it('should keep valid closed IDs and prune only stale ones on websocket update', () => {
            vi.useFakeTimers();
            const originalNotifications = [
                createActiveNotification(SystemNotificationType.WARNING, 1),
                createActiveNotification(SystemNotificationType.INFO, 2),
                createActiveNotification(SystemNotificationType.WARNING, 3),
            ];
            // Notification 1 is removed, 2 and 3 remain
            const updatedNotifications = [createActiveNotification(SystemNotificationType.INFO, 2), createActiveNotification(SystemNotificationType.WARNING, 3)];

            // User had closed notifications 1 and 2
            localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1, 2]);
            vi.spyOn(websocketService, 'subscribe').mockReturnValue(of(updatedNotifications));
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(originalNotifications));

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            // ID 1 pruned (not in updated list), ID 2 kept (still in updated list)
            expect(systemNotificationComponent.closedIds).toEqual([2]);
            expect(localStorageService.retrieve(CLOSED_NOTIFICATION_IDS_STORAGE_KEY)).toEqual([2]);
            // Only notification 3 should be displayed (2 is closed, 1 was removed)
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([updatedNotifications[1]]);
            vi.useRealTimers();
        });

        it('should persist dismissal across multiple websocket updates', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            const wsSubject = new Subject<SystemNotification[]>();

            vi.spyOn(websocketService, 'subscribe').mockReturnValue(wsSubject);
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            // Close notification 1
            systemNotificationComponent.close(notifications[0]);
            expect(localStorageService.retrieve(CLOSED_NOTIFICATION_IDS_STORAGE_KEY)).toEqual([1]);

            // Websocket sends same notifications again — closed ID should persist
            wsSubject.next([createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)]);

            expect(systemNotificationComponent.closedIds).toEqual([1]);
            expect(systemNotificationComponent.notificationsToDisplay).toHaveLength(1);
            expect(systemNotificationComponent.notificationsToDisplay[0].id).toBe(2);

            vi.useRealTimers();
        });
    });

    describe('CSS variable for notification height', () => {
        it('should set --system-notification-height CSS variable on the document root after view check', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            const renderer = systemNotificationComponentFixture.debugElement.injector.get(Renderer2);
            const setStyleSpy = vi.spyOn(renderer, 'setStyle');

            // Simulate a previous height that differs from the actual element height (0 in jsdom)
            // so that ngAfterViewChecked detects a change and calls setStyle
            (systemNotificationComponent as any).lastNotificationHeight = 40;

            // Use detectChanges to trigger both ngOnInit and ngAfterViewChecked
            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);

            // In jsdom, offsetHeight is 0, so the variable will be set to '0px'
            const setStyleCalls = setStyleSpy.mock.calls.filter((call) => call[1] === '--system-notification-height');
            expect(setStyleCalls.length).toBeGreaterThanOrEqual(1);
            expect(setStyleCalls[0]).toEqual([document.documentElement, '--system-notification-height', '0px', RendererStyleFlags2.DashCase]);

            vi.useRealTimers();
        });

        it('should reset --system-notification-height to 0px on destroy', () => {
            vi.useFakeTimers();
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of([]));

            const renderer = systemNotificationComponentFixture.debugElement.injector.get(Renderer2);
            const setStyleSpy = vi.spyOn(renderer, 'setStyle');

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            systemNotificationComponent.ngOnDestroy();

            expect(setStyleSpy).toHaveBeenCalledWith(document.documentElement, '--system-notification-height', '0px', RendererStyleFlags2.DashCase);
            vi.useRealTimers();
        });

        it('should only update CSS variable when height actually changes', () => {
            vi.useFakeTimers();
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of([]));

            const renderer = systemNotificationComponentFixture.debugElement.injector.get(Renderer2);
            const setStyleSpy = vi.spyOn(renderer, 'setStyle');

            // First detectChanges triggers ngOnInit + ngAfterViewChecked
            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);

            const callCountAfterFirst = setStyleSpy.mock.calls.filter((call) => call[1] === '--system-notification-height').length;

            // Trigger another change detection cycle — height hasn't changed, so no additional setStyle call
            systemNotificationComponentFixture.detectChanges();

            const callCountAfterSecond = setStyleSpy.mock.calls.filter((call) => call[1] === '--system-notification-height').length;
            expect(callCountAfterSecond).toBe(callCountAfterFirst);

            vi.useRealTimers();
        });

        it('should update CSS variable when lastNotificationHeight changes', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            const renderer = systemNotificationComponentFixture.debugElement.injector.get(Renderer2);
            const setStyleSpy = vi.spyOn(renderer, 'setStyle');

            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);

            // Simulate a previous height being different to force the update check to detect a change
            (systemNotificationComponent as any).lastNotificationHeight = 50;

            // Close a notification and trigger change detection + lifecycle hook
            systemNotificationComponent.close(notifications[0]);
            systemNotificationComponentFixture.detectChanges();
            systemNotificationComponent.ngAfterViewChecked();

            // The height changed (from simulated 50 to actual 0 in jsdom), so setStyle should be called
            const lastCall = setStyleSpy.mock.calls.filter((call) => call[1] === '--system-notification-height').pop();
            expect(lastCall).toEqual([document.documentElement, '--system-notification-height', '0px', RendererStyleFlags2.DashCase]);

            vi.useRealTimers();
        });

        it('should call updateHeaderHeight when notification height changes', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            const updateHeaderHeightSpy = vi.spyOn(navbarUtil, 'updateHeaderHeight').mockImplementation(() => {});

            // Simulate a previous height that differs from the actual element height (0 in jsdom)
            (systemNotificationComponent as any).lastNotificationHeight = 40;

            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);

            // updateHeaderHeight should have been called because height changed (40 -> 0)
            expect(updateHeaderHeightSpy).toHaveBeenCalled();

            vi.useRealTimers();
        });

        it('should not call updateHeaderHeight when notification height has not changed', () => {
            vi.useFakeTimers();
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of([]));

            const updateHeaderHeightSpy = vi.spyOn(navbarUtil, 'updateHeaderHeight').mockImplementation(() => {});

            // First detectChanges triggers ngOnInit + ngAfterViewChecked
            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);
            updateHeaderHeightSpy.mockClear();

            // Trigger another change detection cycle — height hasn't changed, so updateHeaderHeight should NOT be called
            systemNotificationComponentFixture.detectChanges();
            systemNotificationComponent.ngAfterViewChecked();

            expect(updateHeaderHeightSpy).not.toHaveBeenCalled();

            vi.useRealTimers();
        });

        it('should set --system-notification-height on document.documentElement so CSS var() references resolve', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));
            vi.spyOn(navbarUtil, 'updateHeaderHeight').mockImplementation(() => {});

            // Simulate non-zero height to verify the variable is set with the actual value
            (systemNotificationComponent as any).lastNotificationHeight = 40;

            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);

            // The CSS variable must be set on document.documentElement (the :root element)
            // because the dynamic-content-height mixin and admin-container use var(--system-notification-height, 0px)
            const value = document.documentElement.style.getPropertyValue('--system-notification-height');
            expect(value).toBe('0px');

            vi.useRealTimers();
        });

        it('should reset --system-notification-height to 0px on destroy so layouts revert to no-notification sizing', () => {
            vi.useFakeTimers();
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of([]));
            vi.spyOn(navbarUtil, 'updateHeaderHeight').mockImplementation(() => {});

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            // Set a non-zero height to simulate an active notification
            document.documentElement.style.setProperty('--system-notification-height', '40px');
            expect(document.documentElement.style.getPropertyValue('--system-notification-height')).toBe('40px');

            systemNotificationComponent.ngOnDestroy();

            // After destroy, the variable must be reset to 0px so that:
            // - course-base-container: calc(... - var(--system-notification-height, 0px)) reverts to full height
            // - admin-container: calc(... - var(--system-notification-height, 0px)) reverts to full height
            expect(document.documentElement.style.getPropertyValue('--system-notification-height')).toBe('0px');

            vi.useRealTimers();
        });
    });

    describe('close() edge cases', () => {
        it('should not modify closedIds when notification has no ID', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));
            const storeSpy = vi.spyOn(localStorageService, 'store');

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            // Create a notification without an ID
            const notificationWithoutId = { ...notifications[0], id: undefined } as SystemNotification;
            systemNotificationComponent.close(notificationWithoutId);

            // closedIds should remain unchanged, localStorage should NOT be written
            expect(systemNotificationComponent.closedIds).toEqual([]);
            expect(storeSpy).not.toHaveBeenCalled();
            // Original notification should still be displayed
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[0]]);
            vi.useRealTimers();
        });

        it('should not add duplicate IDs when close is called twice for the same notification', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            systemNotificationComponent.close(notifications[0]);
            expect(systemNotificationComponent.closedIds).toEqual([1]);

            // Close the same notification again
            systemNotificationComponent.close(notifications[0]);
            expect(systemNotificationComponent.closedIds).toEqual([1]);
            expect(localStorageService.retrieve(CLOSED_NOTIFICATION_IDS_STORAGE_KEY)).toEqual([1]);
            vi.useRealTimers();
        });
    });

    describe('pruneClosedIds optimization', () => {
        it('should not write to localStorage when no IDs were pruned', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            const wsSubject = new Subject<SystemNotification[]>();

            // Pre-store a closed ID that will remain valid after websocket update
            localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1]);

            vi.spyOn(websocketService, 'subscribe').mockReturnValue(wsSubject);
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            // Spy on store AFTER init, then clear any calls from init
            const storeSpy = vi.spyOn(localStorageService, 'store');
            storeSpy.mockClear();

            // Websocket sends notifications that still include ID 1 — nothing should be pruned
            wsSubject.next([createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)]);

            // closedIds should still contain 1, and store should NOT have been called by pruneClosedIds
            expect(systemNotificationComponent.closedIds).toEqual([1]);
            expect(storeSpy).not.toHaveBeenCalled();
            vi.useRealTimers();
        });

        it('should write to localStorage when IDs are actually pruned', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            const wsSubject = new Subject<SystemNotification[]>();

            // Pre-store closed IDs including one that will become stale
            localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1, 2]);

            vi.spyOn(websocketService, 'subscribe').mockReturnValue(wsSubject);
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            const storeSpy = vi.spyOn(localStorageService, 'store');
            storeSpy.mockClear();

            // Websocket sends notifications without ID 1 — ID 1 should be pruned
            wsSubject.next([createActiveNotification(SystemNotificationType.INFO, 2), createActiveNotification(SystemNotificationType.WARNING, 3)]);

            expect(systemNotificationComponent.closedIds).toEqual([2]);
            expect(storeSpy).toHaveBeenCalledWith(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [2]);
            vi.useRealTimers();
        });
    });

    describe('ngOnDestroy cleanup', () => {
        it('should clear scheduled timeout on destroy', () => {
            vi.useFakeTimers();
            // Use notifications where one will become active in the future, causing a scheduled timeout
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createInactiveNotification(SystemNotificationType.INFO, 2)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            // A timeout should be scheduled for when notification 2 becomes active
            expect(systemNotificationComponent.nextUpdateFuture).toBeDefined();

            const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout');
            systemNotificationComponent.ngOnDestroy();

            expect(clearTimeoutSpy).toHaveBeenCalled();
            vi.useRealTimers();
        });

        it('should unsubscribe all subscriptions on destroy', () => {
            vi.useFakeTimers();
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of([]));
            vi.spyOn(websocketService, 'subscribe').mockReturnValue(of([]));

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            // Verify subscriptions exist
            const wsStatusSub = systemNotificationComponent.websocketStatusSubscription;
            const sysNotifSub = systemNotificationComponent.systemNotificationSubscription;

            const wsStatusUnsubSpy = wsStatusSub ? vi.spyOn(wsStatusSub, 'unsubscribe') : undefined;
            const sysNotifUnsubSpy = sysNotifSub ? vi.spyOn(sysNotifSub, 'unsubscribe') : undefined;

            systemNotificationComponent.ngOnDestroy();

            if (wsStatusUnsubSpy) {
                expect(wsStatusUnsubSpy).toHaveBeenCalledOnce();
            }
            if (sysNotifUnsubSpy) {
                expect(sysNotifUnsubSpy).toHaveBeenCalledOnce();
            }
            vi.useRealTimers();
        });

        it('should reset CSS variable and handle no scheduled timeout gracefully', () => {
            vi.useFakeTimers();
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of([]));

            const renderer = systemNotificationComponentFixture.debugElement.injector.get(Renderer2);
            const setStyleSpy = vi.spyOn(renderer, 'setStyle');

            systemNotificationComponent.ngOnInit();
            vi.advanceTimersByTime(500);

            // With no notifications, there should be no scheduled timeout
            expect(systemNotificationComponent.nextUpdateFuture).toBeUndefined();

            // Destroy should not throw even without a scheduled timeout
            systemNotificationComponent.ngOnDestroy();

            expect(setStyleSpy).toHaveBeenCalledWith(document.documentElement, '--system-notification-height', '0px', RendererStyleFlags2.DashCase);
            vi.useRealTimers();
        });
    });

    describe('DOM rendering', () => {
        it('should render notification elements for active notifications', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);
            systemNotificationComponentFixture.detectChanges();

            const notificationElements = systemNotificationComponentFixture.nativeElement.querySelectorAll('.system-notification');
            expect(notificationElements).toHaveLength(2);

            vi.useRealTimers();
        });

        it('should not render closed notifications in the DOM', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1]);
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);
            systemNotificationComponentFixture.detectChanges();

            const notificationElements = systemNotificationComponentFixture.nativeElement.querySelectorAll('.system-notification');
            expect(notificationElements).toHaveLength(1);

            vi.useRealTimers();
        });

        it('should remove notification from DOM when close button is clicked', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);
            systemNotificationComponentFixture.detectChanges();

            expect(systemNotificationComponentFixture.nativeElement.querySelectorAll('.system-notification')).toHaveLength(2);

            // Click the close button on the first notification
            const closeButton = systemNotificationComponentFixture.nativeElement.querySelector('.notification-close');
            closeButton.click();
            systemNotificationComponentFixture.detectChanges();

            expect(systemNotificationComponentFixture.nativeElement.querySelectorAll('.system-notification')).toHaveLength(1);

            vi.useRealTimers();
        });

        it('should apply correct CSS class for warning notifications', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);
            systemNotificationComponentFixture.detectChanges();

            const notificationElement = systemNotificationComponentFixture.nativeElement.querySelector('.system-notification');
            expect(notificationElement).toBeTruthy();
            expect(notificationElement.classList.contains('warning')).toBe(true);
            expect(notificationElement.classList.contains('info')).toBe(false);

            vi.useRealTimers();
        });

        it('should apply correct CSS class for info notifications', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.INFO, 1)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);
            systemNotificationComponentFixture.detectChanges();

            const notificationElement = systemNotificationComponentFixture.nativeElement.querySelector('.system-notification');
            expect(notificationElement).toBeTruthy();
            expect(notificationElement.classList.contains('info')).toBe(true);
            expect(notificationElement.classList.contains('warning')).toBe(false);

            vi.useRealTimers();
        });

        it('should render no notification elements when all are dismissed', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);
            systemNotificationComponentFixture.detectChanges();

            expect(systemNotificationComponentFixture.nativeElement.querySelectorAll('.system-notification')).toHaveLength(1);

            systemNotificationComponent.close(notifications[0]);
            systemNotificationComponentFixture.detectChanges();

            expect(systemNotificationComponentFixture.nativeElement.querySelectorAll('.system-notification')).toHaveLength(0);

            vi.useRealTimers();
        });

        it('should display notification title and text', () => {
            vi.useFakeTimers();
            const notifications = [createActiveNotification(SystemNotificationType.INFO, 1)];
            vi.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            vi.advanceTimersByTime(500);
            systemNotificationComponentFixture.detectChanges();

            const titleElement = systemNotificationComponentFixture.nativeElement.querySelector('.notification-title');
            expect(titleElement.textContent).toContain('Maintenance');

            const contentElement = systemNotificationComponentFixture.nativeElement.querySelector('.notification-content');
            expect(contentElement.textContent).toContain('Artemis will be unavailable');

            vi.useRealTimers();
        });
    });
});
