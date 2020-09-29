import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TestRunManagementComponent } from 'app/exam/manage/test-runs/test-run-management.component';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { Exam } from 'app/entities/exam.model';
import { User } from 'app/core/user/user.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { Course } from 'app/entities/course.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockActiveModal } from '../../../helpers/mocks/service/mock-active-modal.service';
import * as sinon from 'sinon';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

describe('Test Run Management Component', () => {
    let comp: TestRunManagementComponent;
    let fixture: ComponentFixture<TestRunManagementComponent>;
    let examManagementService: ExamManagementService;
    let accountService: AccountService;

    const course = { id: 1 } as Course;
    const exam = { id: 1, course, started: true } as Exam;
    const user = { id: 99 } as User;
    const studentExams = [
        { id: 1, user: { id: 99 } },
        { id: 2, user: { id: 90 } },
    ] as StudentExam[];
    const route = ({ snapshot: { paramMap: convertToParamMap({ courseId: course.id, examId: exam.id }) } } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TestRunManagementComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: NgbModal, useClass: MockActiveModal },
            ],
        })
            .overrideTemplate(TestRunManagementComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(TestRunManagementComponent);
        comp = fixture.componentInstance;
        examManagementService = fixture.debugElement.injector.get(ExamManagementService);
        accountService = fixture.debugElement.injector.get(AccountService);
    });

    describe('onInit', () => {
        it('should fetch exam with test runs and user on init', fakeAsync(() => {
            // GIVEN
            spyOn(examManagementService, 'find').and.returnValue(of(new HttpResponse({ body: exam })));
            spyOn(examManagementService, 'findAllTestRunsForExam').and.returnValue(of(new HttpResponse({ body: studentExams })));
            spyOn(accountService, 'fetch').and.returnValue(of(new HttpResponse({ body: user })));

            // WHEN
            comp.ngOnInit();
            tick(); // simulate async

            // THEN
            expect(examManagementService.find).toHaveBeenCalledWith(course.id, exam.id, false, true);
            expect(examManagementService.findAllTestRunsForExam).toHaveBeenCalledWith(course.id, exam.id);
            expect(accountService.fetch).toHaveBeenCalledWith();

            expect(comp.exam).toEqual(exam);
            expect(comp.isExamStarted).toEqual(exam.started);
            expect(comp.course).toEqual(course);
            expect(comp.testRuns).toEqual(studentExams);
            expect(comp.instructor).toEqual(user);
        }));
    });
    describe('Delete', () => {
        it('should call delete for test run', fakeAsync(() => {
            comp.testRuns = studentExams;
            comp.course = course;
            comp.exam = exam;
            const responseFakeDelete = {} as HttpResponse<StudentExam>;
            sinon.replace(examManagementService, 'deleteTestRun', sinon.fake.returns(of(responseFakeDelete)));
            spyOn(examManagementService, 'deleteTestRun').and.returnValue(of(responseFakeDelete));

            // WHEN
            comp.deleteTestRun(studentExams[0].id);

            // THEN
            expect(examManagementService.deleteTestRun).toHaveBeenCalledWith(course.id, exam.id, studentExams[0].id);
        }));
    });

    describe('Create test runs', () => {
        it('Test Run cannot be created because the exam contains no exercises', fakeAsync(() => {
            comp.exam = exam;
            fixture.detectChanges();
            expect(comp.examContainsExercises).toBeFalsy();
        }));
        it('Test Run can can be created', fakeAsync(() => {
            const exercise = { id: 1 } as Exercise;
            const exerciseGroup = { id: 1, exercises: [exercise] } as ExerciseGroup;
            exam.exerciseGroups = [exerciseGroup];
            comp.exam = exam;
            fixture.detectChanges();
            expect(comp.examContainsExercises).toBeTruthy();
        }));
    });

    describe('Assessment of test runs', () => {
        it('Test Run cannot be assessed because the logged in user does not have a test run which is submitted', fakeAsync(() => {
            comp.testRuns = studentExams;
            comp.testRuns[0].submitted = false;
            fixture.detectChanges();
            expect(comp.testRunCanBeAssessed).toBeFalsy();
        }));
        it('Test Run can be assessed', fakeAsync(() => {
            comp.testRuns = studentExams;
            comp.testRuns[0].submitted = true;
            fixture.detectChanges();
            expect(comp.testRunCanBeAssessed).toBeTruthy();
        }));
    });
});
