import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, UrlSegment } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamStudentsComponent } from 'app/exam/manage/students/exam-students.component';
import { StudentsExamImportButtonComponent } from 'app/exam/manage/students/students-exam-import-dialog/students-exam-import-button.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import { MockComponent, MockDirective } from 'ng-mocks';
import { MockPipe } from 'ng-mocks';
import { Observable, of } from 'rxjs';
import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { StudentDTO } from 'app/entities/student-dto.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

chai.use(sinonChai);
const expect = chai.expect;

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
                MockComponent(StudentsExamImportButtonComponent),
                MockComponent(DataTableComponent),
                MockComponent(AlertComponent),
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

    afterEach(function () {
        sinon.restore();
        fixture.destroy();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
        expect(component.courseId).to.equal(course.id);
        expect(component.exam).to.deep.equal(examWithCourse);
        expect(component.isLoading).to.be.false;
    });

    it('should handle auto-complete for user without login', () => {
        const callbackSpy = sinon.spy();
        fixture.detectChanges();

        component.onAutocompleteSelect(user1, callbackSpy);
        fixture.detectChanges();

        expect(callbackSpy).to.have.been.calledOnceWith(user1);
    });

    it('should handle auto-complete for unregistered user', () => {
        const user3 = { id: 3, login: 'user3' } as User;
        const student3 = { login: 'user3', registrationNumber: '1234567' } as StudentDTO;
        const callbackSpy = sinon.spy();
        const flashSpy = sinon.spy(component, 'flashRowClass');
        const examServiceStub = sinon.stub(examManagementService, 'addStudentToExam').returns(of(new HttpResponse({ body: student3 })));
        fixture.detectChanges();

        component.onAutocompleteSelect(user3, callbackSpy);
        fixture.detectChanges();

        expect(examServiceStub).to.have.been.calledOnceWith(course.id, examWithCourse.id, user3.login);
        expect(component.allRegisteredUsers).to.deep.equal([user1, user2, user3]);
        expect(callbackSpy).to.have.been.calledOnceWith(user3);
        expect(flashSpy).to.have.been.calledOnce;
        expect(component.isTransitioning).to.be.false;
    });

    it('should search for users', () => {
        const userServiceStub = sinon.stub(userService, 'search').returns(of(new HttpResponse({ body: [user2] })));
        fixture.detectChanges();

        const search = component.searchAllUsers(of({ text: user2.login!, entities: [user2] }));
        fixture.detectChanges();

        // Check if the observable output matches our expectancies
        search.subscribe((a) => {
            expect(a).to.deep.equal([{ id: user2.id, login: user2.login }]);
            expect(component.searchNoResults).to.be.false;
            expect(component.searchFailed).to.be.false;
        });

        expect(userServiceStub).to.have.been.calledOnce;
    });

    it('should reload with only registered users', () => {
        // Same id is intentional: Simulate one user got removed
        const examWithOneUser = { course, id: 2, registeredUsers: [user2] };
        const examServiceStub = sinon.stub(examManagementService, 'find').returns(of(new HttpResponse({ body: examWithOneUser })));
        fixture.detectChanges();

        component.reloadExamWithRegisteredUsers();
        fixture.detectChanges();

        expect(examServiceStub).to.have.been.calledOnceWith(course.id, examWithCourse.id, true);
        expect(component.exam).to.deep.equal(examWithOneUser);
        expect(component.allRegisteredUsers).to.deep.equal([user2]);
    });

    it('should remove users from the exam', () => {
        const examServiceStub = sinon.stub(examManagementService, 'removeStudentFromExam').returns(of(new HttpResponse()));
        fixture.detectChanges();
        component.allRegisteredUsers = [user1, user2];

        component.removeFromExam(user2, { deleteParticipationsAndSubmission: false });
        fixture.detectChanges();

        expect(examServiceStub).to.have.been.calledOnceWith(course.id, examWithCourse.id, user2.login, false);
        expect(component.allRegisteredUsers).to.deep.equal([user1]);
    });

    it('should register all enrolled students of the course to the exam', () => {
        const examServiceStubAddAll = sinon.stub(examManagementService, 'addAllStudentsOfCourseToExam').returns(of(new HttpResponse<void>()));
        const examWithOneUser = { course, id: 2, registeredUsers: [user2] };
        const examServiceStub = sinon.stub(examManagementService, 'find').returns(of(new HttpResponse({ body: examWithOneUser })));
        fixture.detectChanges();

        component.exam = examWithCourse;
        component.registerAllStudentsFromCourse();

        expect(examServiceStub).to.have.been.calledOnceWith(course.id, examWithCourse.id, true);
        expect(examServiceStubAddAll).to.have.been.calledOnceWith(course.id, examWithCourse.id);
        expect(component.allRegisteredUsers).to.deep.equal([user2]);
    });

    it('should remove all users from the exam', () => {
        const examServiceStub = sinon.stub(examManagementService, 'removeAllStudentsFromExam').returns(of(new HttpResponse()));
        fixture.detectChanges();
        component.allRegisteredUsers = [user1, user2];

        component.removeAllStudents({ deleteParticipationsAndSubmission: false });
        fixture.detectChanges();

        expect(examServiceStub).to.have.been.calledOnceWith(course.id, examWithCourse.id, false);
        expect(component.allRegisteredUsers).to.deep.equal([]);
    });

    it('should remove all users from the exam with participaations', () => {
        const examServiceStub = sinon.stub(examManagementService, 'removeAllStudentsFromExam').returns(of(new HttpResponse()));
        fixture.detectChanges();
        component.allRegisteredUsers = [user1, user2];

        component.removeAllStudents({ deleteParticipationsAndSubmission: true });
        fixture.detectChanges();

        expect(examServiceStub).to.have.been.calledOnceWith(course.id, examWithCourse.id, true);
        expect(component.allRegisteredUsers).to.deep.equal([]);
    });

    it('should format search result', () => {
        const resultString = component.searchResultFormatter(user1);
        expect(resultString).to.equal('name (login)');
    });

    it('should format search text from user', () => {
        const resultString = component.searchTextFromUser(user1);
        expect(resultString).to.equal('login');
    });

    it('should test on error', () => {
        component.onError('ErrorString');
        expect(component.isTransitioning).to.equal(false);
    });
});
