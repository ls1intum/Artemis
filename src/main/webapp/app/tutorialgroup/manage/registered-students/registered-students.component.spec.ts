import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { User } from 'app/core/user/user.model';
import { Course, CourseGroup } from 'app/core/course/shared/entities/course.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Observable, of } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Component, input, output } from '@angular/core';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { CourseGroupMembershipComponent } from 'app/core/course/manage/course-group-membership/course-group-membership.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { RegisteredStudentsComponent } from 'app/tutorialgroup/manage/registered-students/registered-students.component';
import { TutorialGroupRegistration, TutorialGroupRegistrationType } from 'app/tutorialgroup/shared/entities/tutorial-group-registration.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LoadingIndicatorContainerStubComponent } from 'test/helpers/stubs/shared/loading-indicator-container-stub.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';

@Component({ selector: 'jhi-course-group', template: '' })
class CourseGroupStubComponent {
    allGroupUsers = input<User[]>([]);
    isLoadingAllGroupUsers = input(false);
    isAdmin = input(false);
    course = input<Course>();
    tutorialGroup = input<TutorialGroup | undefined>(undefined);
    courseGroup = input<CourseGroup>();
    exportFileName = input<string>();
    userSearch = input<() => Observable<HttpResponse<User[]>>>();
    addUserToGroup = input<() => Observable<any>>(() => of({}));
    removeUserFromGroup = input<() => Observable<any>>(() => of({}));
    handleUsersSizeChange = input<() => void>(() => {});

    importFinish = output<void>();
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
            declarations: [
                RegisteredStudentsComponent,
                LoadingIndicatorContainerStubComponent,
                CourseGroupStubComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(CourseManagementService),
                MockProvider(LocalStorageService),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
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

                fixture.componentRef.setInput('course', course);
                fixture.componentRef.setInput('tutorialGroupId', tutorialGroup.id!);

                getTutorialGroupSpy = jest.spyOn(tutorialGroupService, 'getOneOfCourse').mockReturnValue(of(new HttpResponse({ body: tutorialGroup })));

                comp.open();

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
            expect(comp.course()).toEqual(course);
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
