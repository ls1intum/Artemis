import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { TestRunManagementComponent } from 'app/exam/manage/test-runs/test-run-management/test-run-management.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortService } from 'app/shared/service/sort.service';
import { MockDirective } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/shared/service/alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('Test Run Management Component', () => {
    setupTestBed({ zoneless: true });

    let component: TestRunManagementComponent;
    let fixture: ComponentFixture<TestRunManagementComponent>;
    let examManagementService: ExamManagementService;
    let accountService: AccountService;
    let dialogService: DialogService;
    let userSpy: ReturnType<typeof vi.spyOn>;

    const course = { id: 1, isAtLeastInstructor: true } as Course;
    const exam = { id: 1, course, started: true } as Exam;
    const user = { id: 99 } as User;
    const studentExams = [
        { id: 1, user: { id: 99 } },
        { id: 2, user: { id: 90 } },
    ] as StudentExam[];
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id, examId: exam.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                MockDirective(TranslateDirective),
                { provide: DialogService, useValue: { open: vi.fn() } },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestRunManagementComponent);
                component = fixture.componentInstance;
                examManagementService = TestBed.inject(ExamManagementService);
                accountService = TestBed.inject(AccountService);
                dialogService = TestBed.inject(DialogService);
                vi.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: exam })));
                vi.spyOn(examManagementService, 'findAllTestRunsForExam').mockReturnValue(of(new HttpResponse({ body: studentExams })));
                userSpy = vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));
                vi.spyOn(accountService, 'isAtLeastInstructorInCourse').mockReturnValue(true);
                vi.spyOn(examManagementService, 'deleteTestRun').mockReturnValue(of(new HttpResponse<void>()));
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('onInit', () => {
        it('should fetch exam with test runs and user on init', async () => {
            fixture.detectChanges();

            await Promise.resolve();

            expect(examManagementService.find).toHaveBeenCalledWith(course.id!, exam.id!, false, true);
            expect(examManagementService.findAllTestRunsForExam).toHaveBeenCalledWith(course.id!, exam.id!);
            expect(userSpy).toHaveBeenCalledOnce();

            expect(component.exam()).toEqual(exam);
            expect(component.isExamStarted()).toEqual(exam.started!);
            expect(component.course()).toEqual(course);
            expect(component.testRuns()).toEqual(studentExams);
            expect(component.instructor()).toEqual(user);
        });
    });
    describe('Delete', () => {
        it('should call delete for test run', async () => {
            fixture.detectChanges();

            component.deleteTestRun(studentExams[0].id!);
            expect(examManagementService.deleteTestRun).toHaveBeenCalledWith(course.id!, exam.id!, studentExams[0].id!);
        });
    });

    describe('Create test runs', () => {
        it('should not create test run if the exam contains no exercises', () => {
            fixture.detectChanges();
            expect(component.examContainsExercises()).toBeFalsy();
        });

        it('should create test run', async () => {
            const exercise = { id: 1 } as Exercise;
            const exerciseGroup = { id: 1, exercises: [exercise] } as ExerciseGroup;
            exam.exerciseGroups = [exerciseGroup];

            const onCloseSubject = new Subject<StudentExam | undefined>();
            vi.spyOn(dialogService, 'open').mockReturnValue({ onClose: onCloseSubject.asObservable() } as DynamicDialogRef);
            vi.spyOn(examManagementService, 'createTestRun').mockReturnValue(of(new HttpResponse({ body: { id: 3, user: { id: 90 }, exercises: [exercise] } as StudentExam })));
            fixture.detectChanges();

            expect(component.examContainsExercises()).toBeTruthy();
            const createTestRunButton = fixture.debugElement.query(By.css('#createTestRunButton'));
            expect(createTestRunButton).toBeTruthy();
            expect(createTestRunButton.nativeElement.disabled).toBeFalsy();
            createTestRunButton.nativeElement.click();

            onCloseSubject.next({} as StudentExam);

            await Promise.resolve();

            expect(component.testRuns()).toHaveLength(3);
        });

        it('should correctly catch error after creating test run', async () => {
            const alertService = TestBed.inject(AlertService);
            const exercise = { id: 1 } as Exercise;
            const exerciseGroup = { id: 1, exercises: [exercise] } as ExerciseGroup;
            exam.exerciseGroups = [exerciseGroup];
            const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });

            const onCloseSubject = new Subject<StudentExam | undefined>();
            vi.spyOn(dialogService, 'open').mockReturnValue({ onClose: onCloseSubject.asObservable() } as DynamicDialogRef);
            vi.spyOn(examManagementService, 'createTestRun').mockReturnValue(throwError(() => httpError));
            vi.spyOn(alertService, 'error');
            fixture.detectChanges();

            expect(component.examContainsExercises()).toBeTruthy();
            const createTestRunButton = fixture.debugElement.query(By.css('#createTestRunButton'));
            expect(createTestRunButton).toBeTruthy();
            expect(createTestRunButton.nativeElement.disabled).toBeFalsy();
            createTestRunButton.nativeElement.click();

            onCloseSubject.next({} as StudentExam);

            await Promise.resolve();
            expect(alertService.error).toHaveBeenCalledOnce();
        });

        it('should not create test run when dialog closes without configuration', async () => {
            const exercise = { id: 1 } as Exercise;
            const exerciseGroup = { id: 1, exercises: [exercise] } as ExerciseGroup;
            exam.exerciseGroups = [exerciseGroup];

            const onCloseSubject = new Subject<StudentExam | undefined>();
            vi.spyOn(dialogService, 'open').mockReturnValue({ onClose: onCloseSubject.asObservable() } as DynamicDialogRef);
            const createTestRunSpy = vi.spyOn(examManagementService, 'createTestRun');
            fixture.detectChanges();

            const createTestRunButton = fixture.debugElement.query(By.css('#createTestRunButton'));
            createTestRunButton.nativeElement.click();
            onCloseSubject.next(undefined);

            await Promise.resolve();
            expect(createTestRunSpy).not.toHaveBeenCalled();
        });
    });

    describe('Assessment of test runs', () => {
        it('should not be able to assess test run because the logged-in user does not have a test run which is submitted', () => {
            studentExams[0].submitted = false;
            fixture.detectChanges();
            expect(component.testRunCanBeAssessed()).toBeFalsy();
        });

        it('should be able to assess test run', async () => {
            studentExams[0].submitted = true;
            fixture.detectChanges();
            await Promise.resolve();
            expect(component.testRunCanBeAssessed).toBeTruthy();
        });
    });

    describe('sort rows', () => {
        it('should forward request to', async () => {
            const sortService = TestBed.inject(SortService);
            vi.spyOn(sortService, 'sortByProperty').mockReturnValue(studentExams);
            fixture.detectChanges();

            component.sortRows();
            await Promise.resolve();
            expect(sortService.sortByProperty).toHaveBeenCalledOnce();
        });
    });
});
