import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SERVER_API_URL } from 'app/app.constants';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { OptionSpecifier, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { OptionCore, UserSettings } from 'app/shared/user-settings/user-settings.model';
import { defaultNotificationSettings, NotificationOptionCore } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { User } from 'app/core/user/user.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('User Settings Service', () => {
    // general & common
    let userSettingsService: UserSettingsService;
    let httpMock: HttpTestingController;
    let userSettingsCategory: UserSettingsCategory;
    let applyNewChangesSource: Subject<string>;
    let receivedOptionCoresFromServer: OptionCore[];
    let resultingUserSettings: UserSettings<OptionCore>;
    const user = { id: 1, name: 'name', login: 'login' } as User;

    // notification settings specific
    const notificationSettingsResourceUrl = SERVER_API_URL + 'api/notification-settings';
    let notificationOptionCoreA: NotificationOptionCore = {
        id: 1,
        optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
        webapp: false,
        email: false,
    };
    let notificationOptionCoreB: NotificationOptionCore = {
        id: 2,
        optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES,
        webapp: true,
        email: false,
    };

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
                applyNewChangesSource = new Subject<string>();
            });
    });

    afterEach(() => {
        httpMock.verify();
        sinon.restore();
    });

    describe('Service methods with Category Notification Settings', () => {
        beforeAll(() => {
            userSettingsCategory = UserSettingsCategory.NOTIFICATION_SETTINGS;
            receivedOptionCoresFromServer = [notificationOptionCoreA, notificationOptionCoreB];
        });

        it('should call correct URL to fetch all option cores', () => {
            userSettingsService.loadUserOptions(userSettingsCategory).subscribe(() => {});
            const req = httpMock.expectOne({ method: 'GET' });
            const infoUrl = notificationSettingsResourceUrl + '/fetch-options';
            expect(req.request.url).to.equal(infoUrl);
        });

        describe('test loading methods', () => {
            it('should load correct default settings as foundation', () => {
                // to make sure the default settings are not modified
                resultingUserSettings = userSettingsService.loadUserOptionCoresSuccessAsSettings([], userSettingsCategory);
                expect(resultingUserSettings).to.deep.equal(defaultNotificationSettings);
            });

            function checkExpectedAndProvidedNotificationOptionCoresForEquality(
                expectedNotificationCores: NotificationOptionCore[],
                providedNotificationCores: NotificationOptionCore[],
            ) {}

            function checkExpectedAndProvidedNotificationOptionCoreForEquality(
                expectedNotificationCore: NotificationOptionCore,
                providedNotificationCore: NotificationOptionCore,
            ) {}
            /*
            function updateNotificationSettingsByProvidedNotificationOptionCores(
                settings : UserSettings<NotificationOptionCore>, optionCores : NotificationOptionCore[]) {
                settings.groups.find((group) => {
                    if(option)
                }
            }
 */

            it('should correctly update and return settings based on received option cores', () => {
                let expectedUserSettings: UserSettings<NotificationOptionCore> = defaultNotificationSettings;
                expectedUserSettings.groups.find((group) => {
                    group.options.find((option) => {
                        if (option.optionCore.optionSpecifier === notificationOptionCoreA.optionSpecifier) {
                            option.optionCore.webapp = notificationOptionCoreA.webapp;
                            option.optionCore.email = notificationOptionCoreA.email;
                        }

                        if (option.optionCore.optionSpecifier === notificationOptionCoreB.optionSpecifier) {
                            option.optionCore.webapp = notificationOptionCoreB.webapp;
                            option.optionCore.email = notificationOptionCoreB.email;
                        }
                    });
                });

                resultingUserSettings = userSettingsService.loadUserOptionCoresSuccessAsSettings(receivedOptionCoresFromServer, userSettingsCategory);
                expect(resultingUserSettings).to.deep.equal(defaultNotificationSettings);
            });

            it('should correctly update and return option cores based on received option cores', () => {
                let defaultOptionCores = userSettingsService.loadUserOptionCoresSuccessAsOptionCores([], userSettingsCategory);
                let numberOfDefaultOptionCores = defaultOptionCores.length;
                let resultingOptionCores: NotificationOptionCore[];
                resultingOptionCores = userSettingsService.loadUserOptionCoresSuccessAsOptionCores(receivedOptionCoresFromServer, userSettingsCategory) as NotificationOptionCore[];

                expect(resultingOptionCores.length).to.equal(numberOfDefaultOptionCores);

                resultingOptionCores.find((optionCore) => {
                    if (optionCore.optionSpecifier === notificationOptionCoreA.optionSpecifier) {
                        expect(optionCore.webapp).to.equal(notificationOptionCoreA.webapp);
                        expect(optionCore.email).to.equal(notificationOptionCoreA.email);
                    }
                    if (optionCore.optionSpecifier === notificationOptionCoreB.optionSpecifier) {
                        expect(optionCore.webapp).to.equal(notificationOptionCoreB.webapp);
                        expect(optionCore.email).to.equal(notificationOptionCoreB.email);
                    }
                });
            });
        });
    });
});
