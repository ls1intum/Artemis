import { HttpClientTestingModule, HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateTestingModule } from '../helpers/mocks/service/mock-translate.service';
import {
    CONVERSATION_CREATE_GROUP_CHAT_TITLE,
    DATA_EXPORT_CREATED_TITLE,
    DATA_EXPORT_FAILED_TITLE,
    MENTIONED_IN_MESSAGE_TITLE,
    MESSAGE_REPLY_IN_CONVERSATION_TEXT,
    NEW_MESSAGE_TITLE,
    NEW_REPLY_FOR_EXAM_POST_TITLE,
    Notification,
} from 'app/entities/notification.model';
import { MockRouter } from '../helpers/mocks/mock-router';
import { RouterTestingModule } from '@angular/router/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { BehaviorSubject, Subject } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import dayjs from 'dayjs/esm';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../helpers/mocks/service/mock-metis-service.service';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { OneToOneChat } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { GroupChat } from 'app/entities/metis/conversation/group-chat.model';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChangeDetectorRef } from '@angular/core';
import { MetisPostDTO } from 'app/entities/metis/metis-post-dto.model';
import { MetisPostAction } from 'app/shared/metis/metis.util';
import { Post } from 'app/entities/metis/post.model';
import { User } from 'app/core/user/user.model';
import { ConversationType } from 'app/entities/metis/conversation/conversation.model';
import { Channel } from 'app/entities/metis/conversation/channel.model';

describe('Notification Service', () => {
    const resourceUrl = 'api/notifications';

    let notificationService: NotificationService;
    let httpMock: HttpTestingController;
    let router: MockRouter;
    let artemisTranslatePipe: ArtemisTranslatePipe;
    let accountService: MockAccountService;

    let websocketService: JhiWebsocketService;
    let wsSubscribeStub: jest.SpyInstance;
    let wsUnsubscribeStub: jest.SpyInstance;
    let wsReceiveNotificationStub: jest.SpyInstance;
    let wsNotificationSubject: Subject<Notification | undefined>;
    let wsPostDTOSubject: Subject<MetisPostDTO | undefined>;
    let tutorialGroup: TutorialGroup;
    let mutedConversationRequest: TestRequest;
    const conversation: OneToOneChat = new OneToOneChat();
    const groupChat: GroupChat = new GroupChat();
    conversation.id = 99;
    groupChat.id = 100;

    let wsQuizExerciseSubject: Subject<QuizExercise | undefined>;

    const course: Course = new Course();
    course.id = 42;
    course.isAtLeastTutor = true;

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

    const generateConversationReplyNotification = () => {
        const generatedNotification = { title: NEW_REPLY_FOR_EXAM_POST_TITLE, text: 'This is a simple new reply message notification' } as Notification;
        generatedNotification.target = JSON.stringify({ message: 'new-message', entity: 'message', mainPage: 'courses', id: 10, course: course.id, conversation: conversation.id });
        generatedNotification.notificationDate = dayjs();
        return generatedNotification;
    };

    const generateDataExportCreationSuccessNotification = () => {
        const generatedNotification = { title: DATA_EXPORT_CREATED_TITLE, text: 'Data export successfully created' } as Notification;
        generatedNotification.target = JSON.stringify({ entity: 'data-exports', mainPage: 'privacy', id: 1 });
        generatedNotification.notificationDate = dayjs();
        return generatedNotification;
    };
    const generateDataExportCreationFailureNotification = () => {
        const generatedNotification = { title: DATA_EXPORT_FAILED_TITLE, text: 'Data export creation failed' } as Notification;
        generatedNotification.target = JSON.stringify({ entity: 'data-exports', mainPage: 'privacy' });
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

    beforeAll(() => {
        jest.useFakeTimers();
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, TranslateTestingModule, RouterTestingModule.withRoutes([])],
            declarations: [MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: Router, useClass: MockRouter },
                MockProvider(CourseManagementService, {
                    getCoursesForNotifications: () => {
                        return new BehaviorSubject<Course[] | undefined>([course]);
                    },
                }),
                { provide: AccountService, useClass: MockAccountService },
                { provide: ArtemisTranslatePipe, useClass: ArtemisTranslatePipe },
                { provide: ChangeDetectorRef, useValue: {} },
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
            ],
        });

        httpMock = TestBed.inject(HttpTestingController);
        router = TestBed.inject(Router) as any;

        websocketService = TestBed.inject(JhiWebsocketService);
        artemisTranslatePipe = TestBed.inject(ArtemisTranslatePipe);
        accountService = TestBed.inject(AccountService) as any;
        wsSubscribeStub = jest.spyOn(websocketService, 'subscribe');
        wsUnsubscribeStub = jest.spyOn(websocketService, 'unsubscribe');
        wsNotificationSubject = new Subject<Notification | undefined>();
        wsPostDTOSubject = new Subject<MetisPostDTO | undefined>();
        wsReceiveNotificationStub = jest
            .spyOn(websocketService, 'receive')
            .mockImplementation((topic) => (topic.includes('notifications/conversations') ? wsPostDTOSubject : wsNotificationSubject));

        wsQuizExerciseSubject = new Subject<QuizExercise | undefined>();

        tutorialGroup = new TutorialGroup();
        tutorialGroup.id = 99;

        TestBed.inject(CourseManagementService);
        notificationService = TestBed.inject(NotificationService);
        jest.advanceTimersByTime(20 * 1000); // simulate setInterval time passing

        mutedConversationRequest = httpMock.expectOne({ method: 'GET', url: 'api/muted-conversations' });
    });

    afterEach(() => {
        httpMock.expectOne({ method: 'GET', url: 'api/notification-settings' });
        httpMock.verify();
        jest.restoreAllMocks();
        jest.clearAllTimers();
    });

    describe('Service methods', () => {
        it('should call correct URL to fetch all notifications filtered by current notification settings', () => {
            notificationService.queryNotificationsFilteredBySettings().subscribe(() => {});
            const req = httpMock.expectOne({ method: 'GET', url: resourceUrl });
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

        it('should navigate to new message reply notification target', () => {
            const navigateToNotificationTarget = jest.spyOn(notificationService, 'navigateToNotificationTarget');
            jest.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));
            notificationService.interpretNotification(generateConversationReplyNotification());
            expect(router.navigate).toHaveBeenCalledOnce();
            expect(navigateToNotificationTarget).toHaveBeenCalledOnce();
        });

        it('should navigate to data export success notification target', () => {
            const navigationSpy = jest.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));
            notificationService.interpretNotification(generateDataExportCreationSuccessNotification());
            expect(navigationSpy).toHaveBeenCalledOnce();
            expect(navigationSpy).toHaveBeenCalledWith(['privacy', 'data-exports', 1]);
        });

        it('should navigate to data export creation failure notification target', () => {
            const navigationSpy = jest.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));
            notificationService.interpretNotification(generateDataExportCreationFailureNotification());
            expect(navigationSpy).toHaveBeenCalledOnce();
            expect(navigationSpy).toHaveBeenCalledWith(['privacy', 'data-exports']);
        });

        it('should convert date array from server', () => {
            // strange method, because notificationDate can only be of type Dayjs, I can not simulate an input with string for date
            const notificationArray = [singleUserNotification, quizNotification];
            const serverResponse = notificationArray;
            const expectedResult = notificationArray.sort();

            notificationService.queryNotificationsFilteredBySettings().subscribe((resp) => {
                expect(resp.body).toEqual(expectedResult);
            });

            const req = httpMock.expectOne({ method: 'GET', url: resourceUrl });
            req.flush(serverResponse);
        });

        it('should subscribe to single user notification updates and receive new single user notification', () => {
            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).toEqual([singleUserNotification]);
            });

            expect(wsSubscribeStub).toHaveBeenCalledTimes(5);
            // websocket correctly subscribed to the topic

            expect(wsReceiveNotificationStub).toHaveBeenCalledTimes(5);
            // websocket "receive" called

            // add new single user notification
            wsNotificationSubject.next(singleUserNotification);
            // calls addNotificationToObserver i.e. calls next on subscribeToNotificationUpdates' ReplaySubject
        });

        it('should subscribe to tutorial group notification updates and receive new tutorial group notifications', () => {
            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).toEqual([tutorialGroupNotification]);
            });

            const notificationTopic = `/topic/user/${99}/notifications/tutorial-groups`;
            expect(wsSubscribeStub).toHaveBeenCalledTimes(5);
            expect(wsSubscribeStub).toHaveBeenCalledWith(notificationTopic);
            expect(wsReceiveNotificationStub).toHaveBeenCalledTimes(5);
            wsNotificationSubject.next(tutorialGroupNotification);
        });

        it('should subscribe to conversation notification updates and receive new message notifications', () => {
            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).toEqual([conversationNotification]);
            });

            const notificationTopic = `/topic/user/${99}/notifications/conversations`;
            expect(wsSubscribeStub).toHaveBeenCalledTimes(5);
            expect(wsSubscribeStub).toHaveBeenCalledWith(notificationTopic);
            expect(wsReceiveNotificationStub).toHaveBeenCalledTimes(5);
            wsNotificationSubject.next(conversationNotification);
        });

        it('should subscribe to group notification updates and receive new group notification', () => {
            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).toEqual([groupNotification]);
            });

            const notificationTopic = `/topic/course/${course.id}/TA`;
            expect(wsSubscribeStub).toHaveBeenCalledTimes(5);
            expect(wsSubscribeStub).toHaveBeenCalledWith(notificationTopic);
            // websocket correctly subscribed to the topic

            expect(wsReceiveNotificationStub).toHaveBeenCalledTimes(5);
            // websocket "receive" called

            // add new single user notification
            wsNotificationSubject.next(groupNotification);
            // calls addNotificationToObserver i.e. calls next on subscribeToNotificationUpdates' ReplaySubject
        });

        it('should subscribe to quiz notification updates and receive a new quiz exercise and create a new quiz notification from it', () => {
            const wsReceiveQuizExerciseStub = jest.spyOn(websocketService, 'receive').mockReturnValue(wsQuizExerciseSubject);

            notificationService.subscribeToNotificationUpdates().subscribe((notifications) => {
                // the quiz notification is created after a new quiz exercise has been detected, therefore the time will always be different
                notifications.forEach((notification) => (notification.notificationDate = undefined));
                quizNotification.notificationDate = undefined;

                expect(notifications).toEqual([quizNotification]);
            });

            const notificationTopic = `/topic/course/${course.id}/TA`;
            expect(wsSubscribeStub).toHaveBeenCalledTimes(5);
            expect(wsSubscribeStub).toHaveBeenCalledWith(notificationTopic);
            // websocket correctly subscribed to the topic

            expect(wsReceiveQuizExerciseStub).toHaveBeenCalledTimes(5);
            // websocket "receive" called

            // pushes new quizExercise
            wsQuizExerciseSubject.next(quizExercise);
            // calls addNotificationToObserver i.e. calls next on subscribeToNotificationUpdates' ReplaySubject
        });

        it('should handle new message notification if user is mentioned', () => {
            const postDTO: MetisPostDTO = {
                post: { author: { id: 456 }, content: 'Content', conversation: { id: 1 } } as Post,
                action: MetisPostAction.CREATE,
                notification: { title: MENTIONED_IN_MESSAGE_TITLE },
            };

            const handleNotificationSpy = jest.spyOn(notificationService, 'handleNotification');

            wsPostDTOSubject.next(postDTO);

            expect(handleNotificationSpy).toHaveBeenCalledWith(postDTO);
        });

        it('should not show notification for muted conversation', () => {
            mutedConversationRequest.flush([1]);

            const postDTO: MetisPostDTO = {
                post: { author: { id: 456 }, content: 'Content', conversation: { id: 1 } } as Post,
                action: MetisPostAction.CREATE,
                notification: { title: 'title' },
            };

            const handleNotificationSpy = jest.spyOn(notificationService, 'handleNotification');

            wsPostDTOSubject.next(postDTO);

            expect(handleNotificationSpy).not.toHaveBeenCalled();
        });

        it('should mute conversation', () => {
            notificationService.muteNotificationsForConversation(1);
            expect(notificationService['mutedConversations']).toEqual([1]);

            // Do not mute same conversation twice
            notificationService.muteNotificationsForConversation(1);
            expect(notificationService['mutedConversations']).toEqual([1]);
        });

        it('should unmute conversation', () => {
            notificationService['mutedConversations'] = [1];

            notificationService.unmuteNotificationsForConversation(1);
            expect(notificationService['mutedConversations']).toEqual([]);

            // No error if already unmuted conversation is removed
            notificationService.unmuteNotificationsForConversation(1);
            expect(notificationService['mutedConversations']).toEqual([]);
        });

        it('should handle textIsPlaceholder being true and return translated text', () => {
            const notification: Notification = { textIsPlaceholder: true, text: 'someText' };
            jest.spyOn(artemisTranslatePipe, 'transform').mockReturnValue('Translated Text');
            const result = notificationService.getNotificationTextTranslation(notification, 50);
            expect(result).toBe('Translated Text');
        });

        it('should truncate long translations', () => {
            const notification: Notification = { textIsPlaceholder: true, text: 'longText' };
            jest.spyOn(artemisTranslatePipe, 'transform').mockReturnValue('A very long translated text that exceeds the limit');
            const result = notificationService.getNotificationTextTranslation(notification, 10);
            expect(result).toBe('A very lo...');
        });

        it('should return original text if translation not found', () => {
            const notification: Notification = { textIsPlaceholder: true, text: 'someKey' };
            jest.spyOn(artemisTranslatePipe, 'transform').mockReturnValue('translation-not-found');
            const result = notificationService.getNotificationTextTranslation(notification, 50);
            expect(result).toBe(notification.text);
        });

        it('should return default message if translation not found and original text is undefined', () => {
            const notification: Notification = { textIsPlaceholder: true, text: 'abcdef' };
            jest.spyOn(artemisTranslatePipe, 'transform').mockReturnValue(undefined);
            const result = notificationService.getNotificationTextTranslation(notification, 50);
            expect(result).toBeUndefined();
        });

        it('should replace specific text patterns in the translation', () => {
            const notification: Notification = { textIsPlaceholder: true, text: MESSAGE_REPLY_IN_CONVERSATION_TEXT };
            jest.spyOn(artemisTranslatePipe, 'transform').mockReturnValue('Hello, [user]User Name(user123)[/user]');
            const result = notificationService.getNotificationTextTranslation(notification, 50);
            expect(result).toBe('Hello, User Name');
        });

        it('should replace multiple occurences of specific text patterns in the translation', () => {
            const notification: Notification = { textIsPlaceholder: true, text: MESSAGE_REPLY_IN_CONVERSATION_TEXT };
            jest.spyOn(artemisTranslatePipe, 'transform').mockReturnValue(
                'Hello, [user]User Name(user123)[/user] [exercise]Modeling 1(exercises/123)[/exercise] [abc]Test(test)[/abc]',
            );
            const result = notificationService.getNotificationTextTranslation(notification, 50);
            expect(result).toBe('Hello, User Name Modeling 1 Test');
        });

        it('should recognize wrong specific text patterns and not translate it', () => {
            const notification: Notification = { textIsPlaceholder: true, text: MESSAGE_REPLY_IN_CONVERSATION_TEXT };
            jest.spyOn(artemisTranslatePipe, 'transform').mockReturnValue('Hello, [abc]Test(user123)[/def] [abc]Test[/abc]');
            const result = notificationService.getNotificationTextTranslation(notification, 50);
            expect(result).toBe('Hello, [abc]Test(user123)[/def] [abc]Test[/abc]');
        });

        it('should subscribe to course-wide channel topic on NavigationEnd to /courses URL', () => {
            router.setUrl('/courses/' + 1);
            expect(wsSubscribeStub).toHaveBeenCalledWith('/topic/metis/courses/1');
            expect(wsSubscribeStub).toHaveBeenCalledTimes(6);
            expect(wsReceiveNotificationStub).toHaveBeenCalledWith('/topic/metis/courses/1');
        });

        it('should clear course-wide channel subscription on NavigationEnd to a non-course URL', () => {
            router.setUrl('/courses/' + 1);
            router.setUrl('/courses');

            expect(wsUnsubscribeStub).toHaveBeenCalledExactlyOnceWith('/topic/metis/courses/1');
            expect(wsSubscribeStub).toHaveBeenCalledTimes(6);
        });

        it('should switch course-wide channel subscriptions on NavigationEnd', () => {
            router.setUrl('/courses/' + 1);
            router.setUrl('/courses/' + 2);

            expect(wsUnsubscribeStub).toHaveBeenCalledExactlyOnceWith('/topic/metis/courses/1');
            expect(wsSubscribeStub).toHaveBeenCalledWith('/topic/metis/courses/2');
            expect(wsSubscribeStub).toHaveBeenCalledTimes(7);
        });

        it('should not subscribe to same course-wide channel topic twice', () => {
            router.setUrl('/courses/' + 1);
            router.setUrl('/courses/' + 1 + '/exercises');

            expect(wsSubscribeStub).toHaveBeenCalledWith('/topic/metis/courses/1');
            expect(wsSubscribeStub).toHaveBeenCalledTimes(6);
            expect(wsUnsubscribeStub).not.toHaveBeenCalled();
        });

        it('should add notification if it is from another user and allowed by settings', () => {
            const notification = { author: { id: 1 }, target: 'target', notificationDate: dayjs() } as Notification;
            const postDTO: MetisPostDTO = {
                post: { author: { id: 1 }, content: 'Content' } as Post,
                action: MetisPostAction.CREATE,
                notification,
            };

            notificationService.handleNotification(postDTO);
            expect(notificationService.notifications).toStrictEqual([notification]);
        });

        it('should add notification about reply if current user is involved', () => {
            jest.spyOn(accountService, 'userIdentity', 'get').mockReturnValue({ id: 1, login: 'test', name: 'A B' } as User);
            const notification = { author: { id: 2 }, target: 'target', notificationDate: dayjs() } as Notification;
            const postDTO: MetisPostDTO = {
                post: {
                    author: { id: 2 },
                    conversation: { type: ConversationType.CHANNEL, isCourseWide: true } as Channel,
                    answers: [{ author: { id: 1 } }, { author: { id: 2 } }],
                } as Post,
                action: MetisPostAction.UPDATE,
                notification,
            };

            notificationService.handleNotification(postDTO);
            expect(notificationService.notifications).toStrictEqual([notification]);
        });

        it('should not add notification if it is from the current user', () => {
            jest.spyOn(accountService, 'userIdentity', 'get').mockReturnValue({ id: 1, login: 'test', name: 'A B' } as User);
            const postDTO: MetisPostDTO = {
                post: { author: { id: 1 }, content: 'Content' } as Post,
                action: MetisPostAction.CREATE,
                notification: { author: { id: 1 }, target: 'target' } as Notification,
            };

            notificationService.handleNotification(postDTO);

            expect(notificationService.notifications).toBeEmpty();
        });

        it('should add notification if it is from another author', () => {
            jest.spyOn(accountService, 'userIdentity', 'get').mockReturnValue({ id: 1, login: 'test', name: 'A B' } as User);
            const postDTO: MetisPostDTO = {
                post: { author: { id: 1 }, content: 'Content' } as Post,
                action: MetisPostAction.CREATE,
                notification: { author: { id: 1 }, target: 'target' } as Notification,
            };

            notificationService.handleNotification(postDTO);

            expect(notificationService.notifications).toBeEmpty();
        });

        it('should change notification title if the current user is mentioned in message', () => {
            jest.spyOn(accountService, 'userIdentity', 'get').mockReturnValue({ id: 1, login: 'test', name: 'A B' } as User);
            const postDTO: MetisPostDTO = {
                post: { author: { id: 2 }, content: '[user]A B(test)[/user]', answers: [{ id: 5, content: 'test' }] } as Post,
                notification: { author: { id: 2 }, target: 'target', title: NEW_MESSAGE_TITLE } as Notification,
                action: MetisPostAction.CREATE,
            };

            wsPostDTOSubject.next(postDTO);

            expect(postDTO.notification?.title).toBe(MENTIONED_IN_MESSAGE_TITLE);
        });

        it('should change notification title if the current user is mentioned in reply', () => {
            jest.spyOn(accountService, 'userIdentity', 'get').mockReturnValue({ id: 1, login: 'test', name: 'A B' } as User);
            const postDTO: MetisPostDTO = {
                post: { author: { id: 2 }, content: 'test', answers: [{ id: 5, content: '[user]A B(test)[/user]' }] } as Post,
                notification: { author: { id: 2 }, target: 'target', title: NEW_MESSAGE_TITLE } as Notification,
                action: MetisPostAction.UPDATE,
            };

            wsPostDTOSubject.next(postDTO);

            expect(postDTO.notification?.title).toBe(MENTIONED_IN_MESSAGE_TITLE);
        });
    });
});
