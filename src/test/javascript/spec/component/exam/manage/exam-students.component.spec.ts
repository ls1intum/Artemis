import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ArtemisTestModule } from '../../../test.module';
import { ActivatedRoute, convertToParamMap, UrlSegment } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamStudentsComponent } from 'app/exam/manage/students/exam-students.component';
import { StudentsExamImportButtonComponent } from 'app/exam/manage/students/students-exam-import-dialog/students-exam-import-button.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { JhiTranslateDirective } from 'ng-jhipster';
import { RouterTestingModule } from '@angular/router/testing';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { User } from 'app/core/user/user.model';
import { HttpResponse } from '@angular/common/http';
import { UserService } from 'app/core/user/user.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExamStudentsComponent', () => {
    const examCourse = { id: 1 } as Course;
    const user1 = { id: 1 } as User;
    const user2 = { id: 2, login: 'user2' } as User;

    const route = ({
        snapshot: { paramMap: convertToParamMap({ courseId: examCourse.id }) },
        url: new Observable<UrlSegment[]>(),
        data: { subscribe: (fn: (value: any) => void) => fn({ exam }) },
    } as any) as ActivatedRoute;

    let component: ExamStudentsComponent;
    let fixture: ComponentFixture<ExamStudentsComponent>;
    let examManagementService: ExamManagementService;
    let userService: UserService;
    let exam: Exam;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, RouterTestingModule],
            declarations: [
                ExamStudentsComponent,
                MockComponent(StudentsExamImportButtonComponent),
                MockComponent(DataTableComponent),
                MockComponent(AlertComponent),
                MockDirective(JhiTranslateDirective),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [{ provide: ActivatedRoute, useValue: route }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamStudentsComponent);
        component = fixture.componentInstance;
        examManagementService = TestBed.inject(ExamManagementService);
        userService = TestBed.inject(UserService);

        exam = { course: examCourse, id: 2, registeredUsers: [user1, user2] } as Exam;
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
        expect(component.courseId).to.equal(examCourse.id);
        expect(component.exam).to.deep.equal(exam);
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
        const callbackSpy = sinon.spy();
        const flashSpy = sinon.spy(component, 'flashRowClass');
        const examServiceStub = sinon.stub(examManagementService, 'addStudentToExam').returns(of(new HttpResponse()));
        fixture.detectChanges();

        component.onAutocompleteSelect(user3, callbackSpy);
        fixture.detectChanges();

        expect(examServiceStub).to.have.been.calledOnceWith(examCourse.id, exam.id, user3.login);
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
        const examWithOneUser = { course: examCourse, id: 2, registeredUsers: [user2] };
        const examServiceStub = sinon.stub(examManagementService, 'find').returns(of(new HttpResponse({ body: examWithOneUser })));
        fixture.detectChanges();

        component.reloadExamWithRegisteredUsers();
        fixture.detectChanges();

        expect(examServiceStub).to.have.been.calledOnceWith(examCourse.id, exam.id, true);
        expect(component.exam).to.deep.equal(examWithOneUser);
        expect(component.allRegisteredUsers).to.deep.equal([user2]);
    });

    it('should remove users from the exam', () => {
        const examServiceStub = sinon.stub(examManagementService, 'removeStudentFromExam').returns(of(new HttpResponse()));
        fixture.detectChanges();

        component.removeFromExam(user2, { deleteParticipationsAndSubmission: false });
        fixture.detectChanges();

        expect(examServiceStub).to.have.been.calledOnceWith(examCourse.id, exam.id, user2.login, false);
        expect(component.allRegisteredUsers).to.deep.equal([user1]);
    });
});
