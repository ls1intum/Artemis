import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { User } from 'app/core/user/user.model';
import { Course, CourseGroup } from 'app/entities/course.model';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Observable, of } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { CourseGroupMembershipComponent } from 'app/course/manage/course-group-membership/course-group-membership.component';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { RegisteredStudentsComponent } from 'app/course/tutorial-groups/tutorial-groups-management/registered-students/registered-students.component';
import { TutorialGroupRegistration, TutorialGroupRegistrationType } from 'app/entities/tutorial-group/tutorial-group-registration.model';
import { ArtemisTestModule } from '../../../../test.module';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LoadingIndicatorContainerStubComponent } from '../../../../helpers/stubs/loading-indicator-container-stub.component';

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

describe('Registered Students Component', () => {
    let comp: RegisteredStudentsComponent;
    let fixture: ComponentFixture<RegisteredStudentsComponent>;
    let tutorialGroup: TutorialGroup;
    let tutorialGroupService: TutorialGroupsService;
    let getTutorialGroupSpy: jest.SpyInstance;
    const course = { id: 123, title: 'Example', isAtLeastInstructor: true };
    const tutorialGroupUserOne = new User(1, 'user1');
    const tutorialGroupUserTwo = new User(2, 'user2');

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                RegisteredStudentsComponent,
                LoadingIndicatorContainerStubComponent,
                CourseGroupStubComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [MockProvider(TutorialGroupsService), MockProvider(CourseManagementService), MockProvider(NgbActiveModal)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(RegisteredStudentsComponent);
                comp = fixture.componentInstance;
                tutorialGroupService = TestBed.inject(TutorialGroupsService);
                tutorialGroup = new TutorialGroup();
                tutorialGroup.title = 'Group';
                tutorialGroup.id = 123;
                tutorialGroup.course = course;

                const registrationOne = new TutorialGroupRegistration();
                registrationOne.student = tutorialGroupUserOne;
                registrationOne.tutorialGroup = tutorialGroup;
                registrationOne.type = TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION;

                const registrationTwo = new TutorialGroupRegistration();
                registrationTwo.student = tutorialGroupUserTwo;
                registrationTwo.tutorialGroup = tutorialGroup;
                registrationTwo.type = TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION;

                tutorialGroup.registrations = [registrationOne, registrationTwo];

                comp.course = course;
                comp.tutorialGroupId = tutorialGroup.id!;

                getTutorialGroupSpy = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: tutorialGroup })));

                comp.initialize();

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(CourseGroupMembershipComponent).not.toBeNull();
    });

    describe('OnInit', () => {
        it('should load tutorial group', () => {
            expect(comp.course).toEqual(course);
            expect(comp.tutorialGroup).toEqual(tutorialGroup);
            expect(comp.courseGroup).toEqual(CourseGroup.STUDENTS);
            expect(getTutorialGroupSpy).toHaveBeenCalledOnce();
            expect(getTutorialGroupSpy).toHaveBeenCalledWith(course.id, tutorialGroup.id);
            expect(comp.registeredStudents).toEqual(tutorialGroup.registrations?.map((registration) => registration.student));
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
        it('should return course title concatenated with tutorial group title', () => {
            expect(comp.exportFilename).toBe('Example Group');
        });
    });
});
