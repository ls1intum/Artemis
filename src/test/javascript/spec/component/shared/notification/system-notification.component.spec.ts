import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SystemNotificationComponent, WEBSOCKET_CHANNEL } from 'app/shared/notification/system-notification/system-notification.component';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { AccountService } from 'app/core/auth/account.service';
import { ArtemisTestModule } from '../../../test.module';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { SystemNotification, SystemNotificationType } from 'app/entities/system-notification.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';

describe('System Notification Component', () => {
    let systemNotificationComponent: SystemNotificationComponent;
    let systemNotificationComponentFixture: ComponentFixture<SystemNotificationComponent>;
    let systemNotificationService: SystemNotificationService;
    let jhiWebsocketService: JhiWebsocketService;

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
            imports: [ArtemisTestModule],
            declarations: [SystemNotificationComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: AccountService, useClass: MockAccountService },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
            ],
        })
            .compileComponents()
            .then(() => {
                systemNotificationComponentFixture = TestBed.createComponent(SystemNotificationComponent);
                systemNotificationComponent = systemNotificationComponentFixture.componentInstance;
                systemNotificationService = TestBed.inject(SystemNotificationService);
                jhiWebsocketService = TestBed.inject(JhiWebsocketService);
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

        const subscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe');
        const receiveSpy = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(newNotifications));
        const getActiveNotificationSpy = jest.spyOn(systemNotificationService, 'getActiveNotifications').mockReturnValue(of(originalNotifications));

        systemNotificationComponent.ngOnInit();
        expect(systemNotificationComponent.notifications).toEqual(originalNotifications);
        expect(systemNotificationComponent.notificationsToDisplay).toEqual([originalNotifications[0]]);

        tick(500);

        expect(systemNotificationComponent.notifications).toEqual(newNotifications);
        expect(systemNotificationComponent.notificationsToDisplay).toEqual([newNotifications[0]]);
        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(subscribeSpy).toHaveBeenCalledWith(WEBSOCKET_CHANNEL);
        expect(receiveSpy).toHaveBeenCalledOnce();
        expect(receiveSpy).toHaveBeenCalledWith(WEBSOCKET_CHANNEL);
        expect(getActiveNotificationSpy).toHaveBeenCalledOnce();
        flush();
    }));
});
