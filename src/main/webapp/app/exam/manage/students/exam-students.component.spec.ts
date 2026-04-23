import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, UrlSegment, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamStudentsComponent } from 'app/exam/manage/students/exam-students.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { Observable, of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteDialogService } from 'app/shared/delete-dialog/service/delete-dialog.service';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProvider } from 'ng-mocks';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';

// Stub component for UsersImportButtonComponent to avoid signal viewChild issues with ng-mocks
@Component({
    selector: 'jhi-user-import-button',
    template: '',
    standalone: true,
})
class UsersImportButtonStubComponent {}

describe('ExamStudentsComponent', () => {
    setupTestBed({ zoneless: true });
    const course = { id: 1 } as Course;
    const user1 = { id: 1, name: 'name', login: 'login' } as User;
    const user2 = { id: 2, login: 'user2' } as User;
    const examUser1 = new ExamUser();
    examUser1.didCheckRegistrationNumber = false;
    examUser1.didCheckLogin = false;
    examUser1.didCheckName = false;
    examUser1.didCheckImage = false;
    examUser1.user = user1;
    const examUser2 = new ExamUser();
    examUser2.didCheckRegistrationNumber = false;
    examUser2.didCheckLogin = false;
    examUser2.didCheckName = false;
    examUser2.didCheckImage = false;
    examUser2.user = user2;
    const examWithCourse: Exam = {
        course,
        id: 2,
        examUsers: [
            { ...examUser1, ...user1 },
            { ...examUser2, ...user2 },
        ],
    } as Exam;

    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: course.id }) },
        url: new Observable<UrlSegment[]>(),
        data: of({ exam: examWithCourse }),
    } as any as ActivatedRoute;

    const studentExams: StudentExam[] = [
        Object.assign(new StudentExam(), {
            id: 123,
            user: user1,
            workingTime: 3600,
            started: true,
            submitted: false,
            examSessions: [{}, {}],
        }),
    ];

    let component: ExamStudentsComponent;
    let fixture: ComponentFixture<ExamStudentsComponent>;
    let examManagementService: ExamManagementService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                FaIconComponent,
                UsersImportButtonStubComponent,
                ExamStudentsComponent,
                MockDirective(TranslateDirective),
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: AccountService, useClass: MockAccountService },
                { provide: DeleteDialogService, useClass: MockDialogService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                MockProvider(StudentExamService, {
                    findAllForExam: () => of(new HttpResponse({ body: studentExams })),
                }),
                MockProvider(ExamChecklistService, {
                    getExamStatistics: (_exam: Exam) =>
                        of({
                            numberOfExamsSubmitted: 0,
                            numberOfExamsStarted: 0,
                            numberOfTotalParticipationsForAssessment: 0,
                            existsUnassessedQuizzes: false,
                            existsUnsubmittedExercises: false,
                            allExamExercisesAllStudentsPrepared: false,
                        }),
                }),
                MockProvider(AlertService),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamStudentsComponent);
        component = fixture.componentInstance;
        examManagementService = TestBed.inject(ExamManagementService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        fixture?.destroy();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.courseId()).toEqual(course.id);
        expect(component.exam()).toEqual(examWithCourse);
        expect(component.allRegisteredUsers()[0].studentExamId).toBe(123);
        expect(component.allRegisteredUsers()[0].numberOfExamSessions).toBe(2);
        expect(component.allRegisteredUsers()[0].progress.status).toBe('started');
        expect(component.allRegisteredUsers()[1].studentExamId).toBeUndefined();
        expect(component.allRegisteredUsers()[1].progress.status).toBe('examMissing');
    });

    it('should reload with only registered users', () => {
        // Same id is intentional: Simulate one user got removed
        const examWithOneUser = {
            course,
            id: 2,
            examUsers: [{ didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user2, user: user2 } as ExamUser],
        };
        const examServiceStub = vi.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: examWithOneUser })));
        fixture.detectChanges();

        component.reloadExamWithRegisteredUsers();
        fixture.changeDetectorRef.detectChanges();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, true);
        expect(component.exam()).toEqual(examWithOneUser);
        expect(component.allRegisteredUsers()).toHaveLength(1);
        expect(component.allRegisteredUsers()[0].user?.id).toBe(user2.id);
        expect(component.allRegisteredUsers()[0].studentExamId).toBeUndefined();
        expect(component.allRegisteredUsers()[0].progress.status).toBe('examMissing');
    });

    it('should remove users from the exam', () => {
        const examServiceStub = vi.spyOn(examManagementService, 'removeStudentFromExam').mockReturnValue(of(new HttpResponse<void>()));
        fixture.detectChanges();
        component.exam.set({
            ...examWithCourse,
            examUsers: [
                { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user1, user: user1 },
                { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user2, user: user2 },
            ],
        } as Exam);
        const examAfterRemoval = {
            ...examWithCourse,
            examUsers: [{ didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user1, user: user1 } as ExamUser],
        } as Exam;
        vi.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: examAfterRemoval })));

        component.removeFromExam(
            { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user2, user: user2 },
            { deleteParticipationsAndSubmission: false },
        );
        fixture.changeDetectorRef.detectChanges();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, user2.login, false);
        expect(component.allRegisteredUsers()).toHaveLength(1);
        expect(component.allRegisteredUsers()[0].user?.id).toBe(user1.id);
    });

    it('should register all enrolled students of the course to the exam', () => {
        const examServiceStubAddAll = vi.spyOn(examManagementService, 'addAllStudentsOfCourseToExam').mockReturnValue(of(new HttpResponse<void>()));
        const examWithOneUser = {
            course,
            id: 2,
            examUsers: [{ didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user2, user: user2 } as ExamUser],
        };
        const examServiceStub = vi.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: examWithOneUser })));
        fixture.detectChanges();

        component.exam.set(examWithCourse);
        component.registerAllStudentsFromCourse();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, true);
        expect(examServiceStubAddAll).toHaveBeenCalledWith(course.id, examWithCourse.id);
        expect(component.allRegisteredUsers()).toHaveLength(1);
        expect(component.allRegisteredUsers()[0].user?.id).toBe(user2.id);
    });

    it('should remove all users from the exam', () => {
        const examServiceStub = vi.spyOn(examManagementService, 'removeAllStudentsFromExam').mockReturnValue(of(new HttpResponse<void>()));
        fixture.detectChanges();
        component.exam.set({
            ...examWithCourse,
            examUsers: [
                { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user1, user: user1 },
                { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user2, user: user2 },
            ],
        } as Exam);
        const examAfterRemoval = { ...examWithCourse, examUsers: [] } as Exam;
        vi.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: examAfterRemoval })));

        component.removeAllStudents({ deleteParticipationsAndSubmission: false });
        fixture.changeDetectorRef.detectChanges();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, false);
        expect(component.allRegisteredUsers()).toEqual([]);
    });
});
