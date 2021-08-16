import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { BehaviorSubject } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { NotificationPopupComponent } from 'app/shared/notification/notification-popup/notification-popup.component';
import { NotificationService } from 'app/shared/notification/notification.service';
import { AccountService } from 'app/core/auth/account.service';
import { ArtemisTestModule } from '../../../test.module';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockNotificationService } from '../../../helpers/mocks/service/mock-notification.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { Notification } from 'app/entities/notification.model';
import { RouterTestingModule } from '@angular/router/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE } from 'app/shared/notification/notification.constants';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { MockExamExerciseUpdateService } from '../../../helpers/mocks/service/mock-exam-exercise-update.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { MockExamParticipationService } from '../../../helpers/mocks/service/mock-exam-participation.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('Notification Popup Component', () => {
    let notificationPopupComponent: NotificationPopupComponent;
    let notificationPopupComponentFixture: ComponentFixture<NotificationPopupComponent>;
    let notificationService: NotificationService;
    let accountService: AccountService;
    let examExerciseUpdateService: ExamExerciseUpdateService;
    let router: Router;

    const generateQuizNotification = (id: number) => {
        const generatedNotification = { id, title: 'Quiz started', text: 'Quiz "Proxy pattern" just started.' } as Notification;
        generatedNotification.target = JSON.stringify({ mainPage: 'courses', course: 1, entity: 'exercise', id: 1 });
        return generatedNotification;
    };
    const quizNotification = generateQuizNotification(1);

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
                { provide: AccountService, useClass: MockAccountService },
                { provide: ExamExerciseUpdateService, useClass: MockExamExerciseUpdateService },
                { provide: ExamParticipationService, useClass: MockExamParticipationService },
            ],
        })
            .compileComponents()
            .then(() => {
                notificationPopupComponentFixture = TestBed.createComponent(NotificationPopupComponent);
                notificationPopupComponent = notificationPopupComponentFixture.componentInstance;
                notificationService = TestBed.inject(NotificationService);
                accountService = TestBed.inject(AccountService);
                examExerciseUpdateService = TestBed.inject(ExamExerciseUpdateService);
                router = TestBed.get(Router);
            });
    });

    describe('Initialization', () => {
        it('should get authentication state', () => {
            sinon.spy(accountService, 'getAuthenticationState');
            notificationPopupComponent.ngOnInit();
            expect(accountService.getAuthenticationState).to.have.been.calledOnce;
        });

        it('should subscribe to notification updates', () => {
            sinon.spy(notificationService, 'subscribeToNotificationUpdates');
            notificationPopupComponent.ngOnInit();
            expect(notificationService.subscribeToNotificationUpdates).to.have.been.calledOnce;
        });
    });

    describe('Click', () => {
        describe('General & Quiz', () => {
            beforeEach(() => {
                notificationPopupComponent.notifications.push(quizNotification);
                notificationPopupComponentFixture.detectChanges();
            });

            it('should remove notification from component state when notification is closed', () => {
                sinon.spy(notificationPopupComponent, 'removeNotification');
                sinon.replace(notificationPopupComponent, 'navigateToTarget', sinon.fake());
                const button = notificationPopupComponentFixture.debugElement.query(By.css('.notification-popup-container > div button'));
                button.nativeElement.click();
                expect(notificationPopupComponent.removeNotification).to.have.been.called;
                expect(notificationPopupComponent.notifications).to.be.empty;
            });

            it('should remove quiz notification from component state and navigate to quiz target when notification is clicked', () => {
                sinon.spy(notificationPopupComponent, 'removeNotification');
                sinon.replace(notificationPopupComponent, 'navigateToTarget', sinon.fake());
                const notificationElement = notificationPopupComponentFixture.debugElement.query(By.css('.notification-popup-container > div'));
                notificationElement.nativeElement.click();
                expect(notificationPopupComponent.removeNotification).to.have.been.calledOnce;
                expect(notificationPopupComponent.notifications).to.be.empty;
                expect(notificationPopupComponent.navigateToTarget).to.have.been.calledOnce;
            });

            it('should navigate to quiz target when quiz notification is clicked', () => {
                sinon.spy(notificationPopupComponent, 'navigateToTarget');
                sinon.replace(router, 'navigateByUrl', sinon.fake());
                const button = notificationPopupComponentFixture.debugElement.query(By.css('.notification-popup-container > div button'));
                button.nativeElement.click();
                expect(notificationPopupComponent.navigateToTarget).to.have.been.calledOnce;
                expect(router.navigateByUrl).to.have.been.calledOnce;
            });
        });

        it('should navigate to exam exercise target when ExamExerciseUpdate notification is clicked', () => {
            notificationPopupComponent.notifications.push(examExerciseUpdateNotification);
            notificationPopupComponentFixture.detectChanges();

            sinon.spy(notificationPopupComponent, 'navigateToTarget');
            sinon.replace(examExerciseUpdateService, 'navigateToExamExercise', sinon.fake());
            const button = notificationPopupComponentFixture.debugElement.query(By.css('.notification-popup-container > div button'));
            button.nativeElement.click();
            expect(notificationPopupComponent.navigateToTarget).to.have.been.calledOnce;
            expect(examExerciseUpdateService.navigateToExamExercise).to.have.been.calledOnce;
        });
    });

    describe('Websocket receive', () => {
        const replaceSubscribeToNotificationUpdatesUsingQuizNotification = () => {
            const fake = sinon.fake.returns(new BehaviorSubject(quizNotification));
            sinon.replace(notificationService, 'subscribeToNotificationUpdates', fake);
        };
        const replaceSubscribeToNotificationUpdatesUsingExamExerciseUpdateNotification = () => {
            const fake = sinon.fake.returns(new BehaviorSubject(examExerciseUpdateNotification));
            sinon.replace(notificationService, 'subscribeToNotificationUpdates', fake);
        };

        it('should prepend received notification', fakeAsync(() => {
            sinon.replace(router, 'isActive', sinon.fake.returns(false));
            replaceSubscribeToNotificationUpdatesUsingQuizNotification();
            const otherNotification = generateQuizNotification(2);
            notificationPopupComponent.notifications = [otherNotification];
            notificationPopupComponent.ngOnInit();
            expect(notificationPopupComponent.notifications.length).to.be.equal(2);
            expect(notificationPopupComponent.notifications[0]).to.be.equal(quizNotification);
            tick(30000);
            expect(notificationPopupComponent.notifications.length).to.be.equal(1);
            expect(notificationPopupComponent.notifications[0]).to.be.equal(otherNotification);
        }));

        it('should not add received quiz notification if user is already on quiz page', () => {
            sinon.replace(router, 'isActive', sinon.fake.returns(true));
            replaceSubscribeToNotificationUpdatesUsingQuizNotification();
            notificationPopupComponent.ngOnInit();
            expect(notificationPopupComponent.notifications).to.be.empty;
        });

        it('should not add received exam exercise update notification if user is not in exam mode', () => {
            sinon.replace(router, 'isActive', sinon.fake.returns(false));
            sinon.replace(examExerciseUpdateService, 'updateLiveExamExercise', sinon.fake());
            replaceSubscribeToNotificationUpdatesUsingExamExerciseUpdateNotification();
            notificationPopupComponent.ngOnInit();
            expect(notificationPopupComponent.notifications).to.be.empty;
        });

        it('should add received exam exercise update notification if user is in exam mode', () => {
            sinon.replace(router, 'isActive', sinon.fake.returns(true));
            sinon.replace(examExerciseUpdateService, 'updateLiveExamExercise', sinon.fake());
            replaceSubscribeToNotificationUpdatesUsingExamExerciseUpdateNotification();
            notificationPopupComponent.ngOnInit();
            expect(notificationPopupComponent.notifications).to.not.be.empty;
        });
    });
});
