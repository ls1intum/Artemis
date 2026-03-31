import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TutorialRegistrationsContainerComponent } from 'app/tutorialgroup/manage/tutorial-registrations-container/tutorial-registrations-container.component';
import { TutorialGroupRegisteredStudentsService } from 'app/tutorialgroup/manage/service/tutorial-group-registered-students.service';
import { TutorialRegistrationsComponent } from 'app/tutorialgroup/manage/tutorial-registrations/tutorial-registrations.component';
import { TutorialGroupDetailData } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupCourseAndGroupService } from 'app/tutorialgroup/shared/service/tutorial-group-course-and-group.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { LoadingIndicatorOverlayStubComponent } from 'test/helpers/stubs/tutorialgroup/loading-indicator-overlay-stub.component';
import { mockedActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTutorialGroupCourseAndGroupService } from 'test/helpers/mocks/service/mock-tutorial-group-course-and-group.service';
import { TutorialGroupStudent } from 'app/openapi/model/tutorialGroupStudent';

describe('TutorialRegistrationsContainerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialRegistrationsContainerComponent>;
    let component: TutorialRegistrationsContainerComponent;

    let tutorialGroupCourseAndGroupService: MockTutorialGroupCourseAndGroupService;
    let tutorialGroupRegisteredStudentsService: {
        registeredStudents: ReturnType<typeof signal<TutorialGroupStudent[]>>;
        isLoading: ReturnType<typeof signal<boolean>>;
        fetchRegisteredStudents: ReturnType<typeof vi.fn>;
    };
    let accountService: MockAccountService;
    let activatedRoute: ActivatedRoute;

    beforeEach(async () => {
        tutorialGroupCourseAndGroupService = new MockTutorialGroupCourseAndGroupService();
        tutorialGroupRegisteredStudentsService = {
            registeredStudents: signal<TutorialGroupStudent[]>([]),
            isLoading: signal(false),
            fetchRegisteredStudents: vi.fn(),
        };
        accountService = new MockAccountService();

        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsContainerComponent],
            providers: [
                mockedActivatedRoute({ tutorialGroupId: '17' }, {}, {}, { courseId: '2' }),
                { provide: TutorialGroupCourseAndGroupService, useValue: tutorialGroupCourseAndGroupService },
                { provide: TutorialGroupRegisteredStudentsService, useValue: tutorialGroupRegisteredStudentsService },
                { provide: AccountService, useValue: accountService },
            ],
        })
            .overrideComponent(TutorialRegistrationsContainerComponent, {
                remove: { imports: [TutorialRegistrationsComponent, LoadingIndicatorOverlayComponent] },
                add: { imports: [MockComponent(TutorialRegistrationsComponent), LoadingIndicatorOverlayStubComponent] },
            })
            .compileComponents();

        activatedRoute = TestBed.inject(ActivatedRoute);
        activatedRoute.snapshot = { paramMap: convertToParamMap({ tutorialGroupId: '17' }) } as ActivatedRoute['snapshot'];
        if (activatedRoute.parent) {
            activatedRoute.parent.snapshot = { paramMap: convertToParamMap({ courseId: '2' }) } as ActivatedRoute['snapshot'];
        }
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    function createTutorialGroup(tutorLogin = 'grace'): TutorialGroupDetailData {
        return new TutorialGroupDetailData({
            id: 17,
            title: 'TG 1',
            language: 'English',
            isOnline: false,
            sessions: [],
            tutorName: 'Grace Hopper',
            tutorLogin,
            tutorId: 12,
            tutorImageUrl: undefined,
            capacity: 15,
            campus: 'Garching',
            additionalInformation: 'Bring laptop',
            groupChannelId: undefined,
            tutorChatId: undefined,
        });
    }

    function createCourse(editor = false, instructor = false): Course {
        const course = new Course() as Course & { isAtLeastEditor?: boolean; isAtLeastInstructor?: boolean };
        course.id = 2;
        course.isAtLeastEditor = editor;
        course.isAtLeastInstructor = instructor;
        return course;
    }

    function createRegisteredStudents(): TutorialGroupStudent[] {
        return [
            {
                id: 1,
                name: 'Ada Lovelace',
                login: 'ada',
                email: 'ada@tum.de',
                registrationNumber: 'R001',
                profilePictureUrl: undefined,
            },
        ];
    }

    function createComponent() {
        fixture = TestBed.createComponent(TutorialRegistrationsContainerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    }

    it('should fetch registered students, tutorial group, and course on init when ids are available', () => {
        createComponent();

        expect(tutorialGroupRegisteredStudentsService.fetchRegisteredStudents).toHaveBeenCalledOnce();
        expect(tutorialGroupRegisteredStudentsService.fetchRegisteredStudents).toHaveBeenCalledWith(2, 17);
        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).toHaveBeenCalledOnce();
        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).toHaveBeenCalledWith(2, 17);
        expect(tutorialGroupCourseAndGroupService.fetchCourse).toHaveBeenCalledOnce();
        expect(tutorialGroupCourseAndGroupService.fetchCourse).toHaveBeenCalledWith(2);
    });

    it('should not refetch the tutorial group or course on init when both are already available', () => {
        tutorialGroupCourseAndGroupService.tutorialGroup.set(createTutorialGroup());
        tutorialGroupCourseAndGroupService.course.set(createCourse());

        createComponent();

        expect(tutorialGroupRegisteredStudentsService.fetchRegisteredStudents).toHaveBeenCalledWith(2, 17);
        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).not.toHaveBeenCalled();
        expect(tutorialGroupCourseAndGroupService.fetchCourse).not.toHaveBeenCalled();
    });

    it('should mark the user as at least tutor of the group when they are the tutorial group tutor and pass the flags to the child component', () => {
        const registeredStudents = createRegisteredStudents();
        tutorialGroupCourseAndGroupService.tutorialGroup.set(createTutorialGroup('grace'));
        tutorialGroupCourseAndGroupService.course.set(createCourse(false, false));
        tutorialGroupRegisteredStudentsService.registeredStudents.set(registeredStudents);
        accountService.userIdentity.set({ login: 'grace' } as User);

        createComponent();

        expect(component.loggedInUserIsAtLeastTutorOfGroup()).toBe(true);
        expect(component.loggedInUserIsAtLeastInstructorInCourse()).toBe(false);

        const child = fixture.debugElement.query(By.directive(TutorialRegistrationsComponent)).componentInstance as TutorialRegistrationsComponent & {
            courseId: number;
            tutorialGroupId: number;
            registeredStudents: TutorialGroupStudent[];
            loggedInUserIsAtLeastTutorOfGroup: boolean;
            loggedInUserIsAtLeastInstructorInCourse: boolean;
        };
        expect(child.courseId).toBe(2);
        expect(child.tutorialGroupId).toBe(17);
        expect(child.registeredStudents).toEqual(registeredStudents);
        expect(child.loggedInUserIsAtLeastTutorOfGroup).toBe(true);
        expect(child.loggedInUserIsAtLeastInstructorInCourse).toBe(false);
    });

    it('should mark the user as at least tutor of the group when they have editor rights in the course and as instructor when they have instructor rights', () => {
        tutorialGroupCourseAndGroupService.tutorialGroup.set(createTutorialGroup('another-tutor'));
        tutorialGroupCourseAndGroupService.course.set(createCourse(true, true));
        accountService.userIdentity.set({ login: 'student' } as User);

        createComponent();

        expect(component.loggedInUserIsAtLeastTutorOfGroup()).toBe(true);
        expect(component.loggedInUserIsAtLeastInstructorInCourse()).toBe(true);
    });
});
