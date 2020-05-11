import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { NotificationSidebarComponent } from 'app/shared/notification/notification-sidebar/notification-sidebar.component';
import { NotificationService } from 'app/shared/notification/notification.service';
import { ArtemisTestModule } from '../../../test.module';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockNotificationService } from '../../../helpers/mocks/service/mock-notification.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Notification } from 'app/entities/notification.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('Notification Sidebar Component', () => {
    let notificationSidebarComponent: NotificationSidebarComponent;
    let notificationSidebarComponentFixture: ComponentFixture<NotificationSidebarComponent>;
    let notificationService: NotificationService;

    const notification1 = { id: 1 } as Notification;
    const notifications = [notification1] as Notification[];
    const queryResponse = {
        body: notifications,
        headers: new HttpHeaders({
            'X-Total-Count': notifications.length.toString(),
        }),
    } as HttpResponse<Notification[]>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule],
            declarations: [NotificationSidebarComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                notificationSidebarComponentFixture = TestBed.createComponent(NotificationSidebarComponent);
                notificationSidebarComponent = notificationSidebarComponentFixture.componentInstance;
                notificationService = notificationSidebarComponentFixture.debugElement.injector.get(NotificationService);
            });
    });

    describe('Initialization', () => {
        it('should query notifications', fakeAsync(() => {
            sinon.spy(notificationService, 'query');
            notificationSidebarComponent.ngOnInit();
            tick(500);
            expect(notificationService.query).to.have.been.calledOnce;
        }));

        it('should subscribe to notification updates for user', fakeAsync(() => {
            sinon.spy(notificationService, 'subscribeUserNotifications');
            sinon.spy(notificationService, 'subscribeToSocketMessages');
            notificationSidebarComponent.ngOnInit();
            tick(500);
            expect(notificationService.subscribeUserNotifications).to.have.been.calledOnce;
            expect(notificationService.subscribeToSocketMessages).to.have.been.calledOnce;
        }));
    });

    describe('Sidebar visibility', () => {
        it('should open sidebar when user clicks on notification bell', () => {
            sinon.spy(notificationSidebarComponent, 'toggleSidebar');
            const bell = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-button');
            bell.click();
            expect(notificationSidebarComponent.toggleSidebar).to.have.been.calledOnce;
            expect(notificationSidebarComponent.showSidebar).to.be.true;
        });

        it('should close sidebar when user clicks on notification overlay', () => {
            notificationSidebarComponent.showSidebar = true;
            sinon.spy(notificationSidebarComponent, 'toggleSidebar');
            const overlay = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-overlay');
            overlay.click();
            expect(notificationSidebarComponent.toggleSidebar).to.have.been.calledOnce;
            expect(notificationSidebarComponent.showSidebar).to.be.false;
        });

        it('should close sidebar when user clicks on close button', () => {
            notificationSidebarComponent.showSidebar = true;
            sinon.spy(notificationSidebarComponent, 'toggleSidebar');
            const close = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.close');
            close.click();
            expect(notificationSidebarComponent.toggleSidebar).to.have.been.calledOnce;
            expect(notificationSidebarComponent.showSidebar).to.be.false;
        });

        it('should close sidebar when user clicks on a notification', () => {
            notificationSidebarComponent.sortedNotifications = notifications;
            notificationSidebarComponentFixture.detectChanges();
            notificationSidebarComponent.showSidebar = true;
            const notification = notificationSidebarComponentFixture.debugElement.nativeElement.querySelector('.notification-item');
            notification.click();
            expect(notificationSidebarComponent.showSidebar).to.be.false;
        });
    });
});
