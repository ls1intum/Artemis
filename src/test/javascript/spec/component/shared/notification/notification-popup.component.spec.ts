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

chai.use(sinonChai);
const expect = chai.expect;

describe('Notification Popup Component', () => {
    let notificationPopupComponent: NotificationPopupComponent;
    let notificationPopupComponentFixture: ComponentFixture<NotificationPopupComponent>;
    let notificationService: NotificationService;
    let accountService: AccountService;
    let router: Router;

    const generateNotification = (id: number) => {
        const generatedNotification = { id, title: 'Quiz started', text: 'Quiz "Proxy pattern" just started.' } as Notification;
        generatedNotification.target = JSON.stringify({ mainPage: 'courses', course: 1, entity: 'exercise', id: 1 });
        return generatedNotification;
    };
    const notification = generateNotification(1);

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [NotificationPopupComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                notificationPopupComponentFixture = TestBed.createComponent(NotificationPopupComponent);
                notificationPopupComponent = notificationPopupComponentFixture.componentInstance;
                notificationService = TestBed.inject(NotificationService);
                accountService = TestBed.inject(AccountService);
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
        beforeEach(() => {
            notificationPopupComponent.notifications.push(notification);
            notificationPopupComponentFixture.detectChanges();
        });

        it('should remove notification from component state and navigate to target when notification is clicked', () => {
            sinon.spy(notificationPopupComponent, 'removeNotification');
            sinon.replace(notificationPopupComponent, 'navigateToTarget', sinon.fake());
            const notificationElement = notificationPopupComponentFixture.debugElement.query(By.css('.notification-popup-container > div'));
            notificationElement.nativeElement.click();
            expect(notificationPopupComponent.removeNotification).to.have.been.calledOnce;
            expect(notificationPopupComponent.notifications).to.be.empty;
            expect(notificationPopupComponent.navigateToTarget).to.have.been.calledOnce;
        });

        it('should remove notification from component state when notification is closed', () => {
            sinon.spy(notificationPopupComponent, 'removeNotification');
            sinon.replace(notificationPopupComponent, 'navigateToTarget', sinon.fake());
            const button = notificationPopupComponentFixture.debugElement.query(By.css('.notification-popup-container > div button'));
            button.nativeElement.click();
            expect(notificationPopupComponent.removeNotification).to.have.been.called;
            expect(notificationPopupComponent.notifications).to.be.empty;
        });

        it('should navigate to target when notification is clicked', () => {
            sinon.spy(notificationPopupComponent, 'navigateToTarget');
            sinon.replace(router, 'navigateByUrl', sinon.fake());
            const button = notificationPopupComponentFixture.debugElement.query(By.css('.notification-popup-container > div button'));
            button.nativeElement.click();
            expect(notificationPopupComponent.navigateToTarget).to.have.been.calledOnce;
            expect(router.navigateByUrl).to.have.been.calledOnce;
        });
    });

    describe('Webscoket receive', () => {
        const replaceSubscribeToNotificationUpdates = () => {
            const fake = sinon.fake.returns(new BehaviorSubject(notification));
            sinon.replace(notificationService, 'subscribeToNotificationUpdates', fake);
        };

        it('should prepend received quiz notification', fakeAsync(() => {
            sinon.replace(router, 'isActive', sinon.fake.returns(false));
            replaceSubscribeToNotificationUpdates();
            const otherNotification = generateNotification(2);
            notificationPopupComponent.notifications = [otherNotification];
            notificationPopupComponent.ngOnInit();
            expect(notificationPopupComponent.notifications.length).to.be.equal(2);
            expect(notificationPopupComponent.notifications[0]).to.be.equal(notification);
            tick(30000);
            expect(notificationPopupComponent.notifications.length).to.be.equal(1);
            expect(notificationPopupComponent.notifications[0]).to.be.equal(otherNotification);
        }));

        it('should not add received notification if user is already on quiz page', () => {
            sinon.replace(router, 'isActive', sinon.fake.returns(true));
            replaceSubscribeToNotificationUpdates();
            notificationPopupComponent.ngOnInit();
            expect(notificationPopupComponent.notifications).to.be.empty;
        });
    });
});
