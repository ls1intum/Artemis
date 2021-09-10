import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import * as sinon from 'sinon';
import { HttpResponse } from '@angular/common/http';
import { MockRouterLinkDirective } from '../lecture-unit/lecture-unit-management.component.spec';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CourseExamArchiveButtonComponent, CourseExamArchiveState } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap/modal/modal-ref';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/core/util/alert.service';

describe('Course Exam Archive Button Component', () => {
    let comp: CourseExamArchiveButtonComponent;
    let fixture: ComponentFixture<CourseExamArchiveButtonComponent>;
    let courseManagementService: CourseManagementService;
    let examManagementService: ExamManagementService;
    let accountService: AccountService;

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
                MockDirective(TranslateDirective),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(AlertService),
                MockProvider(NgbModal),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseExamArchiveButtonComponent);
        comp = fixture.componentInstance;
        courseManagementService = TestBed.inject(CourseManagementService);
        examManagementService = TestBed.inject(ExamManagementService);
        accountService = TestBed.inject(AccountService);
    });

    describe('OnInit without required properties', () => {
        beforeEach(() => {
            comp.archiveMode = 'Course';
        });

        it('should just return', fakeAsync(() => {
            comp.ngOnInit();
            tick();
            expect(comp.archiveButtonText).toEqual('');
        }));
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
        const course = { id: 123, endDate: dayjs().subtract(5, 'minutes') };

        beforeEach(() => {
            comp.archiveMode = 'Course';
            comp.course = course;
        });

        it('should not display archiving and cleanup controls', fakeAsync(() => {
            sinon.stub(accountService, 'isAtLeastInstructorInCourse').returns(false);

            comp.ngOnInit();
            tick();

            expect(comp.canArchive()).toEqual(false);
            expect(comp.canCleanupCourse()).toEqual(false);
            expect(comp.canDownloadArchive()).toEqual(false);
        }));
    });

    describe('OnInit for course that has no archive', () => {
        const course = { id: 123, isAtLeastInstructor: true, endDate: dayjs().subtract(5, 'minutes') };

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
        const course = { id: 123, isAtLeastInstructor: true, endDate: dayjs().subtract(5, 'minutes'), courseArchivePath: 'some-path' };

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

            const alertService = TestBed.inject(AlertService);
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

        it('should reload course on archive complete', fakeAsync(() => {
            const alertService = TestBed.inject(AlertService);
            const alertServiceSpy = sinon.spy(alertService, 'success');

            sinon.stub(courseManagementService, 'find').returns(of(new HttpResponse({ status: 200, body: course })));

            const archiveState: CourseExamArchiveState = { exportState: 'COMPLETED', message: '' };
            comp.handleArchiveStateChanges(archiveState);

            expect(comp.isBeingArchived).toBe(false);
            expect(comp.archiveButtonText).toEqual(comp.getArchiveButtonText());
            expect(alertServiceSpy).toBeCalled();
            expect(comp.course).toBeDefined();
        }));

        it('should display warning and reload course on archive complete with warnings', fakeAsync(() => {
            const modalService = TestBed.inject(NgbModal);

            sinon.stub(courseManagementService, 'find').returns(of(new HttpResponse({ status: 200, body: course })));
            const ngModalRef: NgbModalRef = { result: Promise.resolve('') } as any;
            sinon.stub(modalService, 'open').returns(ngModalRef);

            const archiveState: CourseExamArchiveState = { exportState: 'COMPLETED_WITH_WARNINGS', message: 'warning 1\nwarning 2' };
            comp.handleArchiveStateChanges(archiveState);

            expect(comp.isBeingArchived).toBe(false);
            expect(comp.archiveButtonText).toEqual(comp.getArchiveButtonText());
            expect(comp.archiveWarnings).toEqual(archiveState.message.split('\n'));
            expect(comp.course).toBeDefined();
        }));
    });

    describe('OnInit for exam that has an archive', () => {
        const course = { id: 123, isAtLeastInstructor: true, endDate: dayjs().subtract(5, 'minutes') };
        const exam: Exam = { id: 123, endDate: dayjs().subtract(5, 'minutes'), examArchivePath: 'some-path', course };

        beforeEach(fakeAsync(() => {
            comp.archiveMode = 'Exam';
            comp.course = course;
            comp.exam = exam;

            comp.ngOnInit();
            tick();
        }));

        afterEach(function () {
            sinon.restore();
        });

        it('should display an archive button', fakeAsync(() => {
            expect(comp.canArchive()).toEqual(true);
            expect(comp.canCleanupCourse()).toEqual(false);
            expect(comp.canDownloadArchive()).toEqual(true);
        }));

        it('should not cleanup archive for exam', fakeAsync(() => {
            const response: HttpResponse<void> = new HttpResponse({ status: 200 });
            const cleanupStub = sinon.stub(courseManagementService, 'cleanupCourse').returns(of(response));

            comp.archiveMode = 'Exam';
            comp.cleanupCourse();

            expect(cleanupStub).toBeCalledTimes(0);
            expect(comp.canCleanupCourse()).toBe(false);
        }));

        it('should download archive for exam', fakeAsync(() => {
            const response: HttpResponse<Blob> = new HttpResponse({ status: 200 });
            const downloadStub = sinon.stub(examManagementService, 'downloadExamArchive').returns(of(response));

            comp.downloadArchive();

            expect(downloadStub).toHaveBeenCalled();
        }));

        it('should archive course', fakeAsync(() => {
            const response: HttpResponse<void> = new HttpResponse({ status: 200 });
            const downloadStub = sinon.stub(examManagementService, 'archiveExam').returns(of(response));

            comp.archive();

            expect(downloadStub).toHaveBeenCalled();
        }));

        it('should reload exam on archive complete', fakeAsync(() => {
            const alertService = TestBed.inject(AlertService);
            const alertServiceSpy = sinon.spy(alertService, 'success');

            sinon.stub(examManagementService, 'find').returns(of(new HttpResponse({ status: 200, body: exam })));

            const archiveState: CourseExamArchiveState = { exportState: 'COMPLETED', message: '' };
            comp.handleArchiveStateChanges(archiveState);

            expect(comp.isBeingArchived).toBe(false);
            expect(comp.archiveButtonText).toEqual(comp.getArchiveButtonText());
            expect(alertServiceSpy).toBeCalled();
            expect(comp.exam).toBeDefined();
        }));
    });
});
