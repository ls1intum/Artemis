import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SERVER_API_URL } from 'app/app.constants';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Notification } from 'app/entities/notification.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { BehaviorSubject, Subject } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import * as moment from 'moment';
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
    let userSettingsService: UserSettingsService;
    let httpMock: HttpTestingController;
    let router: Router;

    let userSettingsCategory: UserSettingsCategory;
    //let notificationSettingsCategory: UserSettingsCategory = UserSettingsCategory.NOTIFICATION_SETTINGS;
    let notificationSettingsResourceUrl = SERVER_API_URL + 'api/notification-settings';

    let websocketService: JhiWebsocketService;
    let wsSubscribeStub: SinonStub;
    let wsReceiveNotificationStub: SinonStub;
    let wsNotificationSubject: Subject<Notification | undefined>;

    let wsQuizExerciseSubject: Subject<QuizExercise | undefined>;
    let wsReceiveQuizExerciseStub: SinonStub;

    let courseManagementService: CourseManagementService;
    let cmGetCoursesForNotificationsStub: SinonStub;
    let cmCoursesSubject: Subject<[Course] | undefined>;
    const course: Course = new Course();
    course.id = 42;

    const quizExercise: QuizExercise = { course, title: 'test quiz', started: true, visibleToStudents: true, id: 27 } as QuizExercise;

    const generateQuizNotification = () => {
        const generatedNotification = {
            title: 'Quiz started',
            text: 'Quiz "' + quizExercise.title + '" just started.',
            notificationDate: moment(),
        } as Notification;
        generatedNotification.target = JSON.stringify({ course: course.id, mainPage: 'courses', entity: 'exercises', id: quizExercise.id });
        return generatedNotification;
    };
    const quizNotification = generateQuizNotification();

    const generateSingleUserNotification = () => {
        const generatedNotification = { title: 'Single user notification', text: 'This is a notification for a single user' } as Notification;
        generatedNotification.notificationDate = moment().subtract(3, 'days');
        return generatedNotification;
    };
    const singleUserNotification = generateSingleUserNotification();

    const generateGroupNotification = () => {
        const generatedNotification = { title: 'simple group notification', text: 'This is a  simple group notification' } as Notification;
        generatedNotification.notificationDate = moment();
        return generatedNotification;
    };
    const groupNotification = generateGroupNotification();

    beforeEach(() => {
        /*
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, TranslateTestingModule, RouterTestingModule.withRoutes([])],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: Router, useClass: MockRouter },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
            ],
        })
         */
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

                wsQuizExerciseSubject = new Subject<QuizExercise | undefined>();

                courseManagementService = TestBed.inject(CourseManagementService);
                cmCoursesSubject = new Subject<[Course] | undefined>();
                cmGetCoursesForNotificationsStub = stub(courseManagementService, 'getCoursesForNotifications').returns(cmCoursesSubject as BehaviorSubject<[Course] | undefined>);
            });
    });

    afterEach(() => {
        httpMock.verify();
        sinon.restore();
    });

    const user1 = { id: 1, name: 'name', login: 'login' } as User;

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

    let receivedOptionCoresFromServer: OptionCore[] = [notificationOptionCoreA, notificationOptionCoreB];

    describe('Service methods with Category Notification Settings', () => {
        beforeAll(() => {
            userSettingsCategory = UserSettingsCategory.NOTIFICATION_SETTINGS;
        });

        it('should call correct URL to fetch all option cores', () => {
            userSettingsService.loadUserOptions(userSettingsCategory).subscribe(() => {});
            const req = httpMock.expectOne({ method: 'GET' });
            const infoUrl = notificationSettingsResourceUrl + '/fetch-options';
            expect(req.request.url).to.equal(infoUrl);
        });

        let resultingUserSettings: UserSettings<OptionCore>;

        describe('test loading methods', () => {
            it('should load correct default settings as foundation', () => {
                // to make sure the default settings are not modified
                resultingUserSettings = userSettingsService.loadUserOptionCoresSuccessAsSettings([], userSettingsCategory);
                expect(resultingUserSettings).to.deep.equal(defaultNotificationSettings);
            });

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
