import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TutorialGroupsNotificationService } from 'app/course/tutorial-groups/services/tutorial-groups-notification.service';
import { Course } from 'app/entities/course.model';
import { Notification } from 'app/entities/notification.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { MockRouter } from '../helpers/mocks/mock-router';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { MockCourseManagementService } from '../helpers/mocks/service/mock-course-management.service';
import { MockMetisService } from '../helpers/mocks/service/mock-metis-service.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateTestingModule } from '../helpers/mocks/service/mock-translate.service';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';

describe('Notification Service', () => {
    let notificationService: NotificationService;
    let httpMock: HttpTestingController;
    let router: Router;

    let websocketService: JhiWebsocketService;
    let wsSubscribeStub: jest.SpyInstance;
    let wsReceiveNotificationStub: jest.SpyInstance;
    let wsNotificationSubject: Subject<Notification | undefined>;
    let tutorialGroupNotificationService: TutorialGroupsNotificationService;
    let getTutorialGroupsForNotificationsSpy: jest.SpyInstance;
    let tutorialGroup: TutorialGroup;

    let wsQuizExerciseSubject: Subject<QuizExercise | undefined>;

    let courseManagementService: CourseManagementService;
    let cmGetCoursesForNotificationsStub: jest.SpyInstance;
    let cmCoursesSubject: Subject<[Course] | undefined>;
    const course: Course = new Course();
    course.id = 42;

    const quizExercise: QuizExercise = { course, title: 'test quiz', quizStarted: true, visibleToStudents: true, id: 27 } as QuizExercise;

    const generateQuizNotification = () => {
        const generatedNotification = {
            title: 'Quiz started',
            text: 'Quiz "' + quizExercise.title + '" just started.',
            notificationDate: dayjs(),
        } as Notification;
        generatedNotification.target = JSON.stringify({ course: course.id, mainPage: 'courses', entity: 'exercises', id: quizExercise.id });
        return generatedNotification;
    };
    const quizNotification = generateQuizNotification();

    const generateSingleUserNotification = () => {
        const generatedNotification = { title: 'Single user notification', text: 'This is a notification for a single user' } as Notification;
        generatedNotification.notificationDate = dayjs().subtract(3, 'days');
        return generatedNotification;
    };
    const singleUserNotification = generateSingleUserNotification();

    const generateGroupNotification = () => {
        const generatedNotification = { title: 'simple group notification', text: 'This is a  simple group notification' } as Notification;
        generatedNotification.notificationDate = dayjs();
        return generatedNotification;
    };
    const groupNotification = generateGroupNotification();

    const generateTutorialGroupNotification = () => {
        const generatedNotification = { title: 'tutorial group notification', text: 'This is a simple tutorial group notification' } as Notification;
        generatedNotification.notificationDate = dayjs();
        return generatedNotification;
    };

    const tutorialGroupNotification = generateTutorialGroupNotification();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, TranslateTestingModule, RouterTestingModule.withRoutes([])],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: Router, useClass: MockRouter },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: MetisService, useClass: MockMetisService },
                MockProvider(TutorialGroupsNotificationService),
            ],
        })
            .compileComponents()
            .then(() => {
                notificationService = TestBed.inject(NotificationService);
                httpMock = TestBed.inject(HttpTestingController);
                router = TestBed.inject(Router);

                websocketService = TestBed.inject(JhiWebsocketService);
                wsSubscribeStub = jest.spyOn(websocketService, 'subscribe');
                wsNotificationSubject = new Subject<Notification | undefined>();
                wsReceiveNotificationStub = jest.spyOn(websocketService, 'receive').mockReturnValue(wsNotificationSubject);

                wsQuizExerciseSubject = new Subject<QuizExercise | undefined>();
                tutorialGroupNotificationService = TestBed.inject(TutorialGroupsNotificationService);

                tutorialGroup = new TutorialGroup();
                tutorialGroup.id = 99;
                getTutorialGroupsForNotificationsSpy = jest.spyOn(tutorialGroupNotificationService, 'getTutorialGroupsForNotifications').mockReturnValue(of([]));

                courseManagementService = TestBed.inject(CourseManagementService);
                cmCoursesSubject = new Subject<[Course] | undefined>();
                cmGetCoursesForNotificationsStub = jest
                    .spyOn(courseManagementService, 'getCoursesForNotifications')
                    .mockReturnValue(cmCoursesSubject as BehaviorSubject<[Course] | undefined>);
            });
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    describe('Service methods', () => {
        it('should call correct URL to fetch all notifications filtered by current notification settings', () => {
            notificationService.queryNotificationsFilteredBySettings().subscribe(() => {});
            const req = httpMock.expectOne({ method: 'GET' });
            const url = SERVER_API_URL + 'api/notifications';
            expect(req.request.url).toBe(url);
        });

        it('should navigate to notification target', () => {
            jest.spyOn(router, 'navigate').mockImplementation();

            notificationService.interpretNotification(quizNotification);

            expect(router.navigate).toHaveBeenCalledOnce();
        });

        it('should convert date array from server', fakeAsync(() => {
            // strange method, because notificationDate can only be of type Dayjs, I can not simulate an input with string for date
            const notificationArray = [singleUserNotification, quizNotification];
            const serverResponse = notificationArray;
            const expectedResult = notificationArray.sort();

            notificationService.queryNotificationsFilteredBySettings().subscribe((resp) => {
                expect(resp.body).toEqual(expectedResult);
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(serverResponse);
            tick();
        }));

        it('should subscribe to single user notification updates and receive new single user notification', fakeAsync(() => {
            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).toEqual(singleUserNotification);
            });

            tick(); // position of tick is very important here !

            const userId = 99; // based on MockAccountService
            const notificationTopic = `/topic/user/${userId}/notifications`;
            expect(wsSubscribeStub).toHaveBeenCalledOnce();
            expect(wsSubscribeStub).toHaveBeenCalledWith(notificationTopic);
            // websocket correctly subscribed to the topic

            expect(wsReceiveNotificationStub).toHaveBeenCalledOnce();
            // websocket "receive" called

            // add new single user notification
            wsNotificationSubject.next(singleUserNotification);
            // calls addNotificationToObserver i.e. calls next on subscribeToNotificationUpdates' ReplaySubject
        }));

        it('should subscribe to tutorial group notification updates and receive new tutorial group notifications', fakeAsync(() => {
            getTutorialGroupsForNotificationsSpy = jest.spyOn(tutorialGroupNotificationService, 'getTutorialGroupsForNotifications').mockReturnValue(of([tutorialGroup]));

            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).toEqual(tutorialGroupNotification);
            });
            expect(getTutorialGroupsForNotificationsSpy).toHaveBeenCalledOnce();
            const notificationTopic = `/topic/tutorial-group/${tutorialGroup.id}/notifications`;
            expect(wsSubscribeStub).toHaveBeenCalledOnce();
            expect(wsSubscribeStub).toHaveBeenCalledWith(notificationTopic);
            expect(wsReceiveNotificationStub).toHaveBeenCalledOnce();
            wsNotificationSubject.next(tutorialGroupNotification);
        }));

        it('should subscribe to group notification updates and receive new group notification', fakeAsync(() => {
            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).toEqual(groupNotification);
            });

            tick(); // position of tick is very important here !

            expect(cmGetCoursesForNotificationsStub).toHaveBeenCalledOnce();
            // courseManagementService.getCoursesForNotifications had been successfully subscribed to

            // push new courses
            cmCoursesSubject.next([course]);

            const notificationTopic = `/topic/course/${course.id}/TA`;
            expect(wsSubscribeStub).toHaveBeenCalledTimes(3);
            expect(wsSubscribeStub).toHaveBeenCalledWith(notificationTopic);
            // websocket correctly subscribed to the topic

            expect(wsReceiveNotificationStub).toHaveBeenCalledTimes(3);
            // websocket "receive" called

            // add new single user notification
            wsNotificationSubject.next(groupNotification);
            // calls addNotificationToObserver i.e. calls next on subscribeToNotificationUpdates' ReplaySubject
        }));

        it('should subscribe to quiz notification updates and receive a new quiz exercise and create a new quiz notification from it', fakeAsync(() => {
            const wsReceiveQuizExerciseStub = jest.spyOn(websocketService, 'receive').mockReturnValue(wsQuizExerciseSubject);

            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                // the quiz notification is created after a new quiz exercise has been detected, therefore the time will always be different
                notification.notificationDate = undefined;
                quizNotification.notificationDate = undefined;

                expect(notification).toEqual(quizNotification);
            });

            tick(); // position of tick is very important here !

            expect(cmGetCoursesForNotificationsStub).toHaveBeenCalledOnce();
            // courseManagementService.getCoursesForNotifications had been successfully subscribed to

            // push new courses
            cmCoursesSubject.next([course]);

            const notificationTopic = `/topic/course/${course.id}/TA`;
            expect(wsSubscribeStub).toHaveBeenCalledTimes(3);
            expect(wsSubscribeStub).toHaveBeenCalledWith(notificationTopic);
            // websocket correctly subscribed to the topic

            expect(wsReceiveQuizExerciseStub).toHaveBeenCalledTimes(3);
            // websocket "receive" called

            // pushes new quizExercise
            wsQuizExerciseSubject.next(quizExercise);
            // calls addNotificationToObserver i.e. calls next on subscribeToNotificationUpdates' ReplaySubject
            tick();
        }));
    });
});
