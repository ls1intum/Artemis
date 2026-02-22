import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { Subject, of } from 'rxjs';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
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

describe('System Notification Component', () => {
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

    beforeEach(() => {
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
        })
            .compileComponents()
            .then(() => {
                systemNotificationComponentFixture = TestBed.createComponent(SystemNotificationComponent);
                systemNotificationComponent = systemNotificationComponentFixture.componentInstance;
                systemNotificationService = TestBed.inject(SystemNotificationService);
                websocketService = TestBed.inject(WebsocketService);
                localStorageService = TestBed.inject(LocalStorageService);
            });
    });

    afterEach(() => {
        localStorage.removeItem(CLOSED_NOTIFICATION_IDS_STORAGE_KEY);
        jest.restoreAllMocks();
    });

    it('should get system notifications, display active ones on init, and schedule changes correctly', fakeAsync(() => {
        const notifications = [
            createActiveNotification(SystemNotificationType.WARNING, 1),
            createActiveNotification(SystemNotificationType.INFO, 2),
            createInactiveNotification(SystemNotificationType.WARNING, 3),
            createInactiveNotification(SystemNotificationType.INFO, 4),
        ];
        const getActiveNotificationSpy = jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));
        systemNotificationComponent.ngOnInit();
        tick(500);
        expect(getActiveNotificationSpy).toHaveBeenCalledOnce();
        expect(systemNotificationComponent.notifications).toEqual(notifications);
        expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[0], notifications[1]]);

        tick(60 * 60 * 1000); // one hour

        expect(systemNotificationComponent.notifications).toEqual(notifications);
        expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[2], notifications[3]]);

        flush();
    }));

    it('should connect to ws on init and update notifications to new array of notifications received through it', fakeAsync(() => {
        const originalNotifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createInactiveNotification(SystemNotificationType.INFO, 2)];
        const newNotifications = [createActiveNotification(SystemNotificationType.WARNING, 3), createInactiveNotification(SystemNotificationType.INFO, 4)];

        const subscribeSpy = jest.spyOn(websocketService, 'subscribe').mockReturnValue(of(newNotifications));
        const getActiveNotificationSpy = jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(originalNotifications));

        systemNotificationComponent.ngOnInit();
        expect(systemNotificationComponent.notifications).toEqual(originalNotifications);
        expect(systemNotificationComponent.notificationsToDisplay).toEqual([originalNotifications[0]]);

        tick(500);

        expect(systemNotificationComponent.notifications).toEqual(newNotifications);
        expect(systemNotificationComponent.notificationsToDisplay).toEqual([newNotifications[0]]);
        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(subscribeSpy).toHaveBeenCalledWith(WEBSOCKET_CHANNEL);
        expect(getActiveNotificationSpy).toHaveBeenCalledOnce();
        flush();
    }));

    describe('Persistence of dismissed notification IDs', () => {
        it('should save closed notification IDs to localStorage', fakeAsync(() => {
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));
            const storeSpy = jest.spyOn(localStorageService, 'store');

            systemNotificationComponent.ngOnInit();
            tick(500);

            systemNotificationComponent.close(notifications[0]);

            expect(storeSpy).toHaveBeenCalledWith(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1]);
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[1]]);
            flush();
        }));

        it('should load closed notification IDs from localStorage on init', fakeAsync(() => {
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1]);
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponent.ngOnInit();
            tick(500);

            expect(systemNotificationComponent.closedIds).toEqual([1]);
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[1]]);
            flush();
        }));

        it('should not reset closed IDs on websocket update', fakeAsync(() => {
            const originalNotifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            const updatedNotifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];

            localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1]);
            jest.spyOn(websocketService, 'subscribe').mockReturnValue(of(updatedNotifications));
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(originalNotifications));

            systemNotificationComponent.ngOnInit();
            tick(500);

            // After websocket update, notification 1 should still be closed
            expect(systemNotificationComponent.closedIds).toEqual([1]);
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([updatedNotifications[1]]);
            flush();
        }));

        it('should prune stale closed IDs when notifications are updated via websocket', fakeAsync(() => {
            const originalNotifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            // New notifications no longer include notification 1
            const updatedNotifications = [createActiveNotification(SystemNotificationType.INFO, 2), createActiveNotification(SystemNotificationType.WARNING, 3)];

            localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1]);
            jest.spyOn(websocketService, 'subscribe').mockReturnValue(of(updatedNotifications));
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(originalNotifications));

            systemNotificationComponent.ngOnInit();
            tick(500);

            // Stale ID 1 should be pruned since it's no longer in the notification list
            expect(systemNotificationComponent.closedIds).toEqual([]);
            expect(localStorageService.retrieve(CLOSED_NOTIFICATION_IDS_STORAGE_KEY)).toEqual([]);
            flush();
        }));

        it('should default to empty array when localStorage has no stored IDs', fakeAsync(() => {
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of([]));

            systemNotificationComponent.ngOnInit();
            tick(500);

            expect(systemNotificationComponent.closedIds).toEqual([]);
            flush();
        }));

        it('should accumulate multiple closed IDs in localStorage', fakeAsync(() => {
            const notifications = [
                createActiveNotification(SystemNotificationType.WARNING, 1),
                createActiveNotification(SystemNotificationType.INFO, 2),
                createActiveNotification(SystemNotificationType.WARNING, 3),
            ];
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponent.ngOnInit();
            tick(500);

            systemNotificationComponent.close(notifications[0]);
            expect(localStorageService.retrieve(CLOSED_NOTIFICATION_IDS_STORAGE_KEY)).toEqual([1]);
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[1], notifications[2]]);

            systemNotificationComponent.close(notifications[2]);
            expect(localStorageService.retrieve(CLOSED_NOTIFICATION_IDS_STORAGE_KEY)).toEqual([1, 3]);
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([notifications[1]]);

            flush();
        }));

        it('should keep valid closed IDs and prune only stale ones on websocket update', fakeAsync(() => {
            const originalNotifications = [
                createActiveNotification(SystemNotificationType.WARNING, 1),
                createActiveNotification(SystemNotificationType.INFO, 2),
                createActiveNotification(SystemNotificationType.WARNING, 3),
            ];
            // Notification 1 is removed, 2 and 3 remain
            const updatedNotifications = [createActiveNotification(SystemNotificationType.INFO, 2), createActiveNotification(SystemNotificationType.WARNING, 3)];

            // User had closed notifications 1 and 2
            localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1, 2]);
            jest.spyOn(websocketService, 'subscribe').mockReturnValue(of(updatedNotifications));
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(originalNotifications));

            systemNotificationComponent.ngOnInit();
            tick(500);

            // ID 1 pruned (not in updated list), ID 2 kept (still in updated list)
            expect(systemNotificationComponent.closedIds).toEqual([2]);
            expect(localStorageService.retrieve(CLOSED_NOTIFICATION_IDS_STORAGE_KEY)).toEqual([2]);
            // Only notification 3 should be displayed (2 is closed, 1 was removed)
            expect(systemNotificationComponent.notificationsToDisplay).toEqual([updatedNotifications[1]]);
            flush();
        }));

        it('should persist dismissal across multiple websocket updates', fakeAsync(() => {
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            const wsSubject = new Subject<SystemNotification[]>();

            jest.spyOn(websocketService, 'subscribe').mockReturnValue(wsSubject);
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponent.ngOnInit();
            tick(500);

            // Close notification 1
            systemNotificationComponent.close(notifications[0]);
            expect(localStorageService.retrieve(CLOSED_NOTIFICATION_IDS_STORAGE_KEY)).toEqual([1]);

            // Websocket sends same notifications again — closed ID should persist
            wsSubject.next([createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)]);

            expect(systemNotificationComponent.closedIds).toEqual([1]);
            expect(systemNotificationComponent.notificationsToDisplay).toHaveLength(1);
            expect(systemNotificationComponent.notificationsToDisplay[0].id).toBe(2);

            flush();
        }));
    });

    describe('CSS variable for notification height', () => {
        it('should set --system-notification-height CSS variable on the document root after view check', fakeAsync(() => {
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1)];
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            const renderer = systemNotificationComponentFixture.debugElement.injector.get(Renderer2);
            const setStyleSpy = jest.spyOn(renderer, 'setStyle');

            // Simulate a previous height that differs from the actual element height (0 in jsdom)
            // so that ngAfterViewChecked detects a change and calls setStyle
            (systemNotificationComponent as any).lastNotificationHeight = 40;

            // Use detectChanges to trigger both ngOnInit and ngAfterViewChecked
            systemNotificationComponentFixture.detectChanges();
            tick(500);

            // In jsdom, offsetHeight is 0, so the variable will be set to '0px'
            const setStyleCalls = setStyleSpy.mock.calls.filter((call) => call[1] === '--system-notification-height');
            expect(setStyleCalls.length).toBeGreaterThanOrEqual(1);
            expect(setStyleCalls[0]).toEqual([document.documentElement, '--system-notification-height', '0px', RendererStyleFlags2.DashCase]);

            flush();
        }));

        it('should reset --system-notification-height to 0px on destroy', fakeAsync(() => {
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of([]));

            const renderer = systemNotificationComponentFixture.debugElement.injector.get(Renderer2);
            const setStyleSpy = jest.spyOn(renderer, 'setStyle');

            systemNotificationComponent.ngOnInit();
            tick(500);

            systemNotificationComponent.ngOnDestroy();

            expect(setStyleSpy).toHaveBeenCalledWith(document.documentElement, '--system-notification-height', '0px', RendererStyleFlags2.DashCase);
            flush();
        }));

        it('should only update CSS variable when height actually changes', fakeAsync(() => {
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of([]));

            const renderer = systemNotificationComponentFixture.debugElement.injector.get(Renderer2);
            const setStyleSpy = jest.spyOn(renderer, 'setStyle');

            // First detectChanges triggers ngOnInit + ngAfterViewChecked
            systemNotificationComponentFixture.detectChanges();
            tick(500);

            const callCountAfterFirst = setStyleSpy.mock.calls.filter((call) => call[1] === '--system-notification-height').length;

            // Trigger another change detection cycle — height hasn't changed, so no additional setStyle call
            systemNotificationComponentFixture.detectChanges();

            const callCountAfterSecond = setStyleSpy.mock.calls.filter((call) => call[1] === '--system-notification-height').length;
            expect(callCountAfterSecond).toBe(callCountAfterFirst);

            flush();
        }));

        it('should update CSS variable when lastNotificationHeight changes', fakeAsync(() => {
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            const renderer = systemNotificationComponentFixture.debugElement.injector.get(Renderer2);
            const setStyleSpy = jest.spyOn(renderer, 'setStyle');

            systemNotificationComponentFixture.detectChanges();
            tick(500);

            // Simulate a previous height being different to force the update check to detect a change
            (systemNotificationComponent as any).lastNotificationHeight = 50;

            // Close a notification and trigger change detection
            systemNotificationComponent.close(notifications[0]);
            systemNotificationComponentFixture.detectChanges();

            // The height changed (from simulated 50 to actual 0 in jsdom), so setStyle should be called
            const lastCall = setStyleSpy.mock.calls.filter((call) => call[1] === '--system-notification-height').pop();
            expect(lastCall).toEqual([document.documentElement, '--system-notification-height', '0px', RendererStyleFlags2.DashCase]);

            flush();
        }));
    });

    describe('DOM rendering', () => {
        it('should render notification elements for active notifications', fakeAsync(() => {
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            tick(500);
            systemNotificationComponentFixture.detectChanges();

            const notificationElements = systemNotificationComponentFixture.nativeElement.querySelectorAll('.system-notification');
            expect(notificationElements).toHaveLength(2);

            flush();
        }));

        it('should not render closed notifications in the DOM', fakeAsync(() => {
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            localStorageService.store(CLOSED_NOTIFICATION_IDS_STORAGE_KEY, [1]);
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            tick(500);
            systemNotificationComponentFixture.detectChanges();

            const notificationElements = systemNotificationComponentFixture.nativeElement.querySelectorAll('.system-notification');
            expect(notificationElements).toHaveLength(1);

            flush();
        }));

        it('should remove notification from DOM when close button is clicked', fakeAsync(() => {
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1), createActiveNotification(SystemNotificationType.INFO, 2)];
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            tick(500);
            systemNotificationComponentFixture.detectChanges();

            expect(systemNotificationComponentFixture.nativeElement.querySelectorAll('.system-notification')).toHaveLength(2);

            // Click the close button on the first notification
            const closeButton = systemNotificationComponentFixture.nativeElement.querySelector('.notification-close');
            closeButton.click();
            systemNotificationComponentFixture.detectChanges();

            expect(systemNotificationComponentFixture.nativeElement.querySelectorAll('.system-notification')).toHaveLength(1);

            flush();
        }));

        it('should apply correct CSS class for warning notifications', fakeAsync(() => {
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1)];
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            tick(500);
            systemNotificationComponentFixture.detectChanges();

            const notificationElement = systemNotificationComponentFixture.nativeElement.querySelector('.system-notification');
            expect(notificationElement).toBeTruthy();
            expect(notificationElement.classList.contains('warning')).toBeTrue();
            expect(notificationElement.classList.contains('info')).toBeFalse();

            flush();
        }));

        it('should apply correct CSS class for info notifications', fakeAsync(() => {
            const notifications = [createActiveNotification(SystemNotificationType.INFO, 1)];
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            tick(500);
            systemNotificationComponentFixture.detectChanges();

            const notificationElement = systemNotificationComponentFixture.nativeElement.querySelector('.system-notification');
            expect(notificationElement).toBeTruthy();
            expect(notificationElement.classList.contains('info')).toBeTrue();
            expect(notificationElement.classList.contains('warning')).toBeFalse();

            flush();
        }));

        it('should render no notification elements when all are dismissed', fakeAsync(() => {
            const notifications = [createActiveNotification(SystemNotificationType.WARNING, 1)];
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            tick(500);
            systemNotificationComponentFixture.detectChanges();

            expect(systemNotificationComponentFixture.nativeElement.querySelectorAll('.system-notification')).toHaveLength(1);

            systemNotificationComponent.close(notifications[0]);
            systemNotificationComponentFixture.detectChanges();

            expect(systemNotificationComponentFixture.nativeElement.querySelectorAll('.system-notification')).toHaveLength(0);

            flush();
        }));

        it('should display notification title and text', fakeAsync(() => {
            const notifications = [createActiveNotification(SystemNotificationType.INFO, 1)];
            jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(notifications));

            systemNotificationComponentFixture.detectChanges();
            tick(500);
            systemNotificationComponentFixture.detectChanges();

            const titleElement = systemNotificationComponentFixture.nativeElement.querySelector('.notification-title');
            expect(titleElement.textContent).toContain('Maintenance');

            const contentElement = systemNotificationComponentFixture.nativeElement.querySelector('.notification-content');
            expect(contentElement.textContent).toContain('Artemis will be unavailable');

            flush();
        }));
    });
});
