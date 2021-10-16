import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import dayjs from 'dayjs';
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

chai.use(sinonChai);
const expect = chai.expect;

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
                { provide: jhiWebsocketService, useClass: JhiWebsocketService },
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
            const fake = sinon.fake.returns(of(notification));
            sinon.replace(systemNotificationService, 'getActiveNotification', fake);
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(systemNotificationService.getActiveNotification).to.have.been.calledOnce;
            expect(systemNotificationComponent.notification).to.equal(notification);
            expect(systemNotificationComponent.alertClass).to.equal('alert-warning');
            expect(systemNotificationComponent.alertIcon).to.equal('exclamation-triangle');
        }));

        it('should get active system notification with system notification type info', fakeAsync(() => {
            const notification = createActiveNotification(SystemNotificationType.INFO);
            const fake = sinon.fake.returns(of(notification));
            sinon.replace(systemNotificationService, 'getActiveNotification', fake);
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(systemNotificationService.getActiveNotification).to.have.been.calledOnce;
            expect(systemNotificationComponent.notification).to.equal(notification);
            expect(systemNotificationComponent.alertClass).to.equal('alert-info');
            expect(systemNotificationComponent.alertIcon).to.equal('info-circle');
        }));
    });

    describe('Websocket', () => {
        beforeEach(() => {
            const fake = sinon.fake.yields('event');
            sinon.replace(jhiWebsocketService, 'bind', fake);
        });

        it('should subscribe to socket messages on init', fakeAsync(() => {
            sinon.spy(jhiWebsocketService, 'subscribe');
            sinon.spy(jhiWebsocketService, 'receive');
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(jhiWebsocketService.bind).to.have.been.calledOnce;
            expect(systemNotificationComponent.websocketChannel).to.equal('/topic/system-notification');
            expect(jhiWebsocketService.subscribe).to.have.been.calledOnce;
            expect(jhiWebsocketService.receive).to.have.been.calledOnce;
        }));

        it('should delete notification when "deleted" is received via websocket', fakeAsync(() => {
            sinon.replace(jhiWebsocketService, 'receive', sinon.fake.returns(of('deleted')));
            sinon.replace(systemNotificationService, 'getActiveNotification', sinon.fake.returns(of(null)));
            systemNotificationComponent.notification = createActiveNotification(SystemNotificationType.WARNING);
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(jhiWebsocketService.receive).to.have.been.calledOnce;
            expect(systemNotificationComponent.notification).to.equal(undefined);
            expect(systemNotificationService.getActiveNotification).to.have.been.calledTwice;
        }));

        it('should add notification when active notification is received via websocket', fakeAsync(() => {
            const notification = createActiveNotification(SystemNotificationType.WARNING);
            sinon.spy(jhiWebsocketService, 'subscribe');
            sinon.replace(jhiWebsocketService, 'receive', sinon.fake.returns(of(notification)));
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(jhiWebsocketService.subscribe).to.have.been.calledOnce;
            expect(jhiWebsocketService.receive).to.have.been.calledOnce;
            expect(systemNotificationComponent.notification).to.equal(notification);
            expect(systemNotificationComponent.alertClass).to.equal('alert-warning');
            expect(systemNotificationComponent.alertIcon).to.equal('exclamation-triangle');
        }));

        it('should not add notification when non-active notification is received via websocket', fakeAsync(() => {
            const notification = createActiveNotification(SystemNotificationType.WARNING);
            notification.expireDate = dayjs().subtract(5, 'minutes');
            sinon.spy(jhiWebsocketService, 'subscribe');
            sinon.replace(jhiWebsocketService, 'receive', sinon.fake.returns(of(notification)));
            sinon.replace(systemNotificationService, 'getActiveNotification', sinon.fake.returns(of()));
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(jhiWebsocketService.subscribe).to.have.been.calledOnce;
            expect(jhiWebsocketService.receive).to.have.been.calledOnce;
            expect(systemNotificationService.getActiveNotification).to.have.been.calledTwice;
            expect(systemNotificationComponent.notification).to.equal(undefined);
            expect(systemNotificationComponent.alertClass).to.equal(undefined);
            expect(systemNotificationComponent.alertIcon).to.equal(undefined);
        }));

        it('should update notification when same notification is received via websocket', fakeAsync(() => {
            const notification = createActiveNotification(SystemNotificationType.WARNING);
            const changedNotification = createActiveNotification(SystemNotificationType.INFO);
            sinon.spy(jhiWebsocketService, 'subscribe');
            sinon.replace(jhiWebsocketService, 'receive', sinon.fake.returns(of(changedNotification)));
            sinon.replace(systemNotificationService, 'getActiveNotification', sinon.fake.returns(of(notification)));
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(jhiWebsocketService.subscribe).to.have.been.calledOnce;
            expect(jhiWebsocketService.receive).to.have.been.calledOnce;
            expect(systemNotificationService.getActiveNotification).to.have.been.calledOnce;
            expect(systemNotificationComponent.notification).to.equal(changedNotification);
        }));

        it('should update notification when new active notification is received via websocket', fakeAsync(() => {
            const notification = createActiveNotification(SystemNotificationType.WARNING);
            const newNotification = createActiveNotification(SystemNotificationType.INFO);
            newNotification.id = 2;
            newNotification.notificationDate = dayjs().subtract(2, 'days');
            newNotification.expireDate = dayjs().add(2, 'days');
            sinon.spy(jhiWebsocketService, 'subscribe');
            sinon.replace(jhiWebsocketService, 'receive', sinon.fake.returns(of(newNotification)));
            sinon.replace(systemNotificationService, 'getActiveNotification', sinon.fake.returns(of(notification)));
            systemNotificationComponent.ngOnInit();
            tick(500);
            expect(jhiWebsocketService.subscribe).to.have.been.calledOnce;
            expect(jhiWebsocketService.receive).to.have.been.calledOnce;
            expect(systemNotificationService.getActiveNotification).to.have.been.calledOnce;
            expect(systemNotificationComponent.notification).to.equal(newNotification);
        }));
    });
});
