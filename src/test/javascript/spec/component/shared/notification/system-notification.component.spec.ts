import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SystemNotificationComponent } from 'app/shared/notification/system-notification/system-notification.component';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { AccountService } from 'app/core/auth/account.service';
import { ArtemisTestModule } from '../../../test.module';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { SystemNotification, SystemNotificationType } from 'app/entities/system-notification.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { faExclamationTriangle, faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';

describe('System Notification Component', () => {
    let systemNotificationComponent: SystemNotificationComponent;
    let systemNotificationComponentFixture: ComponentFixture<SystemNotificationComponent>;
    let systemNotificationService: SystemNotificationService;
    let jhiWebsocketService: JhiWebsocketService;

    const createActiveNotification = (type: SystemNotificationType) => {
        return {
            id: 1,
            title: 'Maintenance',
            text: 'Artemis will be unavailable',
            type,
            notificationDate: dayjs().subtract(1, 'days'),
            expireDate: dayjs().add(1, 'days'),
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

    describe('Load active notification', () => {
        it('should get active system notification with system notification type warning', fakeAsync(() => {
            const notification = createActiveNotification(SystemNotificationType.WARNING);
            const getActiveNotificationSpy = jest.spyOn(systemNotificationService, 'getActiveNotification').mockReturnValue(of(notification));
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(getActiveNotificationSpy).toHaveBeenCalledOnce();
            expect(systemNotificationComponent.notification).toEqual(notification);
            expect(systemNotificationComponent.alertClass).toBe('alert-warning');
            expect(systemNotificationComponent.alertIcon).toEqual(faExclamationTriangle);
        }));

        it('should get active system notification with system notification type info', fakeAsync(() => {
            const notification = createActiveNotification(SystemNotificationType.INFO);
            const getActiveNotificationSpy = jest.spyOn(systemNotificationService, 'getActiveNotification').mockReturnValue(of(notification));
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(getActiveNotificationSpy).toHaveBeenCalledOnce();
            expect(systemNotificationComponent.notification).toEqual(notification);
            expect(systemNotificationComponent.alertClass).toBe('alert-info');
            expect(systemNotificationComponent.alertIcon).toEqual(faInfoCircle);
        }));
    });

    describe('Websocket', () => {
        let connectionStateSubscribeSpy: jest.SpyInstance;
        beforeEach(() => {
            connectionStateSubscribeSpy = jest.spyOn(jhiWebsocketService.connectionState, 'subscribe');
        });

        it('should subscribe to socket messages on init', fakeAsync(() => {
            const subscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe');
            const receiveSpy = jest.spyOn(jhiWebsocketService, 'receive');
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(connectionStateSubscribeSpy).toHaveBeenCalledOnce();
            expect(systemNotificationComponent.websocketChannel).toBe('/topic/system-notification');
            expect(subscribeSpy).toHaveBeenCalledOnce();
            expect(receiveSpy).toHaveBeenCalledOnce();
        }));

        it('should delete notification when "deleted" is received via websocket', fakeAsync(() => {
            const receiveSpy = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of('deleted'));
            const getActiveNotificationSpy = jest.spyOn(systemNotificationService, 'getActiveNotification').mockReturnValue(of(null));
            systemNotificationComponent.notification = createActiveNotification(SystemNotificationType.WARNING);
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(receiveSpy).toHaveBeenCalledOnce();
            expect(systemNotificationComponent.notification).toBeUndefined();
            expect(getActiveNotificationSpy).toHaveBeenCalledTimes(2);
        }));

        it('should add notification when active notification is received via websocket', fakeAsync(() => {
            const notification = createActiveNotification(SystemNotificationType.WARNING);
            const subscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe');
            const receiveSpy = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(notification));
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(subscribeSpy).toHaveBeenCalledOnce();
            expect(receiveSpy).toHaveBeenCalledOnce();
            expect(systemNotificationComponent.notification).toEqual(notification);
            expect(systemNotificationComponent.alertClass).toBe('alert-warning');
            expect(systemNotificationComponent.alertIcon).toEqual(faExclamationTriangle);
        }));

        it('should not add notification when non-active notification is received via websocket', fakeAsync(() => {
            const notification = createActiveNotification(SystemNotificationType.WARNING);
            notification.expireDate = dayjs().subtract(5, 'minutes');
            const subscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe');
            const receiveSpy = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(notification));
            const getActiveNotificationSpy = jest.spyOn(systemNotificationService, 'getActiveNotification').mockReturnValue(of());
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(subscribeSpy).toHaveBeenCalledOnce();
            expect(receiveSpy).toHaveBeenCalledOnce();
            expect(getActiveNotificationSpy).toHaveBeenCalledTimes(2);
            expect(systemNotificationComponent.notification).toBeUndefined();
            expect(systemNotificationComponent.alertClass).toBeUndefined();
            expect(systemNotificationComponent.alertIcon).toBeUndefined();
        }));

        it('should update notification when same notification is received via websocket', fakeAsync(() => {
            const notification = createActiveNotification(SystemNotificationType.WARNING);
            const changedNotification = createActiveNotification(SystemNotificationType.INFO);
            const subscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe');
            const receiveSpy = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(changedNotification));
            const getActiveNotificationSpy = jest.spyOn(systemNotificationService, 'getActiveNotification').mockReturnValue(of(notification));
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(subscribeSpy).toHaveBeenCalledOnce();
            expect(receiveSpy).toHaveBeenCalledOnce();
            expect(getActiveNotificationSpy).toHaveBeenCalledOnce();
            expect(systemNotificationComponent.notification).toEqual(changedNotification);
        }));

        it('should update notification when new active notification is received via websocket', fakeAsync(() => {
            const notification = createActiveNotification(SystemNotificationType.WARNING);
            const newNotification = createActiveNotification(SystemNotificationType.INFO);
            newNotification.id = 2;
            newNotification.notificationDate = dayjs().subtract(2, 'days');
            newNotification.expireDate = dayjs().add(2, 'days');
            const subscribeSpy = jest.spyOn(jhiWebsocketService, 'subscribe');
            const receiveSpy = jest.spyOn(jhiWebsocketService, 'receive').mockReturnValue(of(newNotification));
            const getActiveNotificationSpy = jest.spyOn(systemNotificationService, 'getActiveNotification').mockReturnValue(of(notification));
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(subscribeSpy).toHaveBeenCalledOnce();
            expect(receiveSpy).toHaveBeenCalledOnce();
            expect(getActiveNotificationSpy).toHaveBeenCalledOnce();
            expect(systemNotificationComponent.notification).toEqual(newNotification);
        }));
    });
});
