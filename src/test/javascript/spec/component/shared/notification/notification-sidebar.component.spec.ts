import dayjs from 'dayjs/esm';
import { BehaviorSubject, of, ReplaySubject } from 'rxjs';
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
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { MockUserSettingsService } from '../../../helpers/mocks/service/mock-user-settings.service';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NotificationSettingsService } from 'app/shared/user-settings/notification-settings/notification-settings.service';
import { MockNotificationSettingsService } from '../../../helpers/mocks/service/mock-notification-settings.service';

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
            declarations: [NotificationSidebarComponent, MockPipe(ArtemisTranslatePipe), MockRouterLinkDirective, MockComponent(FaIconComponent), MockDirective(NgbTooltip)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: NotificationSettingsService, useClass: MockNotificationSettingsService },
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

                const loadSettingsStub = jest.spyOn(userSettingsService, 'loadSettings');
                loadSettingsStub.mockReturnValue(of(new HttpResponse({ body: receivedNotificationSettings })));
            });
    });

    describe('Initialization', () => {
        it('should set last notification read', () => {
            const lastNotificationRead = dayjs();
            const getAuthenticationStateStub = jest.spyOn(accountService, 'getAuthenticationState');
            getAuthenticationStateStub.mockReturnValue(of({ lastNotificationRead } as User));

            notificationSidebarComponent.ngOnInit();
            expect(accountService.getAuthenticationState).toHaveBeenCalledOnce();
            expect(notificationSidebarComponent.lastNotificationRead).toBe(lastNotificationRead);
        });

        it('should query notifications', () => {
            jest.spyOn(notificationService, 'queryNotificationsFilteredBySettings');
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.queryNotificationsFilteredBySettings).toHaveBeenCalledOnce();
        });

        it('should subscribe to notification updates for user', () => {
            jest.spyOn(notificationService, 'subscribeToNotificationUpdates');
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.subscribeToNotificationUpdates).toHaveBeenCalledOnce();
        });
    });

    describe('Sidebar visibility', () => {
        it('should open sidebar when user clicks on notification bell', () => {
            jest.spyOn(notificationSidebarComponent, 'toggleSidebar');
            const bell = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-button');
            bell.click();
            expect(notificationSidebarComponent.toggleSidebar).toHaveBeenCalledOnce();
            expect(notificationSidebarComponent.showSidebar).toBeTrue();
        });

        it('should close sidebar when user clicks on notification overlay', () => {
            notificationSidebarComponent.showSidebar = true;
            jest.spyOn(notificationSidebarComponent, 'toggleSidebar');
            const overlay = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-overlay');
            overlay.click();
            expect(notificationSidebarComponent.toggleSidebar).toHaveBeenCalledOnce();
            expect(notificationSidebarComponent.showSidebar).toBeFalse();
        });

        it('should close sidebar when user clicks on close button', () => {
            notificationSidebarComponent.showSidebar = true;
            jest.spyOn(notificationSidebarComponent, 'toggleSidebar');
            const close = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.close');
            close.click();
            expect(notificationSidebarComponent.toggleSidebar).toHaveBeenCalledOnce();
            expect(notificationSidebarComponent.showSidebar).toBeFalse();
        });

        it('should close sidebar when user clicks on a notification', () => {
            notificationSidebarComponent.sortedNotifications = notifications;
            notificationSidebarComponentFixture.detectChanges();
            notificationSidebarComponent.showSidebar = true;
            const notification = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-item');
            notification.click();
            expect(notificationSidebarComponent.showSidebar).toBeFalse();
        });
    });

    describe('Notification click', () => {
        it('should interpret notification target when user clicks notification', () => {
            jest.spyOn(notificationService, 'interpretNotification');
            notificationSidebarComponent.sortedNotifications = notifications;
            notificationSidebarComponentFixture.detectChanges();
            const notification = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-item');
            notification.click();
            expect(notificationService.interpretNotification).toHaveBeenCalledOnce();
        });
    });

    describe('Last notification read', () => {
        it('should update users last notification read when user opens sidebar', fakeAsync(() => {
            jest.spyOn(notificationSidebarComponent, 'updateLastNotificationRead');
            jest.spyOn(userService, 'updateLastNotificationRead');
            const bell = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-button');
            bell.click();
            tick(2000);
            expect(notificationSidebarComponent.updateLastNotificationRead).toHaveBeenCalledOnce();
            expect(userService.updateLastNotificationRead).toHaveBeenCalledOnce();
        }));

        it('should update components last notification read two seconds after the user opened the sidebar', fakeAsync(() => {
            notificationSidebarComponent.lastNotificationRead = undefined;
            const bell = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-button');
            const lastNotificationReadNow = dayjs();
            bell.click();
            tick(2000);
            expect(notificationSidebarComponent.lastNotificationRead).toStrictEqual(lastNotificationReadNow);
        }));
    });

    describe('Load notifications', () => {
        const replaceSubscribeToNotificationUpdates = () => {
            const subscribeToNotificationUpdatesStub = jest.spyOn(notificationService, 'subscribeToNotificationUpdates');
            subscribeToNotificationUpdatesStub.mockReturnValue(new BehaviorSubject(notificationNow).asObservable() as ReplaySubject<Notification>);
        };

        it('should not add already existing notifications', () => {
            notificationSidebarComponent.notifications = [notificationNow];
            const queryNotificationsFilteredBySettingsStub = jest.spyOn(notificationService, 'queryNotificationsFilteredBySettings');
            queryNotificationsFilteredBySettingsStub.mockReturnValue(of(generateQueryResponse(notifications)));
            notificationSidebarComponent.ngOnInit();
            expect(notificationSidebarComponent.notifications.length).toBe(notifications.length);
        });

        it('should update sorted notifications array after new notifications were loaded', () => {
            const queryNotificationsFilteredBySettingsStub = jest.spyOn(notificationService, 'queryNotificationsFilteredBySettings');
            queryNotificationsFilteredBySettingsStub.mockReturnValue(of(generateQueryResponse([notificationPast, notificationNow])));
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.queryNotificationsFilteredBySettings).toHaveBeenCalledOnce();
            expect(notificationSidebarComponent.sortedNotifications[0]).toBe(notificationNow);
            expect(notificationSidebarComponent.sortedNotifications[1]).toBe(notificationPast);
        });

        it('should set total notification count to received X-Total-Count header', () => {
            const queryNotificationsFilteredBySettingsStub = jest.spyOn(notificationService, 'queryNotificationsFilteredBySettings');
            queryNotificationsFilteredBySettingsStub.mockReturnValue(of(generateQueryResponse(notifications)));
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.queryNotificationsFilteredBySettings).toHaveBeenCalledOnce();
            expect(notificationSidebarComponent.totalNotifications).toBe(notifications.length);
        });

        it('should increase total notification count if a new notification is received via websocket', () => {
            replaceSubscribeToNotificationUpdates();
            const queryNotificationsFilteredBySettingsStub = jest.spyOn(notificationService, 'queryNotificationsFilteredBySettings');
            queryNotificationsFilteredBySettingsStub.mockReturnValue(of(generateQueryResponse([notificationNow])));
            notificationSidebarComponent.ngOnInit();
            expect(notificationSidebarComponent.notifications.length).toBe(1);
            expect(notificationSidebarComponent.totalNotifications).toBe(1);
        });

        it('should not add already existing notification received via websocket', () => {
            notificationSidebarComponent.notifications = [notificationNow];
            notificationSidebarComponent.totalNotifications = 1;
            replaceSubscribeToNotificationUpdates();
            notificationSidebarComponent.ngOnInit();
            expect(notificationSidebarComponent.notifications.length).toBe(1);
            expect(notificationSidebarComponent.totalNotifications).toBe(1);
        });

        it('should load more notifications only if not all are already loaded', () => {
            notificationSidebarComponent.notifications = notifications;
            notificationSidebarComponent.totalNotifications = 2;
            jest.spyOn(notificationService, 'queryNotificationsFilteredBySettings');
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.queryNotificationsFilteredBySettings).not.toHaveBeenCalled();
        });
    });

    describe('Recent notifications', () => {
        it('should evaluate recent notifications correctly', () => {
            notificationSidebarComponent.lastNotificationRead = dayjs().subtract(1, 'day');
            const queryNotificationsFilteredBySettingsStub = jest.spyOn(notificationService, 'queryNotificationsFilteredBySettings');
            queryNotificationsFilteredBySettingsStub.mockReturnValue(of(generateQueryResponse(notifications)));

            notificationSidebarComponent.ngOnInit();
            expect(notificationService.queryNotificationsFilteredBySettings).toHaveBeenCalledOnce();
            expect(notificationSidebarComponent.recentNotificationCount).toBe(1);
        });

        it('should show plus sign in recent notification count badge if all loaded notifications are recent notifications', () => {
            notificationSidebarComponent.notifications = notifications;
            notificationSidebarComponent.recentNotificationCount = 2;
            notificationSidebarComponentFixture.detectChanges();
            const plus = notificationSidebarComponentFixture.debugElement.query(By.css('.bg-danger > span'));
            expect(plus).not.toBe(null);
        });
    });

    describe('UI', () => {
        it('should show no notifications message', () => {
            notificationSidebarComponent.notifications = [];
            notificationSidebarComponentFixture.detectChanges();
            const noNotificationsMessage = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.no-notifications');
            expect(noNotificationsMessage).not.toBe(null);
        });

        it('should show loading spinner when more notifications are loaded', () => {
            notificationSidebarComponent.loading = true;
            notificationSidebarComponentFixture.detectChanges();
            const loadingSpinner = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.loading-spinner');
            expect(loadingSpinner).not.toBe(null);
        });

        it('should show all notifications loaded message when all notifications are loaded', () => {
            notificationSidebarComponent.notifications = notifications;
            notificationSidebarComponent.totalNotifications = 1;
            notificationSidebarComponentFixture.detectChanges();
            const allLoadedMessage = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.all-loaded');
            expect(allLoadedMessage).not.toBe(null);
        });

        it('should show error message when loading of notifications failed', () => {
            notificationSidebarComponent.error = 'error';
            notificationSidebarComponentFixture.detectChanges();
            const errorMessage = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.alert-danger');
            expect(errorMessage).not.toBe(null);
        });

        it('should toggle which notifications are displayed (hide until property) when user clicks on archive button', () => {
            notificationSidebarComponent.ngOnInit();
            notificationSidebarComponent.notifications = notifications;
            expect(notificationSidebarComponent.showButtonToHideCurrentlyDisplayedNotifications).toBeTrue();
            jest.spyOn(notificationSidebarComponent, 'toggleNotificationDisplay');
            jest.spyOn(userService, 'updateNotificationVisibility');
            const hideUntilToggle = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('#hide-until-toggle');
            hideUntilToggle.click();
            expect(notificationSidebarComponent.showButtonToHideCurrentlyDisplayedNotifications).toBeFalse();
            expect(notificationSidebarComponent.toggleNotificationDisplay).toHaveBeenCalledOnce();
            expect(userService.updateNotificationVisibility).toHaveBeenCalledOnce();
        });
    });

    describe('Reset Sidebar', () => {
        it('should listen and catch notification settings change and reset side bar', () => {
            // preparation to test reloading
            const lastNotificationRead = dayjs();
            const getAuthenticationStateStub = jest.spyOn(accountService, 'getAuthenticationState');
            getAuthenticationStateStub.mockReturnValue(of({ lastNotificationRead } as User));

            notificationSidebarComponent.ngOnInit();

            // fake status before reloading the side bar
            notificationSidebarComponent.notifications = notifications;
            notificationSidebarComponent.totalNotifications = notifications.length;
            const priorNumberOfNotifications = notifications.length;

            // reload the side bar
            userSettingsService.sendApplyChangesEvent(reloadNotificationSideBarMessage);

            // test the reloading behavior
            expect(userSettingsService.loadSettings).toHaveBeenCalledTimes(2);
            expect(priorNumberOfNotifications).not.toBe(notificationSidebarComponent.totalNotifications);
        });
    });
});
