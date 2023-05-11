import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateTestingModule } from '../helpers/mocks/service/mock-translate.service';
import { CONVERSATION_CREATE_GROUP_CHAT_TITLE, CONVERSATION_REMOVE_USER_GROUP_CHAT_TITLE, NEW_MESSAGE_TITLE, Notification } from 'app/entities/notification.model';
import { MockRouter } from '../helpers/mocks/mock-router';
import { RouterTestingModule } from '@angular/router/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockCourseManagementService } from '../helpers/mocks/service/mock-course-management.service';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import dayjs from 'dayjs/esm';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../helpers/mocks/service/mock-metis-service.service';
import { TutorialGroupsNotificationService } from 'app/course/tutorial-groups/services/tutorial-groups-notification.service';
import { MockProvider } from 'ng-mocks';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { CourseConversationsNotificationsService } from 'app/overview/course-conversations-notifications-service';
import { OneToOneChat } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { GroupChat } from 'app/entities/metis/conversation/group-chat.model';

describe('Notification Service', () => {
    let notificationService: NotificationService;
    let httpMock: HttpTestingController;
    let router: Router;

    let websocketService: JhiWebsocketService;
    let wsSubscribeStub: jest.SpyInstance;
    let wsUnSubscribeStub: jest.SpyInstance;
    let wsReceiveNotificationStub: jest.SpyInstance;
    let wsNotificationSubject: Subject<Notification | undefined>;
    let tutorialGroupNotificationService: TutorialGroupsNotificationService;
    let getTutorialGroupsForNotificationsSpy: jest.SpyInstance;
    let tutorialGroup: TutorialGroup;

    let conversationNotificationService: CourseConversationsNotificationsService;
    let getConversationsForNotificationsSpy: jest.SpyInstance;
    const conversation: OneToOneChat = new OneToOneChat();
    const groupChat: GroupChat = new GroupChat();
    conversation.id = 99;
    groupChat.id = 100;

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

    const generateConversationsNotification = () => {
        const generatedNotification = { title: NEW_MESSAGE_TITLE, text: 'This is a simple new message notification' } as Notification;
        generatedNotification.target = JSON.stringify({ message: 'new-message', entity: 'message', mainPage: 'courses', id: 10, course: course.id, conversation: conversation.id });
        generatedNotification.notificationDate = dayjs();
        return generatedNotification;
    };

    const conversationNotification = generateConversationsNotification();

    const generateConversationsCreationNotification = () => {
        const generatedNotification = { title: CONVERSATION_CREATE_GROUP_CHAT_TITLE, text: 'This is a simple new group chat notification' } as Notification;
        generatedNotification.target = JSON.stringify({
            message: 'conversation-creation',
            entity: 'conversation',
            mainPage: 'courses',
            id: 124,
            course: course.id,
            conversation: groupChat.id,
        });
        generatedNotification.notificationDate = dayjs();
        return generatedNotification;
    };

    const conversationCreationNotification = generateConversationsCreationNotification();

    const generateConversationsDeletionNotification = () => {
        const generatedNotification = { title: CONVERSATION_REMOVE_USER_GROUP_CHAT_TITLE, text: 'This is a simple group chat deletion notification' } as Notification;
        generatedNotification.target = JSON.stringify({
            message: 'conversation-deletion',
            entity: 'conversation',
            mainPage: 'courses',
            id: 124,
            course: course.id,
            conversation: groupChat.id,
        });
        generatedNotification.notificationDate = dayjs();
        return generatedNotification;
    };

    const conversationDeletionNotification = generateConversationsDeletionNotification();

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
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            firstChild: {
                                params: {
                                    courseId: 1,
                                },
                            },
                        },
                    },
                },
                MockProvider(TutorialGroupsNotificationService),
                MockProvider(CourseConversationsNotificationsService),
            ],
        })
            .compileComponents()
            .then(() => {
                notificationService = TestBed.inject(NotificationService);
                httpMock = TestBed.inject(HttpTestingController);
                router = TestBed.inject(Router);

                websocketService = TestBed.inject(JhiWebsocketService);
                wsSubscribeStub = jest.spyOn(websocketService, 'subscribe');
                wsUnSubscribeStub = jest.spyOn(websocketService, 'unsubscribe');
                wsNotificationSubject = new Subject<Notification | undefined>();
                wsReceiveNotificationStub = jest.spyOn(websocketService, 'receive').mockReturnValue(wsNotificationSubject);

                wsQuizExerciseSubject = new Subject<QuizExercise | undefined>();
                tutorialGroupNotificationService = TestBed.inject(TutorialGroupsNotificationService);

                tutorialGroup = new TutorialGroup();
                tutorialGroup.id = 99;
                getTutorialGroupsForNotificationsSpy = jest.spyOn(tutorialGroupNotificationService, 'getTutorialGroupsForNotifications').mockReturnValue(of([]));

                conversationNotificationService = TestBed.inject(CourseConversationsNotificationsService);
                getConversationsForNotificationsSpy = jest.spyOn(conversationNotificationService, 'getConversationsForNotifications').mockReturnValue(of([]));

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
            const url = 'api/notifications';
            expect(req.request.url).toBe(url);
        });

        it('should navigate to notification target', () => {
            jest.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));

            notificationService.interpretNotification(quizNotification);

            expect(router.navigate).toHaveBeenCalledOnce();
        });

        it('should navigate to new group chat notification target', () => {
            const navigateToNotificationTarget = jest.spyOn(notificationService, 'navigateToNotificationTarget');
            jest.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));
            notificationService.interpretNotification(conversationCreationNotification);
            expect(router.navigate).toHaveBeenCalledOnce();
            expect(navigateToNotificationTarget).toHaveBeenCalledOnce();
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

        it('should subscribe to newly created conversation and receive new single user notification', fakeAsync(() => {
            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).toEqual(conversationCreationNotification);
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
            // calls addNotificationToObserver i.e. calls next on subscribeToNotificationUpdates' ReplaySubject
            wsNotificationSubject.next(conversationCreationNotification);

            // websocket subscribe and receive called again for the new conversation
            const conversationId = groupChat.id;
            const conversationNotificationTopic = `/topic/conversation/${conversationId}/notifications`;
            expect(wsSubscribeStub).toHaveBeenCalledTimes(2);
            expect(wsSubscribeStub).toHaveBeenCalledWith(conversationNotificationTopic);
            expect(wsReceiveNotificationStub).toHaveBeenCalledTimes(2);
        }));

        it('should unsubscribe from deleted conversation', fakeAsync(() => {
            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).toEqual(conversationDeletionNotification);
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
            // calls addNotificationToObserver i.e. calls next on subscribeToNotificationUpdates' ReplaySubject
            wsNotificationSubject.next(conversationDeletionNotification);

            // websocket unsubscribe called for the deleted conversation
            expect(wsUnSubscribeStub).toHaveBeenCalledOnce();
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

        it('should subscribe to conversation notification updates and receive new message notifications', fakeAsync(() => {
            getConversationsForNotificationsSpy = jest.spyOn(conversationNotificationService, 'getConversationsForNotifications').mockReturnValue(of([conversation]));

            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).toEqual(conversationNotification);
            });
            expect(getConversationsForNotificationsSpy).toHaveBeenCalledOnce();
            const notificationTopic = `/topic/conversation/${tutorialGroup.id}/notifications`;
            expect(wsSubscribeStub).toHaveBeenCalledOnce();
            expect(wsSubscribeStub).toHaveBeenCalledWith(notificationTopic);
            expect(wsReceiveNotificationStub).toHaveBeenCalledOnce();
            wsNotificationSubject.next(conversationNotification);
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
