import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';
import { NotificationSettingsService } from 'app/shared/user-settings/notification-settings/notification-settings.service';
import { GroupNotification } from 'app/entities/group-notification.model';
import { ATTACHMENT_CHANGE_TITLE, COURSE_ARCHIVE_STARTED_TITLE, EXAM_ARCHIVE_STARTED_TITLE, EXERCISE_PRACTICE_TITLE, NotificationType } from 'app/entities/notification.model';
import { SettingId } from 'app/shared/constants/user-settings.constants';

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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        })
            .compileComponents()
            .then(() => {
                notificationSettingsService = TestBed.inject(NotificationSettingsService);
            });
    });

    describe('Service methods with Category Notification Settings', () => {
        describe('test is notification allowed by settings', () => {
            it('should correctly check if the given notification is allowed by the settings', () => {
                const notificationA: GroupNotification = {
                    title: ATTACHMENT_CHANGE_TITLE,
                    text: 'Notification Text',
                    notificationType: NotificationType.GROUP,
                };

                const notificationB: GroupNotification = {
                    title: EXAM_ARCHIVE_STARTED_TITLE,
                };

                const activationStatusOfA = false;
                const activationStatusOfB = true;

                const map: Map<string, boolean> = new Map<string, boolean>();
                // deactivated in notification settings
                map.set(notificationA.title!, activationStatusOfA);
                // activated in notification settings
                map.set(notificationB.title!, activationStatusOfB);

                const resultOfA = notificationSettingsService.isNotificationAllowedBySettings(notificationA, map);
                const resultOfB = notificationSettingsService.isNotificationAllowedBySettings(notificationB, map);

                expect(resultOfA).toEqual(activationStatusOfA);
                expect(resultOfB).toEqual(activationStatusOfB);
            });
        });

        it('should update notificationTitleActivationMap & map between NotificationType and Settings', () => {
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

            const resultMap = notificationSettingsService.createUpdatedNotificationTitleActivationMap(notificationSettingsForTesting);

            expect(resultMap.has(type1)).toBeTrue();
            expect(resultMap.has(type2)).toBeTrue();
            expect(resultMap.has(type3)).toBeTrue();

            expect(resultMap.size).toEqual(3);

            expect(resultMap.get(type1)).toEqual(type1ActivationStatus);
            expect(resultMap.get(type2)).toEqual(type2ActivationStatus);
            expect(resultMap.get(type3)).toEqual(type3ActivationStatus);
        });
    });
});
