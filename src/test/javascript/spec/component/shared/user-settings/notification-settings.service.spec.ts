import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { NotificationSettingsService, reloadNotificationSideBarMessage } from 'app/shared/user-settings/notification-settings/notification-settings.service';
import { COURSE_ARCHIVE_STARTED_TITLE, EXAM_ARCHIVE_STARTED_TITLE, EXERCISE_PRACTICE_TITLE } from 'app/entities/notification.model';
import { SettingId } from 'app/shared/constants/user-settings.constants';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Setting } from 'app/shared/user-settings/user-settings.model';

const notificationSettingA: NotificationSetting = {
    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
    webapp: false,
    email: false,
};
const notificationSettingB: NotificationSetting = {
    settingId: SettingId.NOTIFICATION__INSTRUCTOR_NOTIFICATION__COURSE_AND_EXAM_ARCHIVING_STARTED,
    webapp: true,
    email: false,
};

const notificationSettingsForTesting: NotificationSetting[] = [notificationSettingA, notificationSettingB];

describe('User Settings Service', () => {
    let notificationSettingsService: NotificationSettingsService;
    let userSettingsService: UserSettingsService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        })
            .compileComponents()
            .then(() => {
                notificationSettingsService = TestBed.inject(NotificationSettingsService);
                userSettingsService = TestBed.inject(UserSettingsService);
            });
    });

    it('should refresh settings after user settings changed', () => {
        userSettingsService.userSettingsChangeEvent.subscribe = jest.fn().mockImplementation((callback) => {
            callback(reloadNotificationSideBarMessage);
        });

        const spy = jest.spyOn(userSettingsService, 'loadSettings').mockReturnValue(of(new HttpResponse<Setting[]>({ body: notificationSettingsForTesting })));

        notificationSettingsService['listenForNotificationSettingsChanges']();
        expect(spy).toHaveBeenCalled();
    });

    it('should provide getters for notification settings and updates to it', () => {
        jest.spyOn(userSettingsService, 'loadSettings').mockReturnValue(of(new HttpResponse<Setting[]>({ body: notificationSettingsForTesting })));
        notificationSettingsService.refreshNotificationSettings();

        const settings = notificationSettingsService.getNotificationSettings();
        expect(settings).not.toBeEmpty();

        // Subscribing to the updates
        notificationSettingsService.getNotificationSettingsUpdates().subscribe((updatedSettings) => {
            expect(updatedSettings).toEqual(settings);
        });
    });

    it('create correct title activation map', () => {
        const type1 = EXERCISE_PRACTICE_TITLE;
        const type1ActivationStatus = notificationSettingA.webapp!;
        const type2 = EXAM_ARCHIVE_STARTED_TITLE;
        const type2ActivationStatus = notificationSettingB.webapp!;
        const type3 = COURSE_ARCHIVE_STARTED_TITLE;
        const type3ActivationStatus = notificationSettingB.webapp!;

        const expectedMap: Map<string, boolean> = new Map<string, boolean>();
        expectedMap.set(type1, type1ActivationStatus);
        expectedMap.set(type2, type2ActivationStatus);
        expectedMap.set(type3, type3ActivationStatus);

        notificationSettingsService['currentNotificationSettings'] = notificationSettingsForTesting;
        const resultMap = notificationSettingsService['createUpdatedNotificationTitleActivationMap']();
        expect(resultMap.has(type1)).toBeTrue();
        expect(resultMap.has(type2)).toBeTrue();
        expect(resultMap.has(type3)).toBeTrue();

        expect(resultMap.size).toBe(3);

        expect(resultMap.get(type1)).toEqual(type1ActivationStatus);
        expect(resultMap.get(type2)).toEqual(type2ActivationStatus);
        expect(resultMap.get(type3)).toEqual(type3ActivationStatus);
    });
});
