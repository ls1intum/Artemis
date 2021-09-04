import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SERVER_API_URL } from 'app/app.constants';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { OptionCore, UserSettings } from 'app/shared/user-settings/user-settings.model';
import { defaultNotificationSettings, NotificationOptionCore } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { notificationOptionCoresForTesting } from './notification-settings.service.spec';

chai.use(sinonChai);
const expect = chai.expect;

describe('User Settings Service', () => {
    // general & common
    let userSettingsService: UserSettingsService;
    let httpMock: HttpTestingController;
    let userSettingsCategory: UserSettingsCategory;
    let resultingUserSettings: UserSettings<OptionCore>;

    // notification settings specific
    const notificationSettingsResourceUrl = SERVER_API_URL + 'api/notification-settings';

    /**
     * Updates the NotificationOptionCores of the provided NotificationSettings by using provided Cores
     * @param settings to be updated
     * @param providedOptionCores are used to find matching cores and update them
     */
    function updateNotificationSettingsByProvidedNotificationOptionCores(settings: UserSettings<NotificationOptionCore>, providedOptionCores: NotificationOptionCore[]) {
        providedOptionCores.forEach((providedOptionCore) => {
            for (const group of settings.groups) {
                for (const option of group.options) {
                    if (option.optionCore.optionSpecifier === providedOptionCore.optionSpecifier) {
                        option.optionCore.webapp = providedOptionCore.webapp;
                        option.optionCore.email = providedOptionCore.email;
                        break;
                    }
                }
            }
        });
    }

    /**
     * Updates the original NotificationOptionCores by using provided Cores
     * @param originalOptionCores which should be updated
     * @param providedOptionCores which are used to update the original cores
     */
    function updateNotificationOptionCoresByProvidedNotificationOptionCores(originalOptionCores: NotificationOptionCore[], providedOptionCores: NotificationOptionCore[]) {
        providedOptionCores.forEach((providedCore) => {
            for (const originalCore of originalOptionCores) {
                if (originalCore.optionSpecifier === providedCore.optionSpecifier) {
                    originalCore.webapp = providedCore.webapp;
                    originalCore.email = providedCore.email;
                    break;
                }
            }
        });
    }

    /**
     * Checks if the provided (newly updated) cores are a subset of the expected cores
     * @param providedNotificationCores which should be part of the expected ones
     * @param expectedNotificationCores the bigger array of cores which should contain the provided cores
     */
    function checkIfProvidedNotificationCoresArePartOfExpectedCores(providedNotificationCores: NotificationOptionCore[], expectedNotificationCores: NotificationOptionCore[]) {
        providedNotificationCores.forEach((providedCore) => {
            for (const expectedNotificationCore of expectedNotificationCores) {
                if (providedCore.optionSpecifier === expectedNotificationCore.optionSpecifier) {
                    expect(providedCore.webapp).to.equal(expectedNotificationCore.webapp);
                    expect(providedCore.email).to.equal(expectedNotificationCore.email);
                    break;
                }
            }
        });
    }

    /**
     * extracts OptionCores from provided UserSettings
     * @param settings where the cores should be extracted from
     * @return extracted option cores
     */
    function extractOptionCoresFromSettings(settings: UserSettings<OptionCore>): OptionCore[] {
        const extractedOptionCores: OptionCore[] = [];
        settings.groups.forEach((group) => {
            group.options.forEach((option) => {
                extractedOptionCores.push(option.optionCore);
            });
        });
        return extractedOptionCores;
    }

    /**
     * Compares UserSettings correctly by first checking the settings for the same "signature/structure"
     * afterwards extracting the cores and comparing them
     * @param expectedSettings
     * @param resultSettings
     */
    function compareSettings(expectedSettings: UserSettings<OptionCore>, resultSettings: UserSettings<OptionCore>) {
        // this step alone is not enough due to the polymorphic nature of the optionCores
        expect(expectedSettings).to.deep.equal(resultSettings);
        const expectedOptionCores = extractOptionCoresFromSettings(expectedSettings);
        const resultOptionCores = extractOptionCoresFromSettings(resultSettings);
        expect(expectedOptionCores).to.deep.equal(resultOptionCores);
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
        sinon.restore();
    });

    describe('Service methods with Category Notification Settings', () => {
        userSettingsCategory = UserSettingsCategory.NOTIFICATION_SETTINGS;

        describe('test loading methods', () => {
            it('should call correct URL to fetch all option cores', () => {
                userSettingsService.loadUserOptions(userSettingsCategory).subscribe();
                const req = httpMock.expectOne({ method: 'GET' });
                const infoUrl = notificationSettingsResourceUrl + '/fetch-options';
                expect(req.request.url).to.equal(infoUrl);
            });

            it('should load correct default settings as foundation', () => {
                // to make sure the default settings are not modified
                resultingUserSettings = userSettingsService.loadUserOptionCoresSuccessAsSettings([], userSettingsCategory);
                compareSettings(defaultNotificationSettings, resultingUserSettings);
            });

            it('should correctly update and return settings based on received option cores', () => {
                const expectedUserSettings: UserSettings<NotificationOptionCore> = defaultNotificationSettings;
                updateNotificationSettingsByProvidedNotificationOptionCores(expectedUserSettings, notificationOptionCoresForTesting);
                resultingUserSettings = userSettingsService.loadUserOptionCoresSuccessAsSettings(notificationOptionCoresForTesting, userSettingsCategory);
                compareSettings(expectedUserSettings, resultingUserSettings);
            });

            it('should correctly update and return option cores based on received option cores', () => {
                const expectedNotificationOptionCores: NotificationOptionCore[] = userSettingsService.extractOptionCoresFromSettings(
                    defaultNotificationSettings,
                ) as NotificationOptionCore[];
                updateNotificationOptionCoresByProvidedNotificationOptionCores(expectedNotificationOptionCores, notificationOptionCoresForTesting);

                let resultingOptionCores: NotificationOptionCore[];
                resultingOptionCores = userSettingsService.loadUserOptionCoresSuccessAsOptionCores(
                    notificationOptionCoresForTesting,
                    userSettingsCategory,
                ) as NotificationOptionCore[];

                expect(resultingOptionCores.length).to.equal(expectedNotificationOptionCores.length);
                checkIfProvidedNotificationCoresArePartOfExpectedCores(resultingOptionCores, expectedNotificationOptionCores);
            });
        });

        describe('test saving methods', () => {
            it('should call correct URL to save option cores', () => {
                userSettingsService.saveUserOptions(notificationOptionCoresForTesting, userSettingsCategory).subscribe();
                const req = httpMock.expectOne({ method: 'POST' });
                const infoUrl = notificationSettingsResourceUrl + '/save-options';
                expect(req.request.url).to.equal(infoUrl);
            });

            it('server response should contain inputted options', fakeAsync(() => {
                userSettingsService.saveUserOptions(notificationOptionCoresForTesting, userSettingsCategory).subscribe((resp) => {
                    checkIfProvidedNotificationCoresArePartOfExpectedCores(resp.body as NotificationOptionCore[], notificationOptionCoresForTesting);
                });
                const req = httpMock.expectOne({ method: 'POST' });
                req.flush(notificationOptionCoresForTesting);
                tick();
            }));

            it('should correctly update and return settings based on received option cores', () => {
                const expectedUserSettings: UserSettings<NotificationOptionCore> = defaultNotificationSettings;
                updateNotificationSettingsByProvidedNotificationOptionCores(expectedUserSettings, notificationOptionCoresForTesting);
                resultingUserSettings = userSettingsService.saveUserOptionsSuccess(expectedUserSettings, notificationOptionCoresForTesting);
                compareSettings(expectedUserSettings, resultingUserSettings);
            });
        });
    });
});
