import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import dayjs from 'dayjs';
import { BehaviorSubject, of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { NotificationSidebarComponent, reloadNotificationSideBarMessage } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { NotificationService } from 'app/shared/notification/notification.service';
import { ArtemisTestModule } from '../../../test.module';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockNotificationService } from '../../../helpers/mocks/service/mock-notification.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { Notification } from 'app/entities/notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { MockUserService } from '../../../helpers/mocks/service/mock-user.service';
import { UserService } from 'app/core/user/user.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { MockUserSettingsService } from '../../../helpers/mocks/service/mock-user-settings.service';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { SettingId } from 'app/shared/constants/user-settings.constants';

chai.use(sinonChai);
const expect = chai.expect;

describe('Notification Sidebar Component', () => {
    let notificationSidebarComponent: NotificationSidebarComponent;
    let notificationSidebarComponentFixture: ComponentFixture<NotificationSidebarComponent>;
    let notificationService: NotificationService;
    let accountService: AccountService;
    let userService: UserService;
    let userSettingsService: UserSettingsService;

    const notificationNow = { id: 1, notificationDate: dayjs() } as Notification;
    const notificationPast = { id: 2, notificationDate: dayjs().subtract(2, 'day') } as Notification;
    const notifications = [notificationNow, notificationPast] as Notification[];

    const notificationSettingA: NotificationSetting = {
        webapp: true,
        email: false,
        changed: false,
        settingId: SettingId.NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES,
    };
    const notificationSettingB: NotificationSetting = {
        webapp: true,
        email: false,
        changed: false,
        settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED,
    };
    const receivedNotificationSettings: NotificationSetting[] = [notificationSettingA, notificationSettingB];

    const generateQueryResponse = (ns: Notification[]) => {
        return {
            body: ns,
            headers: new HttpHeaders({
                'X-Total-Count': ns.length.toString(),
            }),
        } as HttpResponse<Notification[]>;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [NotificationSidebarComponent, MockPipe(ArtemisTranslatePipe), MockRouterLinkDirective, MockComponent(FaIconComponent)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: UserService, useClass: MockUserService },
                { provide: UserSettingsService, useClass: MockUserSettingsService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                notificationSidebarComponentFixture = TestBed.createComponent(NotificationSidebarComponent);
                notificationSidebarComponent = notificationSidebarComponentFixture.componentInstance;
                notificationService = TestBed.inject(NotificationService);
                accountService = TestBed.inject(AccountService);
                userService = TestBed.inject(UserService);
                userSettingsService = TestBed.inject(UserSettingsService);

                const fake2 = sinon.fake.returns(of(receivedNotificationSettings));
                sinon.replace(userSettingsService, 'loadSettings', fake2);
            });
    });

    describe('Initialization', () => {
        it('should set last notification read', () => {
            const lastNotificationRead = dayjs();
            const fake = sinon.fake.returns(of({ lastNotificationRead } as User));
            sinon.replace(accountService, 'getAuthenticationState', fake);
            notificationSidebarComponent.ngOnInit();
            expect(accountService.getAuthenticationState).to.have.been.calledOnce;
            expect(notificationSidebarComponent.lastNotificationRead).to.equal(lastNotificationRead);
        });

        it('should query notifications', () => {
            sinon.spy(notificationService, 'queryNotificationsFilteredBySettings');
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.queryNotificationsFilteredBySettings).to.have.been.calledOnce;
        });

        it('should subscribe to notification updates for user', () => {
            sinon.spy(notificationService, 'subscribeToNotificationUpdates');
            sinon.mock(notificationService);
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.subscribeToNotificationUpdates).to.have.been.calledOnce;
        });
    });

    describe('Sidebar visibility', () => {
        it('should open sidebar when user clicks on notification bell', () => {
            sinon.spy(notificationSidebarComponent, 'toggleSidebar');
            const bell = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-button');
            bell.click();
            expect(notificationSidebarComponent.toggleSidebar).to.have.been.calledOnce;
            expect(notificationSidebarComponent.showSidebar).to.be.true;
        });

        it('should close sidebar when user clicks on notification overlay', () => {
            notificationSidebarComponent.showSidebar = true;
            sinon.spy(notificationSidebarComponent, 'toggleSidebar');
            const overlay = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-overlay');
            overlay.click();
            expect(notificationSidebarComponent.toggleSidebar).to.have.been.calledOnce;
            expect(notificationSidebarComponent.showSidebar).to.be.false;
        });

        it('should close sidebar when user clicks on close button', () => {
            notificationSidebarComponent.showSidebar = true;
            sinon.spy(notificationSidebarComponent, 'toggleSidebar');
            const close = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.close');
            close.click();
            expect(notificationSidebarComponent.toggleSidebar).to.have.been.calledOnce;
            expect(notificationSidebarComponent.showSidebar).to.be.false;
        });

        it('should close sidebar when user clicks on a notification', () => {
            notificationSidebarComponent.sortedNotifications = notifications;
            notificationSidebarComponentFixture.detectChanges();
            notificationSidebarComponent.showSidebar = true;
            const notification = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-item');
            notification.click();
            expect(notificationSidebarComponent.showSidebar).to.be.false;
        });
    });

    describe('Notification click', () => {
        it('should interpret notification target when user clicks notification', () => {
            sinon.spy(notificationService, 'interpretNotification');
            notificationSidebarComponent.sortedNotifications = notifications;
            notificationSidebarComponentFixture.detectChanges();
            const notification = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-item');
            notification.click();
            expect(notificationService.interpretNotification).to.be.calledOnce;
        });
    });

    describe('Last notification read', () => {
        it('should update users last notification read when user opens sidebar', fakeAsync(() => {
            sinon.spy(notificationSidebarComponent, 'updateLastNotificationRead');
            sinon.spy(userService, 'updateLastNotificationRead');
            const bell = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-button');
            bell.click();
            tick(2000);
            expect(notificationSidebarComponent.updateLastNotificationRead).to.have.been.calledOnce;
            expect(userService.updateLastNotificationRead).to.have.been.calledOnce;
        }));

        it('should update components last notification read two seconds after the user opened the sidebar', fakeAsync(() => {
            notificationSidebarComponent.lastNotificationRead = undefined;
            const bell = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-button');
            const lastNotificationReadNow = dayjs();
            bell.click();
            tick(2000);
            expect(notificationSidebarComponent.lastNotificationRead).to.be.eql(lastNotificationReadNow);
        }));
    });

    describe('Load notifications', () => {
        const replaceSubscribeToNotificationUpdates = () => {
            const fake = sinon.fake.returns(new BehaviorSubject(notificationNow));
            sinon.replace(notificationService, 'subscribeToNotificationUpdates', fake);
        };

        it('should not add already existing notifications', () => {
            notificationSidebarComponent.notifications = [notificationNow];
            const fake = sinon.fake.returns(of(generateQueryResponse(notifications)));
            sinon.replace(notificationService, 'queryNotificationsFilteredBySettings', fake);
            notificationSidebarComponent.ngOnInit();
            expect(notificationSidebarComponent.notifications.length).to.be.equal(notifications.length);
        });

        it('should update sorted notifications array after new notifications were loaded', () => {
            const fake = sinon.fake.returns(of(generateQueryResponse([notificationPast, notificationNow])));
            sinon.replace(notificationService, 'queryNotificationsFilteredBySettings', fake);
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.queryNotificationsFilteredBySettings).to.have.been.calledOnce;
            expect(notificationSidebarComponent.sortedNotifications[0]).to.be.equal(notificationNow);
            expect(notificationSidebarComponent.sortedNotifications[1]).to.be.equal(notificationPast);
        });

        it('should set total notification count to received X-Total-Count header', () => {
            const fake = sinon.fake.returns(of(generateQueryResponse(notifications)));
            sinon.replace(notificationService, 'queryNotificationsFilteredBySettings', fake);
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.queryNotificationsFilteredBySettings).to.have.been.calledOnce;
            expect(notificationSidebarComponent.totalNotifications).to.be.equal(notifications.length);
        });

        it('should increase total notification count if a new notification is received via websocket', () => {
            replaceSubscribeToNotificationUpdates();
            notificationSidebarComponent.ngOnInit();
            expect(notificationSidebarComponent.notifications.length).to.be.equal(1);
            expect(notificationSidebarComponent.totalNotifications).to.be.equal(1);
        });

        it('should not add already existing notification received via websocket', () => {
            notificationSidebarComponent.notifications = [notificationNow];
            notificationSidebarComponent.totalNotifications = 1;
            replaceSubscribeToNotificationUpdates();
            notificationSidebarComponent.ngOnInit();
            expect(notificationSidebarComponent.notifications.length).to.be.equal(1);
            expect(notificationSidebarComponent.totalNotifications).to.be.equal(1);
        });

        it('should load more notifications only if not all are already loaded', () => {
            notificationSidebarComponent.notifications = notifications;
            notificationSidebarComponent.totalNotifications = 2;
            sinon.spy(notificationService, 'queryNotificationsFilteredBySettings');
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.queryNotificationsFilteredBySettings).not.to.be.called;
        });
    });

    describe('Recent notifications', () => {
        it('should evaluate recent notifications correctly', () => {
            notificationSidebarComponent.lastNotificationRead = dayjs().subtract(1, 'day');
            const fake = sinon.fake.returns(of(generateQueryResponse(notifications)));
            sinon.replace(notificationService, 'queryNotificationsFilteredBySettings', fake);
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.queryNotificationsFilteredBySettings).to.be.called;
            expect(notificationSidebarComponent.recentNotificationCount).to.be.equal(1);
        });

        it('should show plus sign in recent notification count badge if all loaded notifications are recent notifications', () => {
            notificationSidebarComponent.notifications = notifications;
            notificationSidebarComponent.recentNotificationCount = 2;
            notificationSidebarComponentFixture.detectChanges();
            const plus = notificationSidebarComponentFixture.debugElement.query(By.css('.bg-danger > span'));
            expect(plus).to.be.not.null;
        });
    });

    describe('UI', () => {
        it('should show no notifications message', () => {
            notificationSidebarComponent.notifications = [];
            notificationSidebarComponentFixture.detectChanges();
            const noNotificationsMessage = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.no-notifications');
            expect(noNotificationsMessage).to.be.not.null;
        });

        it('should show loading spinner when more notifications are loaded', () => {
            notificationSidebarComponent.loading = true;
            notificationSidebarComponentFixture.detectChanges();
            const loadingSpinner = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.loading-spinner');
            expect(loadingSpinner).to.be.not.null;
        });

        it('should show all notifications loaded message when all notifications are loaded', () => {
            notificationSidebarComponent.notifications = notifications;
            notificationSidebarComponent.totalNotifications = 1;
            notificationSidebarComponentFixture.detectChanges();
            const allLoadedMessage = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.all-loaded');
            expect(allLoadedMessage).to.be.not.null;
        });

        it('should show error message when loading of notifications failed', () => {
            notificationSidebarComponent.error = 'error';
            notificationSidebarComponentFixture.detectChanges();
            const errorMessage = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.alert-danger');
            expect(errorMessage).to.be.not.null;
        });
    });

    describe('Reset Sidebar', () => {
        it('should listen and catch notification settings change and reset side bar', () => {
            // preparation to test reloading
            const lastNotificationRead = dayjs();
            const fake = sinon.fake.returns(of({ lastNotificationRead } as User));
            sinon.replace(accountService, 'getAuthenticationState', fake);
            notificationSidebarComponent.ngOnInit();

            // fake status before reloading the side bar
            notificationSidebarComponent.notifications = notifications;
            notificationSidebarComponent.totalNotifications = notifications.length;
            const priorNumberOfNotifications = notifications.length;

            // reload the side bar
            userSettingsService.sendApplyChangesEvent(reloadNotificationSideBarMessage);

            // test the reloading behavior
            expect(userSettingsService.loadSettings).to.have.been.calledTwice;
            expect(priorNumberOfNotifications).not.to.be.equal(notificationSidebarComponent.totalNotifications);
        });
    });
});
