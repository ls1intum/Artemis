import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { signal } from '@angular/core';
import { ActivatedRoute, ParamMap, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockComponent } from 'ng-mocks';
import { AccountService } from 'app/core/auth/account.service';
import { Course, CourseInformationSharingConfiguration } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialGroupSessionApiService } from 'app/openapi/api/tutorialGroupSessionApi.service';
import { CreateOrUpdateTutorialGroupSessionRequest } from 'app/openapi/model/createOrUpdateTutorialGroupSessionRequest';
import { TutorialGroupSession as RawTutorialGroupSession } from 'app/openapi/model/tutorialGroupSession';
import { AlertService } from 'app/shared/service/alert.service';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import {
    CreateTutorialGroupSessionEvent,
    DeleteTutorialGroupEvent,
    ModifyTutorialGroupSessionEvent,
    TutorialGroupDetailAccessLevel,
    TutorialGroupDetailComponent,
    UpdateTutorialGroupSessionEvent,
} from 'app/tutorialgroup/shared/tutorial-group-detail/tutorial-group-detail.component';
import { TutorialGroupDetailData } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupCourseAndGroupService } from 'app/tutorialgroup/shared/service/tutorial-group-course-and-group.service';
import { ManagementTutorialGroupDetailContainerComponent } from './management-tutorial-group-detail-container.component';
import { LoadingIndicatorOverlayStubComponent } from 'test/helpers/stubs/tutorialgroup/loading-indicator-overlay-stub.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

interface TestRouteSnapshot {
    paramMap: ParamMap;
    data: Record<string, unknown>;
    pathFromRoot: TestRouteSnapshot[];
}

interface TestRoute {
    snapshot: TestRouteSnapshot;
    paramMap: BehaviorSubject<ParamMap>;
    parent: TestRoute | null;
}

describe('ManagementTutorialGroupDetailContainerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ManagementTutorialGroupDetailContainerComponent>;
    let component: ManagementTutorialGroupDetailContainerComponent;

    let tutorialGroupCourseAndGroupService: {
        tutorialGroup: ReturnType<typeof signal<TutorialGroupDetailData | undefined>>;
        course: ReturnType<typeof signal<Course | undefined>>;
        isTutorialGroupLoading: ReturnType<typeof signal<boolean>>;
        fetchTutorialGroup: ReturnType<typeof vi.fn>;
        toggleCancellationStatusOfSession: ReturnType<typeof vi.fn>;
        insertSession: ReturnType<typeof vi.fn>;
    };
    let tutorialGroupSessionApiService: {
        deleteSession: ReturnType<typeof vi.fn>;
        cancelSession: ReturnType<typeof vi.fn>;
        activateSession: ReturnType<typeof vi.fn>;
        updateSession: ReturnType<typeof vi.fn>;
        createSession: ReturnType<typeof vi.fn>;
    };
    let tutorialGroupApiService: {
        deleteTutorialGroup: ReturnType<typeof vi.fn>;
    };
    let alertService: MockAlertService;
    let accountService: MockAccountService;
    let route: TestRoute;

    beforeEach(async () => {
        tutorialGroupCourseAndGroupService = {
            tutorialGroup: signal<TutorialGroupDetailData | undefined>(undefined),
            course: signal<Course | undefined>(undefined),
            isTutorialGroupLoading: signal(false),
            fetchTutorialGroup: vi.fn(),
            toggleCancellationStatusOfSession: vi.fn(),
            insertSession: vi.fn(),
        };
        tutorialGroupSessionApiService = {
            deleteSession: vi.fn(),
            cancelSession: vi.fn(),
            activateSession: vi.fn(),
            updateSession: vi.fn(),
            createSession: vi.fn(),
        };
        tutorialGroupApiService = {
            deleteTutorialGroup: vi.fn(),
        };
        alertService = new MockAlertService();
        vi.spyOn(alertService, 'addErrorAlert');
        accountService = new MockAccountService();

        const courseFromRoute = createCourse(false, true, CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        const rootSnapshot: TestRouteSnapshot = {
            paramMap: convertToParamMap({}),
            data: {},
            pathFromRoot: [],
        };
        const parentSnapshot: TestRouteSnapshot = {
            paramMap: convertToParamMap({ courseId: '2' }),
            data: { course: courseFromRoute },
            pathFromRoot: [],
        };
        const routeSnapshot: TestRouteSnapshot = {
            paramMap: convertToParamMap({ tutorialGroupId: '17' }),
            data: {},
            pathFromRoot: [],
        };
        rootSnapshot.pathFromRoot = [rootSnapshot];
        parentSnapshot.pathFromRoot = [rootSnapshot, parentSnapshot];
        routeSnapshot.pathFromRoot = [rootSnapshot, parentSnapshot, routeSnapshot];
        route = {
            snapshot: routeSnapshot,
            paramMap: new BehaviorSubject(convertToParamMap({ tutorialGroupId: '17' })),
            parent: {
                snapshot: parentSnapshot,
                paramMap: new BehaviorSubject(convertToParamMap({ courseId: '2' })),
                parent: null,
            },
        };

        await TestBed.configureTestingModule({
            imports: [ManagementTutorialGroupDetailContainerComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: TutorialGroupCourseAndGroupService, useValue: tutorialGroupCourseAndGroupService },
                { provide: TutorialGroupSessionApiService, useValue: tutorialGroupSessionApiService },
                { provide: TutorialGroupApiService, useValue: tutorialGroupApiService },
                { provide: AlertService, useValue: alertService },
                { provide: AccountService, useValue: accountService },
            ],
        })
            .overrideComponent(ManagementTutorialGroupDetailContainerComponent, {
                remove: { imports: [TutorialGroupDetailComponent, LoadingIndicatorOverlayComponent] },
                add: { imports: [MockComponent(TutorialGroupDetailComponent), LoadingIndicatorOverlayStubComponent] },
            })
            .compileComponents();
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    function createCourse(editor = false, instructor = false, sharing = CourseInformationSharingConfiguration.COMMUNICATION_ONLY): Course {
        const course = new Course() as Course & { isAtLeastEditor?: boolean; isAtLeastInstructor?: boolean };
        course.id = 2;
        course.isAtLeastEditor = editor;
        course.isAtLeastInstructor = instructor;
        course.courseInformationSharingConfiguration = sharing;
        return course;
    }

    function createRawSession(id: number, isCancelled = false): RawTutorialGroupSession {
        return {
            id,
            start: '2026-04-20T10:15:00.000Z',
            end: '2026-04-20T11:45:00.000Z',
            location: 'Room 101',
            isCancelled,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 8,
        };
    }

    function createTutorialGroup(): TutorialGroupDetailData {
        return new TutorialGroupDetailData({
            id: 17,
            title: 'TG 1',
            language: 'English',
            isOnline: false,
            sessions: [createRawSession(1), createRawSession(2, true)],
            tutorName: 'Grace Hopper',
            tutorLogin: 'grace',
            tutorId: 12,
            tutorImageUrl: undefined,
            capacity: 15,
            campus: 'Garching',
            additionalInformation: 'Bring laptop',
            groupChannelId: 22,
            tutorChatId: 33,
        });
    }

    function createModifyEvent(sessionId = 1): ModifyTutorialGroupSessionEvent {
        return { courseId: 2, tutorialGroupId: 17, tutorialGroupSessionId: sessionId };
    }

    function createCreateOrUpdateTutorialGroupSessionRequest(): CreateOrUpdateTutorialGroupSessionRequest {
        return {
            date: '2026-04-27',
            startTime: '10:15',
            endTime: '11:45',
            location: 'Room 102',
            attendance: 9,
        };
    }

    function createUpdateEvent(sessionId = 1): UpdateTutorialGroupSessionEvent {
        return {
            courseId: 2,
            tutorialGroupId: 17,
            tutorialGroupSessionId: sessionId,
            updateTutorialGroupSessionRequest: createCreateOrUpdateTutorialGroupSessionRequest(),
        };
    }

    function createCreateEvent(): CreateTutorialGroupSessionEvent {
        return {
            courseId: 2,
            tutorialGroupId: 17,
            createTutorialGroupSessionRequest: createCreateOrUpdateTutorialGroupSessionRequest(),
        };
    }

    function createDeleteGroupEvent(): DeleteTutorialGroupEvent {
        return { courseId: 2, tutorialGroupId: 17 };
    }

    function setRouteCourse(course: Course) {
        if (!route.parent) return;
        route.parent.snapshot = {
            ...route.parent.snapshot,
            data: { course },
        };
        route.snapshot = {
            ...route.snapshot,
            pathFromRoot: [route.snapshot.pathFromRoot[0], route.parent.snapshot, route.snapshot],
        };
    }

    function createComponent() {
        fixture = TestBed.createComponent(ManagementTutorialGroupDetailContainerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    }

    it('should fetch the tutorial group on init and set the course from route data', () => {
        const tutorialGroup = createTutorialGroup();
        tutorialGroupCourseAndGroupService.fetchTutorialGroup.mockImplementation(() => {
            tutorialGroupCourseAndGroupService.tutorialGroup.set(tutorialGroup);
        });
        accountService.userIdentity.set({ login: 'grace' } as User);

        createComponent();

        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).toHaveBeenCalledOnce();
        expect(tutorialGroupCourseAndGroupService.fetchTutorialGroup).toHaveBeenCalledWith(2, 17);
        expect(component.isMessagingEnabled()).toBe(true);
        expect(component.loggedInUserTutorialGroupDetailAccessLevel()).toBe(TutorialGroupDetailAccessLevel.INSTRUCTOR_OF_GROUP_OR_ADMIN);
    });

    it('should expose editor access level when the logged in user is editor but not instructor', () => {
        setRouteCourse(createCourse(true, false));
        tutorialGroupCourseAndGroupService.tutorialGroup.set(createTutorialGroup());
        accountService.userIdentity.set({ login: 'grace' } as User);

        createComponent();

        expect(component.loggedInUserTutorialGroupDetailAccessLevel()).toBe(TutorialGroupDetailAccessLevel.EDITOR_OF_GROUP);
    });

    it('should expose tutor access level when the logged in user is the tutor of the group without editor rights', () => {
        setRouteCourse(createCourse(false, false));
        tutorialGroupCourseAndGroupService.tutorialGroup.set(createTutorialGroup());
        accountService.userIdentity.set({ login: 'grace' } as User);

        createComponent();

        expect(component.loggedInUserTutorialGroupDetailAccessLevel()).toBe(TutorialGroupDetailAccessLevel.TUTOR_OF_GROUP);
    });

    it('should expose the fallback access level when the logged in user is neither instructor nor editor nor tutor of the group', () => {
        setRouteCourse(createCourse(false, false));
        tutorialGroupCourseAndGroupService.tutorialGroup.set(createTutorialGroup());
        accountService.userIdentity.set({ login: 'other-user' } as User);

        createComponent();

        expect(component.loggedInUserTutorialGroupDetailAccessLevel()).toBe(TutorialGroupDetailAccessLevel.TUTOR_OF_OTHER_GROUP_OR_EDITOR_OR_INSTRUCTOR_OF_OTHER_COURSE);
    });

    it('should remove the deleted session on successful deleteSession', () => {
        tutorialGroupCourseAndGroupService.tutorialGroup.set(createTutorialGroup());
        tutorialGroupSessionApiService.deleteSession.mockReturnValue(of({ status: 200 }));

        createComponent();

        component.deleteSession(createModifyEvent(1));

        expect(tutorialGroupSessionApiService.deleteSession).toHaveBeenCalledWith(2, 17, 1, 'response');
        expect(component.isLoading()).toBe(false);
        expect(component.tutorialGroup()?.sessions.map((session) => session.id)).toEqual([2]);
    });

    it('should show an error alert on failed deleteSession', () => {
        tutorialGroupCourseAndGroupService.tutorialGroup.set(createTutorialGroup());
        tutorialGroupSessionApiService.deleteSession.mockReturnValue(throwError(() => new Error('network error')));

        createComponent();

        component.deleteSession(createModifyEvent(1));

        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupDetail.networkError.deleteSession');
        expect(component.tutorialGroup()?.sessions.map((session) => session.id)).toEqual([1, 2]);
        expect(component.isLoading()).toBe(false);
    });

    it('should toggle the cancellation status on successful cancelSession', () => {
        const cancel$ = new Subject<void>();
        tutorialGroupSessionApiService.cancelSession.mockReturnValue(cancel$.asObservable());

        createComponent();

        component.cancelSession(createModifyEvent(2));

        expect(component.isLoading()).toBe(true);
        cancel$.next();
        cancel$.complete();

        expect(tutorialGroupCourseAndGroupService.toggleCancellationStatusOfSession).toHaveBeenCalledWith(2);
        expect(component.isLoading()).toBe(false);
    });

    it('should show an error alert on failed cancelSession', () => {
        const cancel$ = new Subject<void>();
        tutorialGroupSessionApiService.cancelSession.mockReturnValue(cancel$.asObservable());

        createComponent();

        component.cancelSession(createModifyEvent(2));
        cancel$.error(new Error('network error'));

        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupDetail.networkError.cancelSession');
        expect(tutorialGroupCourseAndGroupService.toggleCancellationStatusOfSession).not.toHaveBeenCalled();
        expect(component.isLoading()).toBe(false);
    });

    it('should toggle the cancellation status on successful activateSession', () => {
        const activate$ = new Subject<void>();
        tutorialGroupSessionApiService.activateSession.mockReturnValue(activate$.asObservable());

        createComponent();

        component.activateSession(createModifyEvent(2));

        expect(component.isLoading()).toBe(true);
        activate$.next();
        activate$.complete();

        expect(tutorialGroupCourseAndGroupService.toggleCancellationStatusOfSession).toHaveBeenCalledWith(2);
        expect(component.isLoading()).toBe(false);
    });

    it('should show an error alert on failed activateSession', () => {
        const activate$ = new Subject<void>();
        tutorialGroupSessionApiService.activateSession.mockReturnValue(activate$.asObservable());

        createComponent();

        component.activateSession(createModifyEvent(2));
        activate$.error(new Error('network error'));

        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupDetail.networkError.activateSession');
        expect(tutorialGroupCourseAndGroupService.toggleCancellationStatusOfSession).not.toHaveBeenCalled();
        expect(component.isLoading()).toBe(false);
    });

    it('should update the session on successful updateSession', () => {
        const rawSession = createRawSession(3);
        const update$ = new Subject<RawTutorialGroupSession>();
        tutorialGroupSessionApiService.updateSession.mockReturnValue(update$.asObservable());

        createComponent();

        component.updateSession(createUpdateEvent(1));

        expect(component.isLoading()).toBe(true);
        update$.next(rawSession);
        update$.complete();

        expect(tutorialGroupCourseAndGroupService.insertSession).toHaveBeenCalledOnce();
        const updatedSession = tutorialGroupCourseAndGroupService.insertSession.mock.calls[0][0] as TutorialGroupSession;
        expect(updatedSession).toBeInstanceOf(TutorialGroupSession);
        expect(updatedSession.id).toBe(3);
        expect(component.isLoading()).toBe(false);
    });

    it('should show an error alert on failed updateSession', () => {
        const update$ = new Subject<RawTutorialGroupSession>();
        tutorialGroupSessionApiService.updateSession.mockReturnValue(update$.asObservable());

        createComponent();

        component.updateSession(createUpdateEvent(1));
        update$.error(new Error('network error'));

        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupDetail.networkError.updateSession');
        expect(tutorialGroupCourseAndGroupService.insertSession).not.toHaveBeenCalled();
        expect(component.isLoading()).toBe(false);
    });

    it('should insert the created session on successful createSession', () => {
        const rawSession = createRawSession(4);
        const create$ = new Subject<RawTutorialGroupSession>();
        tutorialGroupSessionApiService.createSession.mockReturnValue(create$.asObservable());

        createComponent();

        component.createSession(createCreateEvent());

        expect(component.isLoading()).toBe(true);
        create$.next(rawSession);
        create$.complete();

        expect(tutorialGroupCourseAndGroupService.insertSession).toHaveBeenCalledOnce();
        const insertedSession = tutorialGroupCourseAndGroupService.insertSession.mock.calls[0][0] as TutorialGroupSession;
        expect(insertedSession.id).toBe(4);
        expect(component.isLoading()).toBe(false);
    });

    it('should show an error alert on failed createSession', () => {
        const create$ = new Subject<RawTutorialGroupSession>();
        tutorialGroupSessionApiService.createSession.mockReturnValue(create$.asObservable());

        createComponent();

        component.createSession(createCreateEvent());
        create$.error(new Error('network error'));

        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupDetail.networkError.createSession');
        expect(tutorialGroupCourseAndGroupService.insertSession).not.toHaveBeenCalled();
        expect(component.isLoading()).toBe(false);
    });

    it('should navigate back on successful deleteGroup', async () => {
        const router = TestBed.inject(Router);
        tutorialGroupApiService.deleteTutorialGroup.mockReturnValue(of({ status: 200 }));

        createComponent();

        component.deleteGroup(createDeleteGroupEvent());
        await fixture.whenStable();

        expect(tutorialGroupApiService.deleteTutorialGroup).toHaveBeenCalledWith(2, 17, 'response');
        expect(router.navigate).toHaveBeenCalledWith(['../'], { relativeTo: route });
        expect(component.isLoading()).toBe(false);
    });

    it('should show an error alert and not navigate on failed deleteGroup', async () => {
        const router = TestBed.inject(Router);
        tutorialGroupApiService.deleteTutorialGroup.mockReturnValue(throwError(() => new Error('network error')));

        createComponent();

        component.deleteGroup(createDeleteGroupEvent());
        await fixture.whenStable();

        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupDetail.networkError.deleteGroup');
        expect(router.navigate).not.toHaveBeenCalled();
        expect(component.isLoading()).toBe(false);
    });
});
