import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TestRunManagementComponent } from 'app/exam/manage/test-runs/test-run-management.component';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { Exam } from 'app/entities/exam.model';
import { User } from 'app/core/user/user.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { Course } from 'app/entities/course.model';

describe('Test Run Management Component', () => {
    let comp: TestRunManagementComponent;
    let fixture: ComponentFixture<TestRunManagementComponent>;
    let examManagementService: ExamManagementService;
    let accountService: AccountService;

    const course = { id: 1 } as Course;
    const exam = { id: 1, course, started: true } as Exam;
    const user = { id: 1 } as User;
    const studentExams = [{ id: 1 }, { id: 2 }] as StudentExam[];
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
        it('should fetch exam with exercises and user on init', fakeAsync(() => {
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
});
