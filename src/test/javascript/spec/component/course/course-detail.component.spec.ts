import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { CourseDetailComponent } from 'app/course/manage/course-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import * as moment from 'moment';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import * as sinon from 'sinon';
import { JhiAlertService, JhiTranslateDirective } from 'ng-jhipster';
import { MockRouterLinkDirective } from '../lecture-unit/lecture-unit-management.component.spec';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';

describe('Course Management Detail Component', () => {
    let comp: CourseDetailComponent;
    let fixture: ComponentFixture<CourseDetailComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseDetailComponent,
                MockComponent(SecuredImageComponent),
                MockRouterLinkDirective,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(DeleteButtonDirective),
                MockComponent(AlertErrorComponent),
                MockDirective(AlertComponent),
                MockPipe(ArtemisDatePipe),
                MockDirective(JhiTranslateDirective),
                MockComponent(CourseExamArchiveButtonComponent),
                MockDirective(HasAnyAuthorityDirective),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(JhiAlertService),
                MockProvider(NgbModal),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseDetailComponent);
        comp = fixture.componentInstance;
    });

    describe('OnInit for course that has an archive', () => {
        const course = { id: 123, isAtLeastInstructor: true, endDate: moment().subtract(5, 'minutes'), courseArchivePath: 'some-path' };

        beforeEach(fakeAsync(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.data = of({ course });

            comp.ngOnInit();
            tick();
        }));

        afterEach(function () {
            sinon.restore();
        });

        it('Should call registerChangeInCourses on init', () => {
            const registerSpy = spyOn(comp, 'registerChangeInCourses');

            fixture.detectChanges();
            comp.ngOnInit();

            expect(registerSpy).toHaveBeenCalled();
        });
    });
});
