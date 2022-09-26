import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course, CourseGroup } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';
import { MockDirective, MockProvider } from 'ng-mocks';
import { Observable, of } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { CourseGroupMembershipComponent } from 'app/course/manage/course-group-membership/course-group-membership.component';
import { ArtemisTestModule } from '../../test.module';

@Component({ selector: 'jhi-course-group', template: '' })
class CourseGroupStubComponent {
    @Input()
    allGroupUsers: User[] = [];
    @Input()
    isLoadingAllGroupUsers = false;
    @Input()
    isAdmin = false;
    @Input()
    course: Course;
    @Input()
    tutorialGroup: TutorialGroup | undefined = undefined;
    @Input()
    courseGroup: CourseGroup;
    @Input()
    exportFileName: string;
    @Input()
    userSearch: (loginOrName: string) => Observable<HttpResponse<User[]>>;
    @Input()
    addUserToGroup: (login: string) => Observable<any> = () => of({});
    @Input()
    removeUserFromGroup: (login: string) => Observable<any> = () => of({});
    @Input()
    handleUsersSizeChange: (filteredUsersSize: number) => void = () => {};

    @Output()
    importFinish: EventEmitter<void> = new EventEmitter();
}

describe('Course Group Membership Component', () => {
    let comp: CourseGroupMembershipComponent;
    let fixture: ComponentFixture<CourseGroupMembershipComponent>;
    let courseService: CourseManagementService;
    const courseGroup = CourseGroup.STUDENTS;
    const course = { id: 123, title: 'Course Title', isAtLeastInstructor: true, endDate: dayjs().subtract(5, 'minutes'), courseArchivePath: 'some-path' };
    const parentRoute = {
        data: of({ course }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute, params: of({ courseGroup }) } as any as ActivatedRoute;
    const courseGroupUser = new User(1, 'user');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseGroupMembershipComponent, CourseGroupStubComponent, MockDirective(TranslateDirective)],
            providers: [{ provide: ActivatedRoute, useValue: route }, MockProvider(CourseManagementService), MockProvider(UserService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseGroupMembershipComponent);
                comp = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(CourseGroupMembershipComponent).not.toBeNull();
    });

    describe('OnInit', () => {
        it('should load all course group users', () => {
            const getUsersStub = jest.spyOn(courseService, 'getAllUsersInCourseGroup').mockReturnValue(of(new HttpResponse({ body: [courseGroupUser] })));
            fixture.detectChanges();
            expect(comp.course).toEqual(course);
            expect(comp.courseGroup).toEqual(courseGroup);
            expect(getUsersStub).toHaveBeenCalledOnce();
        });
    });

    describe('courseGroupName', () => {
        it('should return courses studentGroupName if group is students', () => {
            comp.courseGroup = CourseGroup.STUDENTS;
            comp.course = { ...course };
            comp.course.studentGroupName = 'testStudentGroupName';
            expect(comp.courseGroupName).toBe(comp.course.studentGroupName);
        });

        it('should return courses teachingAssistantGroupName if group is tutors', () => {
            comp.courseGroup = CourseGroup.TUTORS;
            comp.course = { ...course };
            comp.course.teachingAssistantGroupName = 'testTeachingAssistantGroupName';
            expect(comp.courseGroupName).toBe(comp.course.teachingAssistantGroupName);
        });

        it('should return courses instructorGroupName if group is instructors', () => {
            comp.courseGroup = CourseGroup.INSTRUCTORS;
            comp.course = { ...course };
            comp.course.instructorGroupName = 'testInstructorGroupName';
            expect(comp.courseGroupName).toBe(comp.course.instructorGroupName);
        });
    });

    describe('handleUsersSizeChange', () => {
        it('should change user size to given number', () => {
            const size = 5;
            comp.handleUsersSizeChange(size);
            expect(comp.filteredUsersSize).toBe(size);
        });
    });

    describe('exportFileName', () => {
        it('should return export file name', () => {
            comp.courseGroup = CourseGroup.STUDENTS;
            const user1 = new User(1, 'user1');
            comp.allCourseGroupUsers = [user1];
            comp.course = { title: 'Example' };

            expect(comp.exportFilename).toBe('Student Example');
            const user2 = new User(2, 'user2');
            comp.allCourseGroupUsers.push(user2);
            expect(comp.exportFilename).toBe('Students Example');
        });
    });
});
