import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { SettingId, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { Setting, UserSettingsStructure } from 'app/shared/user-settings/user-settings.model';
import { notificationSettingsStructure, NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';

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
    // general & common
    let userSettingsService: UserSettingsService;
    let httpMock: HttpTestingController;
    let userSettingsCategory: UserSettingsCategory;
    let resultingUserSettings: UserSettingsStructure<Setting>;

    // notification settings specific
    const notificationSettingsResourceUrl = SERVER_API_URL + 'api/notification-settings';

    /**
     * Updates the NotificationSettings of the provided NotificationSettings by using provided Settings
     * @param settingsStructure to be updated
     * @param providedSettings are used to find matching settings and update them
     */
    function updateNotificationSettingsStructureByProvidedNotificationSettings(
        settingsStructure: UserSettingsStructure<NotificationSetting>,
        providedSettings: NotificationSetting[],
    ) {
        providedSettings.forEach((providedSetting) => {
            for (const group of settingsStructure.groups) {
                for (const setting of group.settings) {
                    if (setting.settingId === providedSetting.settingId) {
                        setting.webapp = providedSetting.webapp;
                        setting.email = providedSetting.email;
                        break;
                    }
                }
            }
        });
    }

    /**
     * Updates the original NotificationSettings by using provided Settings
     * @param originalSettings which should be updated
     * @param providedSettings which are used to update the settings
     */
    function updateNotificationSettings(originalSettings: NotificationSetting[], providedSettings: NotificationSetting[]) {
        providedSettings.forEach((providedSetting) => {
            for (const originalSetting of originalSettings) {
                if (originalSetting.settingId === providedSetting.settingId) {
                    originalSetting.webapp = providedSetting.webapp;
                    originalSetting.email = providedSetting.email;
                    break;
                }
            }
        });
    }

    /**
     * Checks if the provided (newly updated) settings are a subset of the expected settings
     * @param providedNotificationSettings which should be part of the expected ones
     * @param expectedNotificationSettings the array of settings which should contain the provided settings
     */
    function checkIfProvidedNotificationSettingsArePartOfExpectedSettings(
        providedNotificationSettings: NotificationSetting[],
        expectedNotificationSettings: NotificationSetting[],
    ) {
        providedNotificationSettings.forEach((providedSetting) => {
            for (const expectedNotificationSetting of expectedNotificationSettings) {
                if (providedSetting.settingId === expectedNotificationSetting.settingId) {
                    expect(providedSetting.webapp).toEqual(expectedNotificationSetting.webapp);
                    expect(providedSetting.email).toEqual(expectedNotificationSetting.email);
                    break;
                }
            }
        });
    }

    /**
     * extracts individual settings from provided settingsStructure
     * @param settingsStructure where the settings should be extracted from
     * @return extracted settingsStructure
     */
    function extractSettingsFromSettingsStructure(settingsStructure: UserSettingsStructure<Setting>): Setting[] {
        const extractedSettings: Setting[] = [];
        settingsStructure.groups.forEach((group) => {
            group.settings.forEach((setting) => {
                extractedSettings.push(setting);
            });
        });
        return extractedSettings;
    }

    /**
     * Compares settings structures correctly by first checking for the same "signature/structure"
     * afterwards extracting the individual settings and comparing them
     * @param expectedSettingsStructure
     * @param resultingSettingsStructure
     */
    function compareSettingsStructure(expectedSettingsStructure: UserSettingsStructure<Setting>, resultingSettingsStructure: UserSettingsStructure<Setting>) {
        // this step alone is not enough due to the polymorphic nature of the settings
        expect(expectedSettingsStructure).toEqual(resultingSettingsStructure);
        const expectedIndividualSettings = extractSettingsFromSettingsStructure(expectedSettingsStructure);
        const resultingIndividualSettings = extractSettingsFromSettingsStructure(resultingSettingsStructure);
        expect(expectedIndividualSettings).toEqual(resultingIndividualSettings);
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, TranslateTestingModule],
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                userSettingsService = TestBed.inject(UserSettingsService);
                httpMock = TestBed.inject(HttpTestingController);
            });
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods with Category Notification Settings', () => {
        userSettingsCategory = UserSettingsCategory.NOTIFICATION_SETTINGS;

        describe('test loading methods', () => {
            it('should call correct URL to fetch all settings', () => {
                userSettingsService.loadSettings(userSettingsCategory).subscribe();
                const req = httpMock.expectOne({ method: 'GET' });
                expect(req.request.url).toEqual(notificationSettingsResourceUrl);
            });

            it('should load correct default settings as foundation', () => {
                // to make sure the default settings are not modified
                resultingUserSettings = userSettingsService.loadSettingsSuccessAsSettingsStructure([], userSettingsCategory);
                compareSettingsStructure(notificationSettingsStructure, resultingUserSettings);
            });

            it('should correctly update and return settings based on received settings', () => {
                const expectedUserSettings: UserSettingsStructure<NotificationSetting> = notificationSettingsStructure;
                updateNotificationSettingsStructureByProvidedNotificationSettings(expectedUserSettings, notificationSettingsForTesting);
                resultingUserSettings = userSettingsService.loadSettingsSuccessAsSettingsStructure(notificationSettingsForTesting, userSettingsCategory);
                compareSettingsStructure(expectedUserSettings, resultingUserSettings);
            });

            it('should correctly update and return settings based on extracted settings', () => {
                const expectedNotificationSettings: NotificationSetting[] = userSettingsService.extractIndividualSettingsFromSettingsStructure(
                    notificationSettingsStructure,
                ) as NotificationSetting[];
                updateNotificationSettings(expectedNotificationSettings, notificationSettingsForTesting);

                let resultingSettings: NotificationSetting[];
                resultingSettings = userSettingsService.loadSettingsSuccessAsIndividualSettings(notificationSettingsForTesting, userSettingsCategory) as NotificationSetting[];

                expect(resultingSettings.length).toEqual(expectedNotificationSettings.length);
                checkIfProvidedNotificationSettingsArePartOfExpectedSettings(resultingSettings, expectedNotificationSettings);
            });
        });

        describe('test saving methods', () => {
            it('should call correct URL to save settings', () => {
                userSettingsService.saveSettings(notificationSettingsForTesting, userSettingsCategory).subscribe();
                const req = httpMock.expectOne({ method: 'PUT' });
                expect(req.request.url).toEqual(notificationSettingsResourceUrl);
            });

            it('should correctly update and return settings based on received settings', () => {
                const expectedUserSettings: UserSettingsStructure<NotificationSetting> = notificationSettingsStructure;
                updateNotificationSettingsStructureByProvidedNotificationSettings(expectedUserSettings, notificationSettingsForTesting);
                resultingUserSettings = userSettingsService.saveSettingsSuccess(expectedUserSettings, notificationSettingsForTesting);
                compareSettingsStructure(expectedUserSettings, resultingUserSettings);
            });

            it('server response should contain inputted settings', fakeAsync(() => {
                userSettingsService.saveSettings(notificationSettingsForTesting, userSettingsCategory).subscribe((resp) => {
                    checkIfProvidedNotificationSettingsArePartOfExpectedSettings(resp.body as NotificationSetting[], notificationSettingsForTesting);
                });
                const req = httpMock.expectOne({ method: 'PUT' });
                req.flush(notificationSettingsForTesting);
                tick();
            }));
        });
    });
});
