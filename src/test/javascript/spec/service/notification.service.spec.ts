import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SERVER_API_URL } from 'app/app.constants';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateTestingModule } from '../helpers/mocks/service/mock-translate.service';
import { Notification } from 'app/entities/notification.model';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { MockRouter } from '../helpers/mocks/mock-router';
import { RouterTestingModule } from '@angular/router/testing';
import * as moment from 'moment';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockCourseManagementService } from '../helpers/mocks/service/mock-course-management.service';
import { BehaviorSubject, Subject } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { SinonStub, stub } from 'sinon';
import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('Logs Service', () => {
    let notificationService: NotificationService;
    let httpMock: HttpTestingController;
    let router: Router;
    let accountService: AccountService;

    let websocketService: JhiWebsocketService;
    let wsSubscribeStub: SinonStub;
    let wsUnsubscribeStub: SinonStub;
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
            .compileComponents()
            .then(() => {
                notificationService = TestBed.inject(NotificationService);
                httpMock = TestBed.inject(HttpTestingController);
                router = TestBed.get(Router);

                accountService = TestBed.inject(AccountService);

                websocketService = TestBed.inject(JhiWebsocketService);
                wsSubscribeStub = stub(websocketService, 'subscribe');
                wsUnsubscribeStub = stub(websocketService, 'unsubscribe');
                wsNotificationSubject = new Subject<Notification | undefined>();
                wsReceiveNotificationStub = stub(websocketService, 'receive').returns(wsNotificationSubject);

                wsQuizExerciseSubject = new Subject<QuizExercise | undefined>();
                //wsReceiveQuizExerciseStub = stub(websocketService, 'receive').returns(wsQuizExerciseSubject);

                courseManagementService = TestBed.inject(CourseManagementService);
                cmCoursesSubject = new Subject<[Course] | undefined>();
                cmGetCoursesForNotificationsStub = stub(courseManagementService, 'getCoursesForNotifications').returns(cmCoursesSubject as BehaviorSubject<[Course] | undefined>);
            });
    });

    afterEach(() => {
        httpMock.verify();
        sinon.restore();
    });

    describe('Service methods', () => {
        it('should call correct URL', () => {
            notificationService.query().subscribe(() => {});
            const req = httpMock.expectOne({ method: 'GET' });
            const infoUrl = SERVER_API_URL + 'api/notifications';
            expect(req.request.url).to.equal(infoUrl);
        });

        it('should navigate to notification target', () => {
            sinon.spy(router, 'navigate');
            sinon.replace(router, 'navigate', sinon.fake());

            notificationService.interpretNotification(quizNotification);

            expect(router.navigate).to.have.been.calledOnce;
        });

        it('should convert date array from server', fakeAsync(() => {
            //strange method, because notificationDate can only be of type Moment, I can not simulate an input with string for date
            const notificationArray = [singleUserNotification, quizNotification];
            let serverResponse = notificationArray;
            const expectedResult = notificationArray.sort();

            notificationService.query().subscribe((resp) => {
                expect(resp.body).to.equal(expectedResult);
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(serverResponse);
            tick();
        }));

        it('should subscribe to single user notification updates and receive new single user notification', fakeAsync(() => {
            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).to.equal(singleUserNotification);
            });

            tick(); // position of tick is very important here !

            const userId = 99; //based on MockAccountService
            const notificationTopic = `/topic/user/${userId}/notifications`;
            expect(wsSubscribeStub).to.have.been.calledOnceWithExactly(notificationTopic);
            //websocket correctly subscribed to the topic

            expect(wsReceiveNotificationStub).to.have.been.calledOnce;
            //websocket "receive" called

            //add new single user notification
            wsNotificationSubject.next(singleUserNotification);
            //calls addNotificationToObserver i.e. calls next on subscribeToNotificationUpdates' ReplaySubject
        }));

        it('should subscribe to group notification updates and receive new group notification', fakeAsync(() => {
            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).to.equal(groupNotification);
            });

            tick(); // position of tick is very important here !

            expect(cmGetCoursesForNotificationsStub).to.have.been.calledOnce;
            //courseManagementService.getCoursesForNotifications had been successfully subscribed to

            //push new courses
            cmCoursesSubject.next([course]);

            const notificationTopic = `/topic/course/${course.id}/TA`;
            expect(wsSubscribeStub).to.have.been.calledWith(notificationTopic);
            //websocket correctly subscribed to the topic

            expect(wsReceiveNotificationStub).to.have.been.called;
            //websocket "receive" called

            //add new single user notification
            wsNotificationSubject.next(groupNotification);
            //calls addNotificationToObserver i.e. calls next on subscribeToNotificationUpdates' ReplaySubject
        }));

        it('should subscribe to quiz notification updates and receive a new quiz exercise and create a new quiz notification from it', fakeAsync(() => {
            wsReceiveNotificationStub.restore();
            wsReceiveQuizExerciseStub = stub(websocketService, 'receive').returns(wsQuizExerciseSubject);

            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                //the quiz notification is created after a new quiz exercise has been detected, therefore the time will always be different
                notification.notificationDate = undefined;
                quizNotification.notificationDate = undefined;

                expect(notification).to.be.deep.equal(quizNotification);
            });

            tick(); // position of tick is very important here !

            expect(cmGetCoursesForNotificationsStub).to.have.been.calledOnce;
            //courseManagementService.getCoursesForNotifications had been successfully subscribed to

            //push new courses
            cmCoursesSubject.next([course]);

            const notificationTopic = `/topic/course/${course.id}/TA`;
            expect(wsSubscribeStub).to.have.been.calledWith(notificationTopic);
            //websocket correctly subscribed to the topic

            expect(wsReceiveQuizExerciseStub).to.have.been.called;
            //websocket "receive" called

            //pushes new quizExercise
            wsQuizExerciseSubject.next(quizExercise);
            //calls addNotificationToObserver i.e. calls next on subscribeToNotificationUpdates' ReplaySubject
            tick();
        }));
    });
});
