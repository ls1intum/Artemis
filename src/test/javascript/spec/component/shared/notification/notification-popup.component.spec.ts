import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ReplaySubject } from 'rxjs';
import { NotificationPopupComponent } from 'app/shared/notification/notification-popup/notification-popup.component';
import { NotificationService } from 'app/shared/notification/notification.service';
import { ArtemisTestModule } from '../../../test.module';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockNotificationService } from '../../../helpers/mocks/service/mock-notification.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import {
    LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE,
    NEW_MESSAGE_TITLE,
    Notification,
    QUIZ_EXERCISE_STARTED_TEXT,
    QUIZ_EXERCISE_STARTED_TITLE,
} from 'app/entities/notification.model';
import { RouterTestingModule } from '@angular/router/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { MockExamExerciseUpdateService } from '../../../helpers/mocks/service/mock-exam-exercise-update.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { MockExamParticipationService } from '../../../helpers/mocks/service/mock-exam-participation.service';

describe('Notification Popup Component', () => {
    let notificationPopupComponent: NotificationPopupComponent;
    let notificationPopupComponentFixture: ComponentFixture<NotificationPopupComponent>;
    let notificationService: NotificationService;
    let examExerciseUpdateService: ExamExerciseUpdateService;
    let router: Router;

    const generateQuizNotification = (notificationId: number) => {
        const generatedNotification = {
            id: notificationId,
            title: QUIZ_EXERCISE_STARTED_TITLE,
            text: QUIZ_EXERCISE_STARTED_TEXT,
            textIsPlaceholder: true,
            placeholderValues: '["Proxy Pattern"]',
        } as Notification;
        generatedNotification.target = JSON.stringify({ mainPage: 'courses', course: 1, entity: 'exercise', id: 1 });
        return generatedNotification;
    };
    const quizNotification = generateQuizNotification(1);

    const generateNewMessageNotification = (notificationId: number) => {
        const generatedNotification = { id: notificationId, title: NEW_MESSAGE_TITLE, text: 'New message from user. In course' } as Notification;
        generatedNotification.target = JSON.stringify({ mainPage: 'courses', course: 1, entity: 'message', id: 20, conversation: 1 });
        return generatedNotification;
    };
    const newMessageNotification = generateNewMessageNotification(2);

    const generateExamExerciseUpdateNotification = () => {
        const generatedNotification = { title: LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE, text: 'Fixed mistake' } as Notification;
        generatedNotification.target = JSON.stringify({ mainPage: 'courses', course: 1, entity: 'exams', exam: 1, exercise: 7, problemStatement: 'Fixed Problem Statement' });
        return generatedNotification;
    };
    const examExerciseUpdateNotification = generateExamExerciseUpdateNotification();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [NotificationPopupComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ExamExerciseUpdateService, useClass: MockExamExerciseUpdateService },
                { provide: ExamParticipationService, useClass: MockExamParticipationService },
                { provide: ArtemisTranslatePipe, useClass: ArtemisTranslatePipe },
            ],
        })
            .compileComponents()
            .then(() => {
                notificationPopupComponentFixture = TestBed.createComponent(NotificationPopupComponent);
                notificationPopupComponent = notificationPopupComponentFixture.componentInstance;
                notificationService = TestBed.inject(NotificationService);
                examExerciseUpdateService = TestBed.inject(ExamExerciseUpdateService);
                router = TestBed.inject(Router);
            });
    });

    describe('Initialization', () => {
        it('should subscribe to singular notification updates', () => {
            const subscribeToSingleIncomingNotifications = jest.spyOn(notificationService, 'subscribeToSingleIncomingNotifications');
            notificationPopupComponent.ngOnInit();
            expect(subscribeToSingleIncomingNotifications).toHaveBeenCalledOnce();
        });
    });

    describe('Click', () => {
        it('should navigate to conversation target when New message notification is clicked', () => {
            notificationPopupComponent.notifications = [newMessageNotification];
            notificationPopupComponentFixture.detectChanges();

            const navigateToTarget = jest.spyOn(notificationPopupComponent, 'navigateToTarget');
            const forceComponentReload = jest.spyOn(notificationService, 'forceComponentReload');
            jest.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));

            expect(navigateToTarget).toHaveBeenCalledTimes(0);
            expect(forceComponentReload).toHaveBeenCalledTimes(0);

            const button = notificationPopupComponentFixture.debugElement.query(By.css('.notification-popup-container > div button'));
            button.nativeElement.click();
            expect(navigateToTarget).toHaveBeenCalledOnce();
            expect(forceComponentReload).toHaveBeenCalledOnce();
        });

        it('should navigate to exam exercise target when ExamExerciseUpdate notification is clicked', () => {
            notificationPopupComponent.notifications = [examExerciseUpdateNotification];
            notificationPopupComponentFixture.detectChanges();

            const navigateToTarget = jest.spyOn(notificationPopupComponent, 'navigateToTarget');
            const navigateToExamExercise = jest.spyOn(examExerciseUpdateService, 'navigateToExamExercise').mockReturnValue();
            const button = notificationPopupComponentFixture.debugElement.query(By.css('.notification-popup-container > div button'));
            button.nativeElement.click();
            expect(navigateToTarget).toHaveBeenCalledOnce();
            expect(navigateToExamExercise).toHaveBeenCalledOnce();
        });

        describe('General & Quiz', () => {
            beforeEach(() => {
                notificationPopupComponent.notifications = [quizNotification];
                notificationPopupComponentFixture.detectChanges();
            });

            it('should remove notification from component state when notification is closed', () => {
                const removeNotification = jest.spyOn(notificationPopupComponent, 'removeNotification');
                jest.spyOn(notificationPopupComponent, 'navigateToTarget').mockReturnValue();
                const button = notificationPopupComponentFixture.debugElement.query(By.css('.notification-popup-container > div button'));
                button.nativeElement.click();
                expect(removeNotification).toHaveBeenCalledTimes(2);
                expect(notificationPopupComponent.notifications).toBeEmpty();
            });

            it('should remove quiz notification from component state and navigate to quiz target when notification is clicked', () => {
                const removeNotification = jest.spyOn(notificationPopupComponent, 'removeNotification');
                const navigateToTarget = jest.spyOn(notificationPopupComponent, 'navigateToTarget').mockReturnValue();
                const notificationElement = notificationPopupComponentFixture.debugElement.query(By.css('.notification-popup-container > div'));
                notificationElement.nativeElement.click();
                expect(removeNotification).toHaveBeenCalledOnce();
                expect(notificationPopupComponent.notifications).toBeEmpty();
                expect(navigateToTarget).toHaveBeenCalledOnce();
            });

            it('should navigate to quiz target when quiz notification is clicked', () => {
                const navigateToTarget = jest.spyOn(notificationPopupComponent, 'navigateToTarget');
                const navigateByUrl = jest.spyOn(router, 'navigateByUrl');
                const button = notificationPopupComponentFixture.debugElement.query(By.css('.notification-popup-container > div button'));
                button.nativeElement.click();
                expect(navigateToTarget).toHaveBeenCalledOnce();
                expect(navigateByUrl).toHaveBeenCalledOnce();
            });
        });
    });

    describe('Websocket receive', () => {
        const replaceSubscribeToNotificationUpdatesUsingQuizNotification = () => {
            const replay = new ReplaySubject<Notification>();
            jest.spyOn(notificationService, 'subscribeToSingleIncomingNotifications').mockReturnValue(replay);
            replay.next(quizNotification);
        };
        const replaceSubscribeToNotificationUpdatesUsingExamExerciseUpdateNotification = () => {
            const replay = new ReplaySubject<Notification>();
            jest.spyOn(notificationService, 'subscribeToSingleIncomingNotifications').mockReturnValue(replay);
            replay.next(examExerciseUpdateNotification);
        };

        it('should append received notification', fakeAsync(() => {
            jest.spyOn(router, 'isActive').mockReturnValue(false);
            replaceSubscribeToNotificationUpdatesUsingQuizNotification();
            const otherNotification = generateQuizNotification(2);
            notificationPopupComponent.notifications = [otherNotification];
            notificationPopupComponent.ngOnInit();
            expect(notificationPopupComponent.notifications).toHaveLength(2);
            expect(notificationPopupComponent.notifications[1]).toEqual(quizNotification);
            tick(30000);
            expect(notificationPopupComponent.notifications).toHaveLength(0);
        }));

        it('should not add received quiz notification if user is already on quiz page', () => {
            jest.spyOn(router, 'isActive').mockReturnValue(true);
            replaceSubscribeToNotificationUpdatesUsingQuizNotification();
            notificationPopupComponent.ngOnInit();
            expect(notificationPopupComponent.notifications).toBeEmpty();
        });

        it('should not add received exam exercise update notification if user is not in exam mode', () => {
            jest.spyOn(router, 'isActive').mockReturnValue(false);
            jest.spyOn(examExerciseUpdateService, 'updateLiveExamExercise').mockReturnValue();
            replaceSubscribeToNotificationUpdatesUsingExamExerciseUpdateNotification();
            notificationPopupComponent.ngOnInit();
            expect(notificationPopupComponent.notifications).toBeEmpty();
        });

        it('should add received exam exercise update notification if user is in exam mode', () => {
            jest.spyOn(router, 'isActive').mockReturnValue(true);
            jest.spyOn(router, 'url', 'get').mockReturnValue('/courses/1/exams/95');
            jest.spyOn(examExerciseUpdateService, 'updateLiveExamExercise').mockReturnValue();
            replaceSubscribeToNotificationUpdatesUsingExamExerciseUpdateNotification();
            notificationPopupComponent.ngOnInit();
            expect(notificationPopupComponent.notifications).not.toBeEmpty();
        });

        it('should not add received not exam exercise relevant update notification if user is not in exam mode', () => {
            jest.spyOn(router, 'url', 'get').mockReturnValue('/courses/1/exams/95');
            replaceSubscribeToNotificationUpdatesUsingQuizNotification();
            notificationPopupComponent.ngOnInit();
            expect(notificationPopupComponent.notifications).toBeEmpty();
        });

        it('should add and remove received message notification', fakeAsync(() => {
            jest.spyOn(router, 'isActive').mockReturnValue(true);
            const replay = new ReplaySubject<Notification>();
            jest.spyOn(notificationService, 'subscribeToSingleIncomingNotifications').mockReturnValue(replay);
            replay.next(newMessageNotification);
            notificationPopupComponent.ngOnInit();
            expect(notificationPopupComponent.notifications).not.toBeEmpty();
            notificationPopupComponentFixture.detectChanges();
            tick();
            expect(notificationPopupComponent['scrollContainer'].nativeElement.scrollTop).toBe(notificationPopupComponent['scrollContainer'].nativeElement.scrollHeight);
            tick(15000);
            expect(notificationPopupComponent.notifications).toBeEmpty();
        }));
    });

    afterEach(() => {
        jest.clearAllMocks();
    });
});
