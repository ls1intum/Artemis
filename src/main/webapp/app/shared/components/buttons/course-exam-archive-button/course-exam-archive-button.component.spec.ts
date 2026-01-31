import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ImageComponent } from 'app/shared/image/image.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { CourseExamArchiveButtonComponent, CourseExamArchiveState } from 'app/shared/components/buttons/course-exam-archive-button/course-exam-archive-button.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DialogService } from 'primeng/dynamicdialog';

describe('Course Exam Archive Button Component', () => {
    setupTestBed({ zoneless: true });
    let comp: CourseExamArchiveButtonComponent;
    let fixture: ComponentFixture<CourseExamArchiveButtonComponent>;
    let courseManagementService: CourseManagementService;
    let examManagementService: ExamManagementService;
    let accountService: AccountService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                CourseExamArchiveButtonComponent,
                MockComponent(ImageComponent),
                MockRouterLinkDirective,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
                MockDirective(FeatureToggleDirective),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useValue: { open: vi.fn(), close: vi.fn() } },
                MockProvider(AlertService),
                MockProvider(NgbModal),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: WebsocketService, useClass: MockWebsocketService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseExamArchiveButtonComponent);
        comp = fixture.componentInstance;
        courseManagementService = TestBed.inject(CourseManagementService);
        examManagementService = TestBed.inject(ExamManagementService);
        accountService = TestBed.inject(AccountService);
    });

    describe('onInit without required properties', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('archiveMode', 'Course');
            fixture.detectChanges();
        });

        afterEach(() => {
            // Otherwise ngOnDestroy crashes
            fixture.componentRef.setInput('course', { id: 123 });
            fixture.detectChanges();
        });

        // since archiveButtonText doesnt depend on ngOnInit after migrating Angular input decorators to signals, we test the behavior of
        // what happens if nothing is set using a spy
        it('should just return (no lang subscription) when not initialized', () => {
            // Ensure inputs are absent so currentCourse/currentExam remain empty
            fixture.componentRef.setInput('course', undefined);
            fixture.componentRef.setInput('exam', undefined);
            fixture.detectChanges();

            const translate = TestBed.inject(TranslateService);
            const subSpy = vi.spyOn(translate.onLangChange, 'subscribe');

            comp.ngOnInit();

            expect(subSpy).not.toHaveBeenCalled();
        });
    });

    describe('onInit for not instructor', () => {
        const course = { id: 123, isAtLeastInstructor: false };

        beforeEach(() => {
            fixture.componentRef.setInput('archiveMode', 'Course');
            fixture.componentRef.setInput('course', course);
            fixture.detectChanges();
        });

        it('should not display archiving and cleanup controls', () => {
            comp.ngOnInit();

            expect(comp.canArchive()).toBeFalsy();
            expect(comp.canCleanup()).toBeFalsy();
            expect(comp.canDownloadArchive()).toBeFalsy();
        });
    });

    describe('onInit for course that is over', () => {
        const course = { id: 123, endDate: dayjs().subtract(5, 'minutes') };

        beforeEach(() => {
            fixture.componentRef.setInput('archiveMode', 'Course');
            fixture.componentRef.setInput('course', course);
            fixture.detectChanges();
        });

        it('should not display archiving and cleanup controls', () => {
            vi.spyOn(accountService, 'isAtLeastInstructorInCourse').mockReturnValue(false);

            comp.ngOnInit();

            expect(comp.canArchive()).toBeFalsy();
            expect(comp.canCleanup()).toBeFalsy();
            expect(comp.canDownloadArchive()).toBeFalsy();
        });
    });

    describe('onInit for course that has no archive', () => {
        const course = { id: 123, isAtLeastInstructor: true, endDate: dayjs().subtract(5, 'minutes') };

        beforeEach(() => {
            fixture.componentRef.setInput('archiveMode', 'Course');
            fixture.componentRef.setInput('course', course);
            fixture.detectChanges();
        });

        it('should not display an archive course button', () => {
            comp.ngOnInit();

            expect(comp.canArchive()).toBeTruthy();
            expect(comp.canCleanup()).toBeFalsy();
            expect(comp.canDownloadArchive()).toBeFalsy();
        });
    });

    describe('onInit for course that has an archive', () => {
        const course = { id: 123, isAtLeastInstructor: true, endDate: dayjs().subtract(5, 'minutes'), courseArchivePath: 'some-path' };

        beforeEach(() => {
            fixture.componentRef.setInput('archiveMode', 'Course');
            fixture.componentRef.setInput('course', course);
            fixture.detectChanges();
            comp.ngOnInit();
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should not display an archive course button', () => {
            expect(comp.canArchive()).toBeTruthy();
            expect(comp.canCleanup()).toBeTruthy();
            expect(comp.canDownloadArchive()).toBeTruthy();
        });

        it('should cleanup archive for course', () => {
            const response: HttpResponse<void> = new HttpResponse({ status: 200 });
            const cleanupStub = vi.spyOn(courseManagementService, 'cleanupCourse').mockReturnValue(of(response));

            const alertService = TestBed.inject(AlertService);
            const alertServiceSpy = vi.spyOn(alertService, 'success');

            comp.cleanup();

            expect(cleanupStub).toHaveBeenCalledOnce();
            expect(alertServiceSpy).toHaveBeenCalledOnce();
        });

        it('should download archive for course', () => {
            const downloadStub = vi.spyOn(courseManagementService, 'downloadCourseArchive').mockImplementation(() => {});

            comp.downloadArchive();

            expect(downloadStub).toHaveBeenCalledOnce();
        });

        it('should archive course', () => {
            const response: HttpResponse<void> = new HttpResponse({ status: 200 });
            const downloadStub = vi.spyOn(courseManagementService, 'archiveCourse').mockReturnValue(of(response));

            comp.archive();

            expect(downloadStub).toHaveBeenCalledOnce();
        });

        it('should reload course on archive complete', () => {
            const alertService = TestBed.inject(AlertService);
            const alertServiceSpy = vi.spyOn(alertService, 'success');

            vi.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ status: 200, body: course })));

            const archiveState: CourseExamArchiveState = { exportState: 'COMPLETED', message: '' };
            comp.handleArchiveStateChanges(archiveState);

            expect(comp.isBeingArchived()).toBeFalsy();
            expect(alertServiceSpy).toHaveBeenCalledOnce();
            expect(comp.course()).toBeDefined();
        });

        it('should display warning and reload course on archive complete with warnings', () => {
            const modalService = TestBed.inject(NgbModal);

            vi.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ status: 200, body: course })));
            const ngModalRef: NgbModalRef = { result: Promise.resolve('') } as any;
            vi.spyOn(modalService, 'open').mockReturnValue(ngModalRef);

            const archiveState: CourseExamArchiveState = { exportState: 'COMPLETED_WITH_WARNINGS', message: 'warning 1\nwarning 2' };
            comp.handleArchiveStateChanges(archiveState);

            expect(comp.isBeingArchived()).toBeFalsy();
            expect(comp.archiveWarnings()).toEqual(archiveState.message.split('\n'));
            expect(comp.course()).toBeDefined();
        });
    });

    describe('onInit for exam that has an archive', () => {
        const course = { id: 123, isAtLeastInstructor: true, endDate: dayjs().subtract(5, 'minutes') };
        const exam: Exam = { id: 123, endDate: dayjs().subtract(5, 'minutes'), examArchivePath: 'some-path', course };

        beforeEach(() => {
            fixture.componentRef.setInput('archiveMode', 'Exam');
            fixture.componentRef.setInput('course', course);
            fixture.componentRef.setInput('exam', exam);
            fixture.detectChanges();
            comp.ngOnInit();
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should display an archive and cleanup button', () => {
            expect(comp.canArchive()).toBeTruthy();
            expect(comp.canCleanup()).toBeTruthy();
            expect(comp.canDownloadArchive()).toBeTruthy();
        });

        it('should cleanup archive for exam', () => {
            const response: HttpResponse<void> = new HttpResponse({ status: 200 });
            const cleanupStub = vi.spyOn(courseManagementService, 'cleanupCourse').mockReturnValue(of(response));

            fixture.componentRef.setInput('archiveMode', 'Exam');
            fixture.detectChanges();
            comp.cleanup();

            expect(cleanupStub).not.toHaveBeenCalled();
            expect(comp.canCleanup()).toBeTruthy();
        });

        it('should download archive for exam', () => {
            const downloadStub = vi.spyOn(examManagementService, 'downloadExamArchive').mockImplementation(() => {});

            comp.downloadArchive();

            expect(downloadStub).toHaveBeenCalledOnce();
        });

        it('should archive course', () => {
            const response: HttpResponse<void> = new HttpResponse({ status: 200 });
            const downloadStub = vi.spyOn(examManagementService, 'archiveExam').mockReturnValue(of(response));

            comp.archive();

            expect(downloadStub).toHaveBeenCalledOnce();
        });

        it('should reload exam on archive complete', () => {
            const alertService = TestBed.inject(AlertService);
            const alertServiceSpy = vi.spyOn(alertService, 'success');

            vi.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ status: 200, body: exam })));

            const archiveState: CourseExamArchiveState = { exportState: 'COMPLETED', message: '' };
            comp.handleArchiveStateChanges(archiveState);

            expect(comp.isBeingArchived()).toBeFalsy();
            expect(alertServiceSpy).toHaveBeenCalledOnce();
            expect(comp.exam()).toBeDefined();
        });
    });
});
