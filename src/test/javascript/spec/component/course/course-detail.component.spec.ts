import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
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
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
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
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { JhiEventManager } from 'ng-jhipster';

chai.use(sinonChai);
const expect = chai.expect;

describe('Course Management Detail Component', () => {
    let comp: CourseDetailComponent;
    let fixture: ComponentFixture<CourseDetailComponent>;
    let courseService: CourseManagementService;
    let eventManager: JhiEventManager;
    let alertService: JhiAlertService;
    const course = { id: 123, title: 'Course Title', isAtLeastInstructor: true, endDate: moment().subtract(5, 'minutes'), courseArchivePath: 'some-path' };

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
                MockProvider(CourseManagementService),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseDetailComponent);
        comp = fixture.componentInstance;
        courseService = fixture.debugElement.injector.get(CourseManagementService);
        alertService = TestBed.inject(JhiAlertService);
        eventManager = fixture.debugElement.injector.get(JhiEventManager);
    });

    beforeEach(fakeAsync(() => {
        const route = TestBed.inject(ActivatedRoute);
        route.data = of({ course });

        comp.ngOnInit();
        tick();
    }));

    afterEach(function () {
        sinon.restore();
    });

    describe('OnInit for course that has an archive', () => {
        it('Should call registerChangeInCourses on init', () => {
            const registerSpy = sinon.spy(comp, 'registerChangeInCourses');

            fixture.detectChanges();
            comp.ngOnInit();
            expect(comp.course).to.deep.equal(course);
            expect(registerSpy).to.have.been.called;
        });
    });

    describe('Register for course', () => {
        it('should alert user if registration is successful', () => {
            const registerStub = sinon.stub(courseService, 'registerForCourse');
            const alertStub = sinon.stub(alertService, 'info');

            registerStub.returns(of(new HttpResponse({ body: { guidedTourSettings: [] } })));

            comp.registerForCourse();

            expect(registerStub).to.have.been.calledWith(course.id);
            expect(alertStub).to.have.been.calledWith(`Registered user for course ${course.title}`);
        });

        it('should alert user if registration fails', () => {
            const registerStub = sinon.stub(courseService, 'registerForCourse');
            const alertStub = sinon.stub(alertService, 'error');
            const headers = new HttpHeaders().append('X-artemisApp-message', 'errorMessage');

            registerStub.returns(throwError(new HttpErrorResponse({ headers })));

            comp.registerForCourse();

            expect(registerStub).to.have.been.calledWith(course.id);
            expect(alertStub).to.have.been.calledWith(`errorMessage`);
        });
    });

    it('should destroy event subscriber onDestroy', () => {
        comp.ngOnDestroy();
        expect(eventManager.destroy).to.have.been.called;
    });

    it('should broadcast course modification on delete', () => {
        const deleteStub = sinon.stub(courseService, 'delete');
        deleteStub.returns(of(new HttpResponse({})));

        const courseId = 444;
        comp.deleteCourse(courseId);

        expect(deleteStub).to.have.been.calledWith(courseId);
        expect(eventManager.broadcast).to.have.been.calledWith({
            name: 'courseListModification',
            content: 'Deleted an course',
        });
    });
});
