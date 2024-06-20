import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, UrlSegment, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamStudentsComponent } from 'app/exam/manage/students/exam-students.component';
import { StudentsUploadImagesButtonComponent } from 'app/exam/manage/students/upload-images/students-upload-images-button.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Observable, of } from 'rxjs';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { UsersImportButtonComponent } from 'app/shared/user-import/users-import-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExamUserDTO } from 'app/entities/exam-user-dto.model';
import { ExamUser } from 'app/entities/exam-user.model';
import { StudentExamStatusComponent } from 'app/exam/manage/student-exams/student-exam-status/student-exam-status.component';
import { StudentExam } from 'app/entities/student-exam.model';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';

describe('ExamStudentsComponent', () => {
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
    const studentExam1 = new StudentExam();
    const studentExam2 = new StudentExam();
    studentExam1.user = user1;
    studentExam2.user = user2;

    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: course.id }) },
        url: new Observable<UrlSegment[]>(),
        data: { subscribe: (fn: (value: any) => void) => fn({ exam: examWithCourse }) },
    } as any as ActivatedRoute;

    let component: ExamStudentsComponent;
    let fixture: ComponentFixture<ExamStudentsComponent>;
    let examManagementService: ExamManagementService;
    let studentExamService: StudentExamService;
    let userService: UserService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, RouterTestingModule],
            declarations: [
                ExamStudentsComponent,
                MockComponent(UsersImportButtonComponent),
                MockComponent(StudentsUploadImagesButtonComponent),
                MockComponent(DataTableComponent),
                MockComponent(StudentExamStatusComponent),
                MockDirective(TranslateDirective),
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: ArtemisTranslatePipe, useClass: ArtemisTranslatePipe },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamStudentsComponent);
        component = fixture.componentInstance;
        examManagementService = TestBed.inject(ExamManagementService);
        studentExamService = TestBed.inject(StudentExamService);
        userService = TestBed.inject(UserService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
        fixture.destroy();
    });

    it('should initialize', () => {
        const examServiceStub = jest.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: examWithCourse })));
        fixture.detectChanges();
        expect(examServiceStub).toHaveBeenCalledOnce();
        expect(component).not.toBeNull();
        expect(component.courseId).toEqual(course.id);
        expect(component.exam).toEqual(examWithCourse);
        expect(component.isLoading).toBeFalse();
    });

    it('should handle auto-complete for user without login', () => {
        const callbackSpy = jest.fn();
        const examServiceStub = jest.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: examWithCourse })));
        const fakeResponseStudentExams = { body: [studentExam1, studentExam2] } as HttpResponse<StudentExam[]>;
        const studentExamServiceStub = jest.spyOn(studentExamService, 'findAllForExam').mockReturnValue(of(fakeResponseStudentExams));
        fixture.detectChanges();

        component.onAutocompleteSelect(user1, callbackSpy);
        fixture.detectChanges();
        expect(examServiceStub).toHaveBeenCalledOnce();
        expect(studentExamServiceStub).toHaveBeenCalledOnce();
        expect(callbackSpy).toHaveBeenCalledWith(user1);
    });

    it('should handle auto-complete for unregistered user', () => {
        const user3 = { id: 3, login: 'user3' } as User;
        const student3 = { login: 'user3', firstName: 'student2', lastName: 'student2', registrationNumber: '1234567' } as ExamUserDTO;
        const studentExam3 = { user: user3 };
        const callbackSpy = jest.fn();
        const flashSpy = jest.spyOn(component, 'flashRowClass');
        const reloadSpy = jest.spyOn(component, 'loadAll');
        const addStudentToExamSpy = jest.spyOn(examManagementService, 'addStudentToExam').mockReturnValue(of(new HttpResponse({ body: student3 })));
        const findExamSpy = jest.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: examWithCourse })));
        const fakeResponseStudentExams = { body: [studentExam1, studentExam2, studentExam3] } as HttpResponse<StudentExam[]>;
        const studentExamServiceStub = jest.spyOn(studentExamService, 'findAllForExam').mockReturnValue(of(fakeResponseStudentExams));
        fixture.detectChanges();

        component.onAutocompleteSelect(user3, callbackSpy);
        fixture.detectChanges();

        expect(addStudentToExamSpy).toHaveBeenCalledWith(course.id, examWithCourse.id, user3.login);
        expect(findExamSpy).toHaveBeenCalledTimes(2);
        expect(reloadSpy).toHaveBeenCalledTimes(2);
        expect(studentExamServiceStub).toHaveBeenCalledTimes(2);
        expect(callbackSpy).not.toHaveBeenCalled();
        expect(flashSpy).toHaveBeenCalledOnce();
        expect(component.isTransitioning).toBeFalse();
        expect(component.hasStudentsWithoutExam).toBeFalse();
    });

    it('should search for users', () => {
        const userServiceStub = jest.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [user2] })));
        fixture.detectChanges();

        const search = component.searchAllUsers(of({ text: user2.login!, entities: [user2] }));
        fixture.detectChanges();

        // Check if the observable output matches our expectancies
        search.subscribe((a) => {
            expect(a).toEqual([{ id: user2.id, login: user2.login }]);
            expect(component.searchNoResults).toBeFalse();
            expect(component.searchFailed).toBeFalse();
        });

        expect(userServiceStub).toHaveBeenCalledOnce();
    });

    it('should reload with only registered users', () => {
        // Same id is intentional: Simulate one user got removed
        const examWithOneUser = {
            course,
            id: 2,
            examUsers: [{ didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user2, user: user2 } as ExamUser],
        };
        const examServiceStub = jest.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: examWithOneUser })));
        const fakeResponseStudentExams = { body: [studentExam2] } as HttpResponse<StudentExam[]>;
        const studentExamServiceStub = jest.spyOn(studentExamService, 'findAllForExam').mockReturnValue(of(fakeResponseStudentExams));
        fixture.detectChanges();

        component.loadAll();
        fixture.detectChanges();

        expect(studentExamServiceStub).toHaveBeenCalledTimes(2);
        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, true);
        expect(component.exam).toEqual(examWithOneUser);
        expect(component.allRegisteredUsers).toEqual([
            {
                didCheckImage: false,
                didCheckLogin: false,
                didCheckName: false,
                didCheckRegistrationNumber: false,
                didExamUserAttendExam: false,
                ...user2,
                user: user2,
                studentExam: studentExam2,
            },
        ]);
    });

    it('should remove users from the exam', () => {
        const examServiceStub = jest.spyOn(examManagementService, 'removeStudentFromExam').mockReturnValue(of(new HttpResponse()));
        fixture.detectChanges();
        component.allRegisteredUsers = [
            { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user1, user: user1 },
            { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user2, user: user2 },
        ] as ExamUser[];

        component.removeFromExam(
            { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user2, user: user2 },
            { deleteParticipationsAndSubmission: false },
        );
        fixture.detectChanges();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, user2.login, false);
        expect(component.allRegisteredUsers).toEqual([
            { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user1, user: user1 },
        ]);
    });

    it('should register all enrolled students of the course to the exam', () => {
        const examServiceStubAddAll = jest.spyOn(examManagementService, 'addAllStudentsOfCourseToExam').mockReturnValue(of(new HttpResponse<void>()));
        const examWithOneUser = {
            course,
            id: 2,
            examUsers: [{ didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user2, user: user2 } as ExamUser],
        };
        const examServiceStub = jest.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: examWithOneUser })));
        const fakeResponseStudentExams = { body: [studentExam2] } as HttpResponse<StudentExam[]>;
        const studentExamServiceStub = jest.spyOn(studentExamService, 'findAllForExam').mockReturnValue(of(fakeResponseStudentExams));
        fixture.detectChanges();

        component.exam = examWithCourse;
        component.registerAllStudentsFromCourse();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, true);
        expect(examServiceStubAddAll).toHaveBeenCalledWith(course.id, examWithCourse.id);
        expect(studentExamServiceStub).toHaveBeenCalledTimes(2);
        expect(component.hasStudentsWithoutExam).toBeFalse();
        expect(component.allRegisteredUsers).toEqual([
            {
                didCheckImage: false,
                didCheckLogin: false,
                didCheckName: false,
                didCheckRegistrationNumber: false,
                didExamUserAttendExam: false,
                ...user2,
                user: user2,
                studentExam: studentExam2,
            },
        ]);
    });

    it('should remove all users from the exam', () => {
        const examServiceStub = jest.spyOn(examManagementService, 'removeAllStudentsFromExam').mockReturnValue(of(new HttpResponse()));
        fixture.detectChanges();
        component.allRegisteredUsers = [
            { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user1, user: user1 },
            { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user2, user: user2 },
        ] as ExamUser[];

        component.removeAllStudents({ deleteParticipationsAndSubmission: false });
        fixture.detectChanges();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, false);
        expect(component.allRegisteredUsers).toEqual([]);
    });

    it('should remove all users from the exam with participaations', () => {
        const examServiceStub = jest.spyOn(examManagementService, 'removeAllStudentsFromExam').mockReturnValue(of(new HttpResponse()));
        fixture.detectChanges();
        component.allRegisteredUsers = [
            { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user1, user: user1 },
            { didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false, ...user2, user: user2 },
        ] as ExamUser[];

        component.removeAllStudents({ deleteParticipationsAndSubmission: true });
        fixture.detectChanges();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, true);
        expect(component.allRegisteredUsers).toEqual([]);
    });

    it('should format search result', () => {
        const resultString = component.userSearchResultFormatter(user1);
        expect(resultString).toBe('name (login)');
    });

    it('should format search text from user', () => {
        const resultString = component.searchTextFromUser(user1);
        expect(resultString).toBe('login');
    });

    it('should test on error', () => {
        component.onError('ErrorString');
        expect(component.isTransitioning).toBeFalse();
    });
});
