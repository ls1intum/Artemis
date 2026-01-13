import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { SystemNotification, SystemNotificationType } from 'app/core/shared/entities/system-notification.model';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { SystemNotificationComponent, WEBSOCKET_CHANNEL } from 'app/core/notification/system-notification/system-notification.component';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';

describe('System Notification Component', () => {
    let systemNotificationComponent: SystemNotificationComponent;
    let systemNotificationComponentFixture: ComponentFixture<SystemNotificationComponent>;
    let systemNotificationService: SystemNotificationService;
    let websocketService: WebsocketService;

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
            });
    });

    afterEach(() => {
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
});
