import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementComponent } from 'app/course/manage/course-management.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTestModule } from '../../test.module';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../helpers/mocks/service/mock-notification.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { UserService } from 'app/core/user/user.service';
import { MockUserService } from '../../helpers/mocks/service/mock-user.service';
import { MockAlertService } from '../../helpers/mocks/service/mock-alert.service';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { MockEventManager } from '../../helpers/mocks/service/mock-event-manager.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { SessionStorageService } from 'ngx-webstorage';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseManagementComponent', () => {
    let fixture: ComponentFixture<CourseManagementComponent>;
    let component: CourseManagementComponent;
    let service: CourseManagementService;
    let alertService: JhiAlertService;
    let eventManager: JhiEventManager;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseManagementComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .overrideTemplate(CourseManagementComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(CourseManagementService);
                alertService = TestBed.inject(JhiAlertService);
                eventManager = TestBed.inject(JhiEventManager);
            });
    });

    afterEach(async () => {
        sinon.restore();
    });

    it('should initialize', async () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });
});
