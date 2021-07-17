import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SERVER_API_URL } from 'app/app.constants';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateTestingModule } from '../helpers/mocks/service/mock-translate.service';
import { Notification } from 'app/entities/notification.model';
import { LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE } from 'app/shared/notification/notification.constants';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { MockRouter } from '../helpers/mocks/mock-router';
import { RouterTestingModule } from '@angular/router/testing';
import * as moment from 'moment';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockCourseManagementService } from '../helpers/mocks/service/mock-course-management.service';
import { Subject } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { SinonStub, stub } from 'sinon';

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
    let wsReceiveStub: SinonStub;
    let wsNotificationSubject: Subject<Notification | undefined>;

    const generateQuizNotification = (id: number) => {
        const generatedNotification = { id, title: 'Quiz started', text: 'Quiz "Proxy pattern" just started.' } as Notification;
        generatedNotification.target = JSON.stringify({ mainPage: 'courses', course: 1, entity: 'exercise', id: 1 });
        generatedNotification.notificationDate = moment();
        return generatedNotification;
    };
    const quizNotification = generateQuizNotification(1);

    const generateExamExerciseUpdateNotification = () => {
        const generatedNotification = { title: LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE, text: 'Fixed mistake' } as Notification;
        generatedNotification.target = JSON.stringify({ mainPage: 'courses', course: 1, entity: 'exams', exam: 1, exercise: 7, problemStatement: 'Fixed Problem Statement' });
        generatedNotification.notificationDate = moment().subtract(3, 'days');
        return generatedNotification;
    };
    const examExerciseUpdateNotification = generateExamExerciseUpdateNotification();

    const generateSingleUserNotification = () => {
        const generatedNotification = { title: 'Single user notification', text: 'This is a notification for a single user' } as Notification;
        generatedNotification.target = JSON.stringify({ mainPage: 'courses', course: 1, entity: 'exams', exam: 1, exercise: 7, problemStatement: 'Fixed Problem Statement' });
        generatedNotification.notificationDate = moment().subtract(3, 'days');
        return generatedNotification;
    };
    const singleUserNotification = generateSingleUserNotification();

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
                wsReceiveStub = stub(websocketService, 'receive').returns(wsNotificationSubject);
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
            const notificationArray = [examExerciseUpdateNotification, quizNotification];
            let serverResponse = notificationArray;
            const expectedResult = notificationArray.sort();

            notificationService.query().subscribe((resp) => {
                expect(resp.body).to.equal(expectedResult);
            });

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(serverResponse);
            tick();
        }));

        it('should subscribe to single user notification updates and receive new notification', fakeAsync(() => {
            notificationService.subscribeToNotificationUpdates().subscribe((notification) => {
                expect(notification).to.equal(singleUserNotification);
            });

            tick(); // position of tick is very important here !

            const userId = 99; //based on MockAccountService
            const notificationTopic = `/topic/user/${userId}/notifications`;
            expect(wsSubscribeStub).to.have.been.calledOnceWithExactly(notificationTopic);
            //websocket correctly subscribed to the topic

            expect(wsReceiveStub).to.have.been.calledOnce;
            //websocket received "receive" call

            //add new single user notification
            wsNotificationSubject.next(singleUserNotification);
            //calls addNotificationToObserver i.e. calls next on subscribeToNotificationUpdates' ReplaySubject
        }));
    });
});
