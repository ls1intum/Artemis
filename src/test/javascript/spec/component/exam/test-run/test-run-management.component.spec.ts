import { HttpClientModule, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { NgbModal, NgbModalRef, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise } from 'app/entities/exercise.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { TestRunManagementComponent } from 'app/exam/manage/test-runs/test-run-management.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { AlertService } from 'app/core/util/alert.service';

describe('Test Run Management Component', () => {
    let component: TestRunManagementComponent;
    let fixture: ComponentFixture<TestRunManagementComponent>;
    let examManagementService: ExamManagementService;
    let accountService: AccountService;
    let modalService: NgbModal;
    let userSpy: jest.SpyInstance;

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
            imports: [RouterTestingModule.withRoutes([]), MockModule(NgbModule), FontAwesomeTestingModule, TranslateModule.forRoot(), HttpClientModule],

            declarations: [
                TestRunManagementComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDurationFromSecondsPipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(SortDirective),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                MockDirective(TranslateDirective),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestRunManagementComponent);
                component = fixture.componentInstance;
                examManagementService = TestBed.inject(ExamManagementService);
                accountService = TestBed.inject(AccountService);
                modalService = modalService = TestBed.inject(NgbModal);
                jest.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: exam })));
                jest.spyOn(examManagementService, 'findAllTestRunsForExam').mockReturnValue(of(new HttpResponse({ body: studentExams })));
                userSpy = jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));
                jest.spyOn(accountService, 'isAtLeastInstructorInCourse').mockReturnValue(true);
                jest.spyOn(examManagementService, 'deleteTestRun').mockReturnValue(of(new HttpResponse({ body: {} })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('onInit', () => {
        it('should fetch exam with test runs and user on init', fakeAsync(() => {
            fixture.detectChanges();

            tick();

            expect(examManagementService.find).toHaveBeenCalledWith(course.id!, exam.id!, false, true);
            expect(examManagementService.findAllTestRunsForExam).toHaveBeenCalledWith(course.id!, exam.id!);
            expect(userSpy).toHaveBeenCalledOnce();

            expect(component.exam).toEqual(exam);
            expect(component.isExamStarted).toEqual(exam.started!);
            expect(component.course).toEqual(course);
            expect(component.testRuns).toEqual(studentExams);
            expect(component.instructor).toEqual(user);
        }));
    });
    describe('Delete', () => {
        it('should call delete for test run', fakeAsync(() => {
            fixture.detectChanges();

            component.deleteTestRun(studentExams[0].id!);
            expect(examManagementService.deleteTestRun).toHaveBeenCalledWith(course.id!, exam.id!, studentExams[0].id!);
        }));
    });

    describe('Create test runs', () => {
        it('should not create test run if the exam contains no exercises', () => {
            fixture.detectChanges();
            expect(component.examContainsExercises).toBeFalsy();
        });

        it('should create test run', () => {
            const exercise = { id: 1 } as Exercise;
            const exerciseGroup = { id: 1, exercises: [exercise] } as ExerciseGroup;
            exam.exerciseGroups = [exerciseGroup];

            const componentInstance = { title: String, text: String };
            const result = new Promise((resolve) => resolve({} as StudentExam));
            jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ componentInstance, result });
            jest.spyOn(examManagementService, 'createTestRun').mockReturnValue(of(new HttpResponse({ body: { id: 3, user: { id: 90 }, exercises: [exercise] } as StudentExam })));
            fixture.detectChanges();

            expect(component.examContainsExercises).toBeTruthy();
            const createTestRunButton = fixture.debugElement.query(By.css('#createTestRunButton'));
            expect(createTestRunButton).toBeTruthy();
            expect(createTestRunButton.nativeElement.disabled).toBeFalsy();
            createTestRunButton.nativeElement.click();
            expect(component.testRuns).toHaveLength(3);
        });

        it('should correctly catch error after creating test run', () => {
            const alertService = TestBed.inject(AlertService);
            const exercise = { id: 1 } as Exercise;
            const exerciseGroup = { id: 1, exercises: [exercise] } as ExerciseGroup;
            exam.exerciseGroups = [exerciseGroup];
            const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });

            const componentInstance = { title: String, text: String };
            const result = new Promise((resolve) => resolve({} as StudentExam));
            jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ componentInstance, result });
            jest.spyOn(examManagementService, 'createTestRun').mockReturnValue(throwError(() => httpError));
            jest.spyOn(alertService, 'error');
            fixture.detectChanges();

            expect(component.examContainsExercises).toBeTruthy();
            const createTestRunButton = fixture.debugElement.query(By.css('#createTestRunButton'));
            expect(createTestRunButton).toBeTruthy();
            expect(createTestRunButton.nativeElement.disabled).toBeFalsy();
            createTestRunButton.nativeElement.click();
            expect(alertService.error).toHaveBeenCalled();
        });
    });

    describe('Assessment of test runs', () => {
        it('should not be able to assess test run because the logged-in user does not have a test run which is submitted', () => {
            studentExams[0].submitted = false;
            fixture.detectChanges();
            expect(component.testRunCanBeAssessed).toBeFalsy();
        });

        it('should be able to assess test run', fakeAsync(() => {
            studentExams[0].submitted = true;
            fixture.detectChanges();
            tick();
            expect(component.testRunCanBeAssessed).toBeTruthy();
        }));
    });

    describe('sort rows', () => {
        it('should forward request to', fakeAsync(() => {
            const sortService = TestBed.inject(SortService);
            jest.spyOn(sortService, 'sortByProperty').mockReturnValue(studentExams);
            fixture.detectChanges();

            component.sortRows();
            tick();
            expect(sortService.sortByProperty).toHaveBeenCalled();
        }));
    });
});
