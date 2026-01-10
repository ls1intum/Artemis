import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { Observable, Subject, of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router, UrlSegment, convertToParamMap } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExamManagementComponent } from 'app/exam/manage/exam-management/exam-management.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { SortService } from 'app/shared/service/sort.service';
import { ExamInformationDTO } from 'app/exam/shared/entities/exam-information.model';
import { EventManager } from 'app/shared/service/event-manager.service';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { DurationPipe } from 'app/shared/pipes/artemis-duration.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('Exam Management Component', () => {
    const course = { id: 456 } as Course;
    const exam = new Exam();
    exam.course = course;
    exam.id = 123;

    let comp: ExamManagementComponent;
    let fixture: ComponentFixture<ExamManagementComponent>;
    let service: ExamManagementService;
    let courseManagementService: CourseManagementService;
    let sortService: SortService;
    let eventManager: EventManager;
    let dialogService: DialogService;
    let router: Router;

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) }, url: new Observable<UrlSegment[]>() } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                ExamManagementComponent,
                MockDirective(HasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockRouterLinkDirective,
                MockDirective(SortDirective),
                MockPipe(DurationPipe),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [
                SessionStorageService,
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
                EventManager,
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamManagementComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(ExamManagementService);
        courseManagementService = TestBed.inject(CourseManagementService);
        sortService = TestBed.inject(SortService);
        eventManager = TestBed.inject(EventManager);
        dialogService = TestBed.inject(DialogService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    it('should call find of courseManagementService to get course on init', () => {
        // GIVEN
        const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
        jest.spyOn(courseManagementService, 'find').mockReturnValue(of(responseFakeCourse));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(courseManagementService.find).toHaveBeenCalledOnce();
        expect(comp.course).toEqual(course);
    });

    it('should call loadAllExamsForCourse on init', () => {
        // GIVEN
        const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
        jest.spyOn(courseManagementService, 'find').mockReturnValue(of(responseFakeCourse));
        const responseFakeExams = { body: [exam] } as HttpResponse<Exam[]>;
        jest.spyOn(service, 'findAllExamsForCourse').mockReturnValue(of(responseFakeExams));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(service.findAllExamsForCourse).toHaveBeenCalledOnce();
        expect(comp.exams).toEqual([exam]);
    });

    it('should call getLatestIndividualDate on init', () => {
        // GIVEN
        const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
        jest.spyOn(courseManagementService, 'find').mockReturnValue(of(responseFakeCourse));
        const responseFakeExams = { body: [exam] } as HttpResponse<Exam[]>;
        jest.spyOn(service, 'findAllExamsForCourse').mockReturnValue(of(responseFakeExams));

        const examInformationDTO = new ExamInformationDTO();
        examInformationDTO.latestIndividualEndDate = dayjs();
        const responseFakeLatestIndividualEndDateOfExam = { body: examInformationDTO } as HttpResponse<ExamInformationDTO>;
        jest.spyOn(service, 'getLatestIndividualEndDateOfExam').mockReturnValue(of(responseFakeLatestIndividualEndDateOfExam));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(service.getLatestIndividualEndDateOfExam).toHaveBeenCalledOnce();
        expect(comp.exams[0].latestIndividualEndDate).toEqual(examInformationDTO.latestIndividualEndDate);
    });

    it('should call findAllExamsForCourse on examListModification event being fired after registering for exam changes', () => {
        // GIVEN
        comp.course = course;
        const responseFakeExams = { body: [exam] } as HttpResponse<Exam[]>;
        jest.spyOn(service, 'findAllExamsForCourse').mockReturnValue(of(responseFakeExams));

        // WHEN
        comp.registerChangeInExams();
        eventManager.broadcast({ name: 'examListModification', content: 'dummy' });

        // THEN
        expect(service.findAllExamsForCourse).toHaveBeenCalledOnce();
        expect(comp.exams).toEqual([exam]);
    });

    it('should return false for examHasFinished when component has no exam information', () => {
        // GIVEN
        exam.latestIndividualEndDate = undefined;

        // WHEN
        const examHasFinished = comp.examHasFinished(exam);

        // THEN
        expect(examHasFinished).toBeFalse();
    });

    it('should return true for examHasFinished when exam is in the past', () => {
        // GIVEN
        exam.latestIndividualEndDate = dayjs().subtract(1, 'days');

        // WHEN
        const examHasFinished = comp.examHasFinished(exam);

        // THEN
        expect(examHasFinished).toBeTrue();
    });

    it('should return false for examHasFinished when exam is in the future', () => {
        // GIVEN
        exam.latestIndividualEndDate = dayjs().add(1, 'minute');

        // WHEN
        const examHasFinished = comp.examHasFinished(exam);

        // THEN
        expect(examHasFinished).toBeFalse();
    });

    it('should return exam.id, when item in the exam table is being tracked', () => {
        // WHEN
        const itemId = comp.trackId(0, exam);

        // THEN
        expect(itemId).toEqual(exam.id);
    });

    it('should call sortService when sortRows is called', () => {
        // GIVEN
        jest.spyOn(sortService, 'sortByProperty').mockReturnValue([]);

        // WHEN
        comp.sortRows();

        // THEN
        expect(sortService.sortByProperty).toHaveBeenCalledOnce();
    });

    it('should open the import dialog for exams', fakeAsync(() => {
        const onCloseSubject = new Subject<Exam | undefined>();
        const mockDialogRef = { onClose: onCloseSubject.asObservable() } as DynamicDialogRef;
        jest.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        jest.spyOn(router, 'navigate');

        comp.course = { id: 1 } as Course;
        comp.openImportModal();

        // Simulate dialog closing with result
        onCloseSubject.next(exam);
        onCloseSubject.complete();
        tick();

        expect(dialogService.open).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledOnce();
    }));
});
