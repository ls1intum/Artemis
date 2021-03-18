import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import * as moment from 'moment';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import * as sinon from 'sinon';
import { HttpResponse } from '@angular/common/http';
import { JhiAlertService, JhiTranslateDirective } from 'ng-jhipster';
import { MockRouterLinkDirective } from '../lecture-unit/lecture-unit-management.component.spec';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';

describe('Course Exam Archive Button Component', () => {
    let comp: CourseExamArchiveButtonComponent;
    let fixture: ComponentFixture<CourseExamArchiveButtonComponent>;
    let courseManagementService: CourseManagementService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseExamArchiveButtonComponent,
                MockComponent(SecuredImageComponent),
                MockRouterLinkDirective,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(DeleteButtonDirective),
                MockComponent(AlertErrorComponent),
                MockDirective(AlertComponent),
                MockPipe(ArtemisDatePipe),
                MockDirective(JhiTranslateDirective),
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
        fixture = TestBed.createComponent(CourseExamArchiveButtonComponent);
        comp = fixture.componentInstance;
        courseManagementService = TestBed.inject(CourseManagementService);
    });

    describe('OnInit for not instructor', () => {
        const course = { id: 123, isAtLeastInstructor: false };

        beforeEach(() => {
            comp.archiveMode = 'Course';
            comp.course = course;
        });

        it('should not display archiving and cleanup controls', fakeAsync(() => {
            comp.ngOnInit();
            tick();

            expect(comp.canArchive()).toEqual(false);
            expect(comp.canCleanupCourse()).toEqual(false);
            expect(comp.canDownloadArchive()).toEqual(false);
        }));
    });

    describe('OnInit for course that is not over', () => {
        const course = { id: 123, endDate: moment().subtract(5, 'minutes') };

        beforeEach(() => {
            comp.archiveMode = 'Course';
            comp.course = course;
        });

        it('should not display archiving and cleanup controls', fakeAsync(() => {
            comp.ngOnInit();
            tick();

            expect(comp.canArchive()).toEqual(false);
            expect(comp.canCleanupCourse()).toEqual(false);
            expect(comp.canDownloadArchive()).toEqual(false);
        }));
    });

    describe('OnInit for course that has no archive', () => {
        const course = { id: 123, isAtLeastInstructor: true, endDate: moment().subtract(5, 'minutes') };

        beforeEach(() => {
            comp.archiveMode = 'Course';
            comp.course = course;
        });

        it('should not display an archive course button', fakeAsync(() => {
            comp.ngOnInit();
            tick();

            expect(comp.canArchive()).toEqual(true);
            expect(comp.canCleanupCourse()).toEqual(false);
            expect(comp.canDownloadArchive()).toEqual(false);
        }));
    });

    describe('OnInit for course that has an archive', () => {
        const course = { id: 123, isAtLeastInstructor: true, endDate: moment().subtract(5, 'minutes'), courseArchivePath: 'some-path' };

        beforeEach(fakeAsync(() => {
            comp.archiveMode = 'Course';
            comp.course = course;

            comp.ngOnInit();
            tick();
        }));

        afterEach(function () {
            sinon.restore();
        });

        it('should not display an archive course button', fakeAsync(() => {
            expect(comp.canArchive()).toEqual(true);
            expect(comp.canCleanupCourse()).toEqual(true);
            expect(comp.canDownloadArchive()).toEqual(true);
        }));

        it('should cleanup archive for course', fakeAsync(() => {
            const response: HttpResponse<void> = new HttpResponse({ status: 200 });
            const cleanupStub = sinon.stub(courseManagementService, 'cleanupCourse').returns(of(response));

            const alertService = TestBed.inject(JhiAlertService);
            const alertServiceSpy = sinon.spy(alertService, 'success');

            comp.cleanupCourse();

            expect(cleanupStub).toHaveBeenCalled();
            expect(alertServiceSpy).toHaveBeenCalled();
        }));

        it('should download archive for course', fakeAsync(() => {
            const response: HttpResponse<Blob> = new HttpResponse({ status: 200 });
            const downloadStub = sinon.stub(courseManagementService, 'downloadCourseArchive').returns(of(response));

            comp.downloadArchive();

            expect(downloadStub).toHaveBeenCalled();
        }));

        it('should archive course', fakeAsync(() => {
            const response: HttpResponse<void> = new HttpResponse({ status: 200 });
            const downloadStub = sinon.stub(courseManagementService, 'archiveCourse').returns(of(response));

            comp.archive();

            expect(downloadStub).toHaveBeenCalled();
        }));
    });
});
