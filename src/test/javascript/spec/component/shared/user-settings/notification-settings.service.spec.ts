import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { NotificationOptionCore } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { NotificationSettingsService } from 'app/shared/user-settings/notification-settings/notification-settings.service';
import { GroupNotification } from 'app/entities/group-notification.model';
import { NotificationType, OriginalNotificationType } from 'app/entities/notification.model';
import { OptionSpecifier } from 'app/shared/constants/user-settings.constants';

export const notificationOptionCoreA: NotificationOptionCore = {
    id: 1,
    optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
    webapp: false,
    email: false,
};
export const notificationOptionCoreB: NotificationOptionCore = {
    id: 2,
    optionSpecifier: OptionSpecifier.NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED,
    webapp: true,
    email: false,
};

export const notificationOptionCoresForTesting: NotificationOptionCore[] = [notificationOptionCoreA, notificationOptionCoreB];

chai.use(sinonChai);
const expect = chai.expect;

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
                    title: 'Notification Title',
                    text: 'Notification Text',
                    notificationType: NotificationType.GROUP,
                    originalNotificationType: OriginalNotificationType.ATTACHMENT_CHANGE,
                };

                const notificationB: GroupNotification = {
                    originalNotificationType: OriginalNotificationType.EXAM_ARCHIVE_STARTED,
                };

                const activationStatusOfA = false;
                const activationStatusOfB = true;

                const map: Map<OriginalNotificationType, boolean> = new Map<OriginalNotificationType, boolean>();
                // deactivated in notification settings
                map.set(notificationA.originalNotificationType!, activationStatusOfA);
                // activated in notification settings
                map.set(notificationB.originalNotificationType!, activationStatusOfB);

                const resultOfA = notificationSettingsService.isNotificationAllowedBySettings(notificationA, map);
                const resultOfB = notificationSettingsService.isNotificationAllowedBySettings(notificationB, map);

                expect(resultOfA).to.equal(activationStatusOfA);
                expect(resultOfB).to.equal(activationStatusOfB);
            });
        });

        it('should update OriginalNotificationTypeActivationMap & map between NotificationType and OptionCore', () => {
            /*
            const notificationOptionCoreA: NotificationOptionCore = {
                id: 1,
                optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
                webapp: false,
                email: false,
            };
            const notificationOptionCoreB: NotificationOptionCore = {
                id: 2,
                optionSpecifier: OptionSpecifier.NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED,
                webapp: true,
                email: false,
            };
             */

            const notificationOptionCoresForTesting: NotificationOptionCore[] = [notificationOptionCoreA, notificationOptionCoreB];

            const type1 = OriginalNotificationType.EXERCISE_PRACTICE;
            const type1ActivationStatus = notificationOptionCoreA.webapp;
            const type2 = OriginalNotificationType.EXAM_ARCHIVE_STARTED;
            const type2ActivationStatus = notificationOptionCoreB.webapp;
            const type3 = OriginalNotificationType.COURSE_ARCHIVE_STARTED;
            const type3ActivationStatus = notificationOptionCoreB.webapp;

            const expectedMap: Map<OriginalNotificationType, boolean> = new Map<OriginalNotificationType, boolean>();
            expectedMap.set(type1, type1ActivationStatus);
            expectedMap.set(type2, type2ActivationStatus);
            expectedMap.set(type3, type3ActivationStatus);

            const resultMap = notificationSettingsService.createUpdatedOriginalNotificationTypeActivationMap(notificationOptionCoresForTesting);

            expect(resultMap.has(type1)).to.be.true;
            expect(resultMap.has(type2)).to.be.true;
            expect(resultMap.has(type3)).to.be.true;

            expect(resultMap.size).to.be.equal(3);

            expect(resultMap.get(type1)).to.be.equal(type1ActivationStatus);
            expect(resultMap.get(type2)).to.be.equal(type2ActivationStatus);
            expect(resultMap.get(type3)).to.be.equal(type3ActivationStatus);
        });
    });
});
