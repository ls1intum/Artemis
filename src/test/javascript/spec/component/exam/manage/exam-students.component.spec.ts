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
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Observable, of } from 'rxjs';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { StudentDTO } from 'app/entities/student-dto.model';
import { UsersImportButtonComponent } from 'app/shared/import/users-import-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('ExamStudentsComponent', () => {
    const course = { id: 1 } as Course;
    const user1 = { id: 1, name: 'name', login: 'login' } as User;
    const user2 = { id: 2, login: 'user2' } as User;
    const examWithCourse: Exam = { course, id: 2, registeredUsers: [user1, user2] } as Exam;

    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: course.id }) },
        url: new Observable<UrlSegment[]>(),
        data: { subscribe: (fn: (value: any) => void) => fn({ exam: examWithCourse }) },
    } as any as ActivatedRoute;

    let component: ExamStudentsComponent;
    let fixture: ComponentFixture<ExamStudentsComponent>;
    let examManagementService: ExamManagementService;
    let userService: UserService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, RouterTestingModule],
            declarations: [
                ExamStudentsComponent,
                MockComponent(UsersImportButtonComponent),
                MockComponent(DataTableComponent),
                MockDirective(TranslateDirective),
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamStudentsComponent);
        component = fixture.componentInstance;
        examManagementService = TestBed.inject(ExamManagementService);
        userService = TestBed.inject(UserService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
        fixture.destroy();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.courseId).toEqual(course.id);
        expect(component.exam).toEqual(examWithCourse);
        expect(component.isLoading).toBeFalse();
    });

    it('should handle auto-complete for user without login', () => {
        const callbackSpy = jest.fn();
        fixture.detectChanges();

        component.onAutocompleteSelect(user1, callbackSpy);
        fixture.detectChanges();

        expect(callbackSpy).toHaveBeenCalledWith(user1);
    });

    it('should handle auto-complete for unregistered user', () => {
        const user3 = { id: 3, login: 'user3' } as User;
        const student3 = { login: 'user3', registrationNumber: '1234567' } as StudentDTO;
        const callbackSpy = jest.fn();
        const flashSpy = jest.spyOn(component, 'flashRowClass');
        const examServiceStub = jest.spyOn(examManagementService, 'addStudentToExam').mockReturnValue(of(new HttpResponse({ body: student3 })));
        fixture.detectChanges();

        component.onAutocompleteSelect(user3, callbackSpy);
        fixture.detectChanges();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, user3.login);
        expect(component.allRegisteredUsers).toEqual([user1, user2, user3]);
        expect(callbackSpy).toHaveBeenCalledWith(user3);
        expect(flashSpy).toHaveBeenCalledOnce();
        expect(component.isTransitioning).toBeFalse();
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
        const examWithOneUser = { course, id: 2, registeredUsers: [user2] };
        const examServiceStub = jest.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: examWithOneUser })));
        fixture.detectChanges();

        component.reloadExamWithRegisteredUsers();
        fixture.detectChanges();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, true);
        expect(component.exam).toEqual(examWithOneUser);
        expect(component.allRegisteredUsers).toEqual([user2]);
    });

    it('should remove users from the exam', () => {
        const examServiceStub = jest.spyOn(examManagementService, 'removeStudentFromExam').mockReturnValue(of(new HttpResponse()));
        fixture.detectChanges();
        component.allRegisteredUsers = [user1, user2];

        component.removeFromExam(user2, { deleteParticipationsAndSubmission: false });
        fixture.detectChanges();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, user2.login, false);
        expect(component.allRegisteredUsers).toEqual([user1]);
    });

    it('should register all enrolled students of the course to the exam', () => {
        const examServiceStubAddAll = jest.spyOn(examManagementService, 'addAllStudentsOfCourseToExam').mockReturnValue(of(new HttpResponse<void>()));
        const examWithOneUser = { course, id: 2, registeredUsers: [user2] };
        const examServiceStub = jest.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: examWithOneUser })));
        fixture.detectChanges();

        component.exam = examWithCourse;
        component.registerAllStudentsFromCourse();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, true);
        expect(examServiceStubAddAll).toHaveBeenCalledWith(course.id, examWithCourse.id);
        expect(component.allRegisteredUsers).toEqual([user2]);
    });

    it('should remove all users from the exam', () => {
        const examServiceStub = jest.spyOn(examManagementService, 'removeAllStudentsFromExam').mockReturnValue(of(new HttpResponse()));
        fixture.detectChanges();
        component.allRegisteredUsers = [user1, user2];

        component.removeAllStudents({ deleteParticipationsAndSubmission: false });
        fixture.detectChanges();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, false);
        expect(component.allRegisteredUsers).toEqual([]);
    });

    it('should remove all users from the exam with participaations', () => {
        const examServiceStub = jest.spyOn(examManagementService, 'removeAllStudentsFromExam').mockReturnValue(of(new HttpResponse()));
        fixture.detectChanges();
        component.allRegisteredUsers = [user1, user2];

        component.removeAllStudents({ deleteParticipationsAndSubmission: true });
        fixture.detectChanges();

        expect(examServiceStub).toHaveBeenCalledWith(course.id, examWithCourse.id, true);
        expect(component.allRegisteredUsers).toEqual([]);
    });

    it('should format search result', () => {
        const resultString = component.searchResultFormatter(user1);
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
