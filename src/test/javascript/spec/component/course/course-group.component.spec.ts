import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { CourseGroupComponent } from 'app/course/manage/course-group.component';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import * as moment from 'moment';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import Sinon, * as sinon from 'sinon';
import { JhiAlertService, JhiTranslateDirective } from 'ng-jhipster';
import { MockRouterLinkDirective } from '../lecture-unit/lecture-unit-management.component.spec';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { JhiEventManager } from 'ng-jhipster';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { User } from 'app/core/user/user.model';
import { Course, CourseGroup, courseGroups } from 'app/entities/course.model';
import { UserService } from 'app/core/user/user.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('Course Management Detail Component', () => {
    let comp: CourseGroupComponent;
    let fixture: ComponentFixture<CourseGroupComponent>;
    let courseService: CourseManagementService;
    let userService: UserService;
    let eventManager: JhiEventManager;
    let alertService: JhiAlertService;
    const courseGroup = CourseGroup.STUDENTS;
    const course = { id: 123, title: 'Course Title', isAtLeastInstructor: true, endDate: moment().subtract(5, 'minutes'), courseArchivePath: 'some-path' };
    const parentRoute = ({
        data: of({ course }),
    } as any) as ActivatedRoute;
    const route = ({ parent: parentRoute, params: of({ courseGroup }) } as any) as ActivatedRoute;
    const courseGroupUser = new User(1, 'user');
    const courseGroupUser2 = new User(2, 'user2');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule],
            declarations: [
                CourseGroupComponent,
                MockComponent(DataTableComponent),
                MockRouterLinkDirective,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(DeleteButtonDirective),
                MockComponent(AlertErrorComponent),
                MockDirective(AlertComponent),
                MockPipe(ArtemisDatePipe),
                MockDirective(JhiTranslateDirective),
                MockComponent(CourseExamArchiveButtonComponent),
                MockDirective(HasAnyAuthorityDirective),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(JhiAlertService),
                MockProvider(NgbModal),
                MockProvider(CourseManagementService),
                MockProvider(UserService),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(CourseGroupComponent);
        comp = fixture.componentInstance;
        courseService = fixture.debugElement.injector.get(CourseManagementService);
        alertService = TestBed.inject(JhiAlertService);
        eventManager = fixture.debugElement.injector.get(JhiEventManager);
        userService = fixture.debugElement.injector.get(UserService);
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CourseGroupComponent).to.be.ok;
    });

    describe('OnInit', () => {
        it('should load all course group users', () => {
            const getUsersStub = sinon.stub(courseService, 'getAllUsersInCourseGroup');
            getUsersStub.returns(of(new HttpResponse({ body: [courseGroupUser] })));
            fixture.detectChanges();
            comp.ngOnInit();
            expect(comp.course).to.deep.equal(course);
            expect(comp.courseGroup).to.deep.equal(courseGroup);
            expect(getUsersStub).to.have.been.called;
        });
    });

    describe('searchAllUsers', () => {
        let loginOrName: string;
        let loginStream: Observable<{ text: string; entities: User[] }>;
        let searchStub: Sinon.SinonStub;

        beforeEach(() => {
            loginOrName = 'testLoginOrName';
            loginStream = of({ text: loginOrName, entities: [] });
            searchStub = sinon.stub(userService, 'search');
        });

        it('should search users for given login or name', () => {
            searchStub.returns(of(new HttpResponse({ body: [courseGroupUser] })));
            comp.searchAllUsers(loginStream).subscribe((users: any) => {
                expect(users).to.deep.equal([courseGroupUser]);
            });
            expect(searchStub).to.have.been.calledWith(loginOrName);
        });

        it('should set search no results if search returns no result', () => {
            searchStub.returns(of(new HttpResponse({ body: [] })));
            comp.searchAllUsers(loginStream).subscribe((users: any) => {
                expect(users).to.deep.equal([]);
            });
            expect(comp.searchNoResults).to.equal(true);
            expect(searchStub).to.have.been.calledWith(loginOrName);
        });

        it('should return empty if search text is shorter than three characters', () => {
            loginStream = of({ text: 'ab', entities: [] });
            searchStub.returns(of(new HttpResponse({ body: [courseGroupUser] })));
            comp.searchAllUsers(loginStream).subscribe((users: any) => {
                expect(users).to.deep.equal([]);
            });
            expect(searchStub).not.to.have.been.called;
        });

        it('should return empty if search fails', () => {
            searchStub.returns(throwError(new Error('')));
            comp.searchAllUsers(loginStream).subscribe((users: any) => {
                expect(users).to.deep.equal([]);
            });
            expect(comp.searchFailed).to.be;
            expect(searchStub).to.have.been.calledWith(loginOrName);
        });
    });

    describe('onAutocompleteSelect', () => {
        let addUserStub: sinon.SinonStub;
        let fake: sinon.SinonSpy;
        let user: User;

        beforeEach(() => {
            addUserStub = sinon.stub(courseService, 'addUserToCourseGroup');
            addUserStub.returns(of(new HttpResponse()));
            fake = sinon.fake();
            user = courseGroupUser;
            comp.allCourseGroupUsers = [];
            comp.course = course;
            comp.courseGroup = courseGroup;
        });
        it('should add the selected user to course group', () => {
            comp.onAutocompleteSelect(user, fake);
            expect(addUserStub).to.have.been.calledWith(course.id, courseGroup, user.login);
            expect(comp.allCourseGroupUsers).to.deep.equal([courseGroupUser]);
            expect(fake).to.have.been.calledWithExactly(user);
        });
        it('should call callback if user is already in the group', () => {
            comp.allCourseGroupUsers = [user];
            comp.onAutocompleteSelect(user, fake);
            expect(addUserStub).not.to.have.been.called;
            expect(comp.allCourseGroupUsers).to.deep.equal([courseGroupUser]);
            expect(fake).to.have.been.calledWithExactly(user);
        });
    });

    describe('removeFromGroup', () => {
        let removeUserStub: sinon.SinonStub;

        beforeEach(() => {
            removeUserStub = sinon.stub(courseService, 'removeUserFromCourseGroup');
            removeUserStub.returns(of(new HttpResponse()));
            comp.allCourseGroupUsers = [courseGroupUser, courseGroupUser2];
            comp.course = course;
            comp.courseGroup = courseGroup;
        });
        it('should given user from group', () => {
            comp.removeFromGroup(courseGroupUser);
            expect(removeUserStub).to.have.been.calledWith(course.id, courseGroup, courseGroupUser.login);
            expect(comp.allCourseGroupUsers).to.deep.equal([courseGroupUser2]);
        });
        it('should not do anything if users has no login', () => {
            const user = { ...courseGroupUser };
            delete user.login;
            comp.removeFromGroup(user);
            expect(removeUserStub).not.to.have.been.called;
        });
    });

    describe('courseGroupName', () => {
        it('should return courses studentGroupName if group is students', () => {
            comp.courseGroup = CourseGroup.STUDENTS;
            comp.course = { ...course };
            comp.course.studentGroupName = 'testStudentGroupName';
            expect(comp.courseGroupName).to.equal(comp.course.studentGroupName);
        });
        it('should return courses teachingAssistantGroupName if group is tutors', () => {
            comp.courseGroup = CourseGroup.TUTORS;
            comp.course = { ...course };
            comp.course.teachingAssistantGroupName = 'testTeachingAssistantGroupName';
            expect(comp.courseGroupName).to.equal(comp.course.teachingAssistantGroupName);
        });
        it('should return courses instructorGroupName if group is instructors', () => {
            comp.courseGroup = CourseGroup.INSTRUCTORS;
            comp.course = { ...course };
            comp.course.instructorGroupName = 'testInstructorGroupName';
            expect(comp.courseGroupName).to.equal(comp.course.instructorGroupName);
        });
    });

    describe('handleUsersSizeChange', () => {
        it('should change user size to given number', () => {
            const size = 5;
            comp.handleUsersSizeChange(size);
            expect(comp.filteredUsersSize).to.equal(size);
        });
    });

    describe('searchResultFormatter', () => {
        it('should format user info into appropriate format', () => {
            const name = 'testName';
            const user = { ...courseGroupUser, name };
            expect(comp.searchResultFormatter(user)).to.equal(`${name} (${user.login})`);
        });
    });

    describe('searchTextFromUser', () => {
        it('converts a user to a string that can be searched for', () => {
            const user = courseGroupUser;
            expect(comp.searchTextFromUser(user)).to.equal(user.login);
        });
        it('should return empty string if user does not have login', () => {
            const user = { ...courseGroupUser };
            delete user.login;
            expect(comp.searchTextFromUser(user)).to.equal('');
        });
    });
});
