import dayjs from 'dayjs/esm';
import { BehaviorSubject, ReplaySubject, of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { LAST_READ_STORAGE_KEY, NotificationSidebarComponent } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { NotificationService } from 'app/shared/notification/notification.service';
import { ArtemisTestModule } from '../../../test.module';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockNotificationService } from '../../../helpers/mocks/service/mock-notification.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ATTACHMENT_CHANGE_TITLE, EXERCISE_SUBMISSION_ASSESSED_TITLE, NEW_MESSAGE_TITLE, Notification } from 'app/entities/notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { MockUserService } from '../../../helpers/mocks/service/mock-user.service';
import { UserService } from 'app/core/user/user.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { NotificationSettingsService } from 'app/shared/user-settings/notification-settings/notification-settings.service';
import { MockNotificationSettingsService } from '../../../helpers/mocks/service/mock-notification-settings.service';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';

describe('Notification Sidebar Component', () => {
    let notificationSidebarComponent: NotificationSidebarComponent;
    let notificationSidebarComponentFixture: ComponentFixture<NotificationSidebarComponent>;
    let notificationService: NotificationService;
    let notificationSettingsService: NotificationSettingsService;
    let accountService: AccountService;
    let userService: UserService;
    let userSettingsService: UserSettingsService;
    let sessionStorageService: SessionStorageService;
    let artemisTranslatePipe: ArtemisTranslatePipe;

    const notificationNow = { id: 1, notificationDate: dayjs(), title: EXERCISE_SUBMISSION_ASSESSED_TITLE } as Notification;
    const notificationPast = { id: 2, notificationDate: dayjs().subtract(2, 'day'), title: ATTACHMENT_CHANGE_TITLE } as Notification;
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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockDirective(NgbTooltip)],
            declarations: [NotificationSidebarComponent, MockPipe(ArtemisTranslatePipe), MockRouterLinkDirective, MockComponent(DocumentationButtonComponent)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: NotificationSettingsService, useClass: MockNotificationSettingsService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: UserService, useClass: MockUserService },
                { provide: ArtemisTranslatePipe, useClass: ArtemisTranslatePipe },
            ],
        })
            .compileComponents()
            .then(() => {
                notificationSidebarComponentFixture = TestBed.createComponent(NotificationSidebarComponent);
                notificationSidebarComponent = notificationSidebarComponentFixture.componentInstance;
                notificationService = TestBed.inject(NotificationService);
                notificationSettingsService = TestBed.inject(NotificationSettingsService);
                accountService = TestBed.inject(AccountService);
                userService = TestBed.inject(UserService);
                userSettingsService = TestBed.inject(UserSettingsService);
                sessionStorageService = TestBed.inject(SessionStorageService);
                artemisTranslatePipe = TestBed.inject(ArtemisTranslatePipe);

                const loadSettingsStub = jest.spyOn(userSettingsService, 'loadSettings');
                loadSettingsStub.mockReturnValue(of(new HttpResponse({ body: receivedNotificationSettings })));
            });
    });

    describe('Initialization', () => {
        const referenceDate = dayjs();

        it.each([
            { userDate: referenceDate, storageDate: undefined, expectedDate: referenceDate },
            { userDate: undefined, storageDate: referenceDate, expectedDate: referenceDate },
            { userDate: referenceDate, storageDate: referenceDate.add(5, 'minutes'), expectedDate: referenceDate.add(5, 'minutes') },
            { userDate: referenceDate, storageDate: referenceDate.subtract(5, 'minutes'), expectedDate: referenceDate },
        ])('should set the correct last notification read', ({ userDate, storageDate, expectedDate }) => {
            const getAuthenticationStateStub = jest.spyOn(accountService, 'getAuthenticationState').mockReturnValue(of({ lastNotificationRead: userDate } as User));
            const sessionStorageSub = jest.spyOn(sessionStorageService, 'retrieve').mockReturnValue((storageDate as dayjs.Dayjs | undefined)?.toISOString());

            notificationSidebarComponent.ngOnInit();
            expect(getAuthenticationStateStub).toHaveBeenCalledOnce();
            expect(sessionStorageSub).toHaveBeenCalledOnce();
            expect(sessionStorageSub).toHaveBeenCalledWith(LAST_READ_STORAGE_KEY);
            expect(notificationSidebarComponent.lastNotificationRead).toEqual(expectedDate);
        });

        it('should clear the session storage last notification read date if the user logs out', () => {
            const getAuthenticationStateStub = jest.spyOn(accountService, 'getAuthenticationState').mockReturnValue(of(undefined));
            const sessionStorageSub = jest.spyOn(sessionStorageService, 'clear');
            notificationSidebarComponent.ngOnInit();
            expect(getAuthenticationStateStub).toHaveBeenCalledOnce();
            expect(sessionStorageSub).toHaveBeenCalledOnce();
            expect(sessionStorageSub).toHaveBeenCalledWith(LAST_READ_STORAGE_KEY);
        });

        it('should subscribe to notification updates for user', () => {
            jest.spyOn(notificationService, 'subscribeToNotificationUpdates').mockReturnValue(of(notifications));
            jest.spyOn(notificationService, 'subscribeToTotalNotificationCountUpdates');
            jest.spyOn(notificationService, 'subscribeToLoadingStateUpdates');
            jest.spyOn(notificationSettingsService, 'isNotificationAllowedBySettings').mockReturnValue(true);
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.subscribeToNotificationUpdates).toHaveBeenCalledOnce();
            expect(notificationService.subscribeToTotalNotificationCountUpdates).toHaveBeenCalledOnce();
            expect(notificationService.subscribeToLoadingStateUpdates).toHaveBeenCalledOnce();
            expect(notificationSidebarComponent.sortedNotifications).toHaveLength(notifications.length);
        });

        /**
         * The notification-sidebar overlaps the exam summary when being printed,
         * so it needs to be hidden for printing for which the display property of
         * the notification sidebar is used
         *
         * If the id is changed it needs to be changed in {@link ThemeService#print} as well
         */
        test('should exist with the id "notification-sidebar"', () => {
            const notificationSidebar = document.getElementById('notification-sidebar');
            expect(notificationSidebar).toBeTruthy();
        });
    });

    describe('Notification Translations', () => {
        it('should get the notification title translation', () => {
            notificationSidebarComponent.ngOnInit();

            const notification = { id: 1, notificationDate: dayjs(), title: NEW_MESSAGE_TITLE } as Notification;
            const notificationTitle = notificationSidebarComponent.getNotificationTitleTranslation(notification);
            expect(notificationTitle).toEqual(artemisTranslatePipe.transform(NEW_MESSAGE_TITLE));
        });

        it('should be undefined if no title', () => {
            notificationSidebarComponent.ngOnInit();

            const notification = { id: 1, notificationDate: dayjs() } as Notification;
            const notificationTitle = notificationSidebarComponent.getNotificationTitleTranslation(notification);
            expect(notificationTitle).toBeUndefined();
        });

        it('should get the notification text translation', () => {
            notificationSidebarComponent.ngOnInit();

            const notification = { id: 1, notificationDate: dayjs(), text: 'test', textIsPlaceholder: true } as Notification;
            const notificationTextTranslation = notificationSidebarComponent.getNotificationTextTranslation(notification);
            expect(notificationTextTranslation).toEqual(artemisTranslatePipe.transform('test'));
        });

        it('should be undefined if no text', () => {
            notificationSidebarComponent.ngOnInit();

            const notification = { id: 1, notificationDate: dayjs(), textIsPlaceholder: true } as Notification;
            const notificationTextTranslation = notificationSidebarComponent.getNotificationTextTranslation(notification);
            expect(notificationTextTranslation).toBeUndefined();
        });
    });

    describe('Notification Texts', () => {
        it('should display the correct text for a notification with text', () => {
            // Set up the component with a notification that has text
            const notificationWithText = { id: 3, notificationDate: dayjs(), text: 'Test Notification' } as Notification;
            notificationSidebarComponent.sortedNotifications = [notificationWithText];

            // Trigger change detection to update the view
            notificationSidebarComponentFixture.detectChanges();

            // Find the notification element in the view
            const notificationElement = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-text');
            expect(notificationElement).not.toBeNull();
            // Expect the notification text to be displayed correctly
            expect(notificationElement?.textContent).toContain(notificationWithText.text);
        });

        it('should display No text found for a notification without text', () => {
            // Set up the component with a notification that does not have text
            const notificationWithoutText = { id: 4, notificationDate: dayjs() } as Notification;
            const notTextFoundText = 'No text found';
            notificationSidebarComponent.sortedNotifications = [notificationWithoutText];

            // Trigger change detection to update the view
            notificationSidebarComponentFixture.detectChanges();

            // Find the notification element in the view
            const notificationElement = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-text');
            expect(notificationElement).not.toBeNull();
            // Expect the notification text to be No text found if no text is found
            expect(notificationElement.textContent).toBe(notTextFoundText);
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
            subscribeToNotificationUpdatesStub.mockReturnValue(new BehaviorSubject([notificationNow]).asObservable() as ReplaySubject<Notification[]>);
        };

        it('should not add already existing notifications', () => {
            notificationSidebarComponent.sortedNotifications = [notificationNow];
            const queryNotificationsFilteredBySettingsStub = jest.spyOn(notificationService, 'subscribeToNotificationUpdates');
            queryNotificationsFilteredBySettingsStub.mockReturnValue(of([notificationNow]));
            notificationSidebarComponent.ngOnInit();
            expect(notificationSidebarComponent.sortedNotifications).toEqual([notificationNow]);
        });

        it('should update sorted notifications array after new notifications were loaded', () => {
            const queryNotificationsFilteredBySettingsStub = jest.spyOn(notificationService, 'subscribeToNotificationUpdates');
            queryNotificationsFilteredBySettingsStub.mockReturnValue(of([notificationPast, notificationNow]));
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.subscribeToNotificationUpdates).toHaveBeenCalledOnce();
            expect(notificationSidebarComponent.sortedNotifications).toEqual([notificationNow, notificationPast]);
        });

        it('should set total notification count to received X-Total-Count header', () => {
            const queryNotificationsFilteredBySettingsStub = jest.spyOn(notificationService, 'subscribeToTotalNotificationCountUpdates');
            queryNotificationsFilteredBySettingsStub.mockReturnValue(of(notifications.length));
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.subscribeToTotalNotificationCountUpdates).toHaveBeenCalledOnce();
            expect(notificationSidebarComponent.totalNotifications).toBe(notifications.length);
        });

        it('should not add already existing notification received via websocket', () => {
            notificationSidebarComponent.sortedNotifications = [notificationNow];
            notificationSidebarComponent.totalNotifications = 1;
            replaceSubscribeToNotificationUpdates();
            notificationSidebarComponent.ngOnInit();
            expect(notificationSidebarComponent.sortedNotifications).toHaveLength(1);
            expect(notificationSidebarComponent.totalNotifications).toBe(1);
        });

        it('should load more notifications only if not all are already loaded', () => {
            notificationSidebarComponent.sortedNotifications = notifications;
            notificationSidebarComponent.totalNotifications = 2;
            jest.spyOn(notificationService, 'queryNotificationsFilteredBySettings');
            notificationSidebarComponent.ngOnInit();
            expect(notificationService.queryNotificationsFilteredBySettings).not.toHaveBeenCalled();
        });
    });

    describe('Recent notifications', () => {
        it('should evaluate recent notifications correctly', () => {
            notificationSidebarComponent.lastNotificationRead = dayjs().subtract(1, 'day');
            const queryNotificationsFilteredBySettingsStub = jest.spyOn(notificationService, 'subscribeToNotificationUpdates');
            queryNotificationsFilteredBySettingsStub.mockReturnValue(of(notifications));

            notificationSidebarComponent.ngOnInit();
            expect(notificationService.subscribeToNotificationUpdates).toHaveBeenCalledOnce();
            expect(notificationSidebarComponent.recentNotificationCount).toBe(1);
        });

        it('should show plus sign in recent notification count badge if all loaded notifications are recent notifications', () => {
            notificationSidebarComponent.sortedNotifications = notifications;
            notificationSidebarComponent.recentNotificationCount = 2;
            notificationSidebarComponentFixture.detectChanges();
            const plus = notificationSidebarComponentFixture.debugElement.query(By.css('.bg-danger > span'));
            expect(plus).not.toBeNull();
        });
    });

    describe('UI', () => {
        it('should show no notifications message', () => {
            notificationSidebarComponent.sortedNotifications = [];
            notificationSidebarComponentFixture.detectChanges();
            const noNotificationsMessage = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.no-notifications');
            expect(noNotificationsMessage).not.toBeNull();
        });

        it('should show loading spinner when more notifications are loaded', () => {
            notificationSidebarComponent.loading = true;
            notificationSidebarComponentFixture.detectChanges();
            const loadingSpinner = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.loading-spinner');
            expect(loadingSpinner).not.toBeNull();
        });

        it('should show all notifications loaded message when all notifications are loaded', () => {
            notificationSidebarComponent.sortedNotifications = notifications;
            notificationSidebarComponent.totalNotifications = 1;
            notificationSidebarComponentFixture.detectChanges();
            const allLoadedMessage = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.all-loaded');
            expect(allLoadedMessage).not.toBeNull();
        });

        it('should show error message when loading of notifications failed', () => {
            notificationSidebarComponent.error = 'error';
            notificationSidebarComponentFixture.detectChanges();
            const errorMessage = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.alert-danger');
            expect(errorMessage).not.toBeNull();
        });

        it('should toggle which notifications are displayed (hide until property) when user clicks on archive button', () => {
            notificationSidebarComponent.ngOnInit();
            notificationSidebarComponent.sortedNotifications = notifications;
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

            // fake status before reloading the sidebar
            notificationSidebarComponent.sortedNotifications = notifications;
            notificationSidebarComponent.totalNotifications = notifications.length;
            const priorNumberOfNotifications = notifications.length;

            // reload the sidebar
            const subscribeToTotalNotificationCountUpdatesStub = jest.spyOn(notificationService, 'subscribeToTotalNotificationCountUpdates');
            subscribeToTotalNotificationCountUpdatesStub.mockReturnValue(of(0));
            notificationSidebarComponent.ngOnInit();

            // test the reloading behavior
            expect(userSettingsService.loadSettings).not.toHaveBeenCalled();
            expect(priorNumberOfNotifications).not.toBe(notificationSidebarComponent.totalNotifications);
        });
    });
});
