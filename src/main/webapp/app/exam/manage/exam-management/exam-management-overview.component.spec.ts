import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import { ExamManagementOverviewComponent } from 'app/exam/manage/exam-management/exam-management-overview.component';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { SortService } from 'app/foundation/service/sort.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamInformationDTO } from 'app/exam/shared/entities/exam-information.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';

describe('ExamManagementOverviewComponent', () => {
    let comp: ExamManagementOverviewComponent;
    let fixture: ComponentFixture<ExamManagementOverviewComponent>;
    let examManagementService: ExamManagementService;
    let courseService: CourseManagementService;
    let eventManager: EventManager;
    let alertService: AlertService;
    let sortService: SortService;
    let dialogService: DialogService;
    let router: Router;

    const course: Course = { id: 456, isAtLeastInstructor: true } as Course;
    const exam1: Exam = { id: 1, title: 'Exam 1', testExam: false } as Exam;
    const exam2: Exam = { id: 2, title: 'Exam 2', testExam: true } as Exam;

    const route = {
        parent: {
            snapshot: {
                paramMap: convertToParamMap({ courseId: course.id }),
            },
        },
    } as any as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamManagementOverviewComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
                EventManager,
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamManagementOverviewComponent);
        comp = fixture.componentInstance;
        examManagementService = TestBed.inject(ExamManagementService);
        courseService = TestBed.inject(CourseManagementService);
        eventManager = TestBed.inject(EventManager);
        alertService = TestBed.inject(AlertService);
        sortService = TestBed.inject(SortService);
        dialogService = TestBed.inject(DialogService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize and load course and exams on ngOnInit', () => {
        const courseResponse = { body: course } as HttpResponse<Course>;
        const examsResponse = { body: [exam1, exam2] } as HttpResponse<Exam[]>;
        const endDateDto: ExamInformationDTO = { latestIndividualEndDate: dayjs().add(2, 'hours') };

        vi.spyOn(courseService, 'find').mockReturnValue(of(courseResponse));
        vi.spyOn(examManagementService, 'findAllExamsForCourse').mockReturnValue(of(examsResponse));
        vi.spyOn(examManagementService, 'getLatestIndividualEndDateOfExam').mockReturnValue(of({ body: endDateDto } as HttpResponse<ExamInformationDTO>));

        comp.ngOnInit();

        expect(courseService.find).toHaveBeenCalledWith(course.id);
        expect(comp.course()).toEqual(course);
        expect(examManagementService.findAllExamsForCourse).toHaveBeenCalledWith(course.id);
        expect(examManagementService.getLatestIndividualEndDateOfExam).toHaveBeenCalledTimes(2);
        expect(comp.exams()).toHaveLength(2);
        expect(comp.exams()[0].latestIndividualEndDate).toEqual(endDateDto.latestIndividualEndDate);
    });

    it('should handle error when courseService.find fails', () => {
        const errorResponse = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
        vi.spyOn(courseService, 'find').mockReturnValue(throwError(() => errorResponse));
        const alertSpy = vi.spyOn(alertService, 'error');

        comp.ngOnInit();

        expect(alertSpy).toHaveBeenCalled();
    });

    it('should handle error when loadAllExamsForCourse fails', () => {
        const errorResponse = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
        comp.course.set(course);
        vi.spyOn(examManagementService, 'findAllExamsForCourse').mockReturnValue(throwError(() => errorResponse));
        const alertSpy = vi.spyOn(alertService, 'error');

        comp.loadAllExamsForCourse();

        expect(alertSpy).toHaveBeenCalledWith('error.http.404');
    });

    it('should reload exams on examListModification event', () => {
        comp.course.set(course);
        const examsResponse = { body: [exam1] } as HttpResponse<Exam[]>;
        const findAllSpy = vi.spyOn(examManagementService, 'findAllExamsForCourse').mockReturnValue(of(examsResponse));
        vi.spyOn(examManagementService, 'getLatestIndividualEndDateOfExam').mockReturnValue(of({ body: {} } as HttpResponse<ExamInformationDTO>));

        comp.registerChangeInExams();
        eventManager.broadcast({ name: 'examListModification', content: 'dummy' });

        expect(findAllSpy).toHaveBeenCalled();
    });

    it('should track exam by trackId', () => {
        expect(comp.trackId(0, exam1)).toBe(exam1.id);
        expect(comp.trackId(1, { id: undefined } as any)).toBeUndefined();
    });

    it('should sort rows using sortService', () => {
        comp.exams.set([exam2, exam1]);
        comp.predicate = 'id';
        comp.ascending = true;

        const sortSpy = vi.spyOn(sortService, 'sortByProperty').mockReturnValue([exam1, exam2]);

        comp.sortRows();

        expect(sortSpy).toHaveBeenCalledWith([exam2, exam1], 'id', true);
        expect(comp.exams()).toEqual([exam1, exam2]);
    });

    it('should open import modal and navigate when an exam is selected', () => {
        comp.course.set(course);
        const selectedExam: Exam = { id: 99, title: 'Imported Exam' };
        const dialogRef = {
            onClose: of(selectedExam),
        } as unknown as DynamicDialogRef;

        const openSpy = vi.spyOn(dialogService, 'open').mockReturnValue(dialogRef);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        comp.openImportModal();

        expect(openSpy).toHaveBeenCalledWith(
            ExamImportComponent,
            expect.objectContaining({
                data: { subsequentExerciseGroupSelection: false },
            }),
        );
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'exams', 'import', selectedExam.id]);
    });

    it('should open import modal and not navigate when closed without selection', () => {
        comp.course.set(course);
        const dialogRef = {
            onClose: of(undefined),
        } as unknown as DynamicDialogRef;

        vi.spyOn(dialogService, 'open').mockReturnValue(dialogRef);
        const navigateSpy = vi.spyOn(router, 'navigate');

        comp.openImportModal();

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should destroy event subscription on ngOnDestroy', () => {
        const destroySpy = vi.spyOn(eventManager, 'destroy');
        comp.eventSubscriber = eventManager.subscribe('examListModification', () => {});

        comp.ngOnDestroy();

        expect(destroySpy).toHaveBeenCalledWith(comp.eventSubscriber);
    });
});
